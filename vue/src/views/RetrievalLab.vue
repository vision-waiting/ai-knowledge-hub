<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { listKnowledgeBases } from '@/api/knowledge'
import { listDocuments } from '@/api/documents'
import { debugRetrieval, evaluateRetrieval, listEvaluationRuns, type DebugResult, type EvaluationCase, type EvaluationRun, type RunSummary } from '@/api/evaluation'
import type { KnowledgeBase, DocumentItem } from '@/types'
import { errorMessage } from '@/utils/errors'

const auth = useAuthStore(), route = useRoute()
const bases = ref<KnowledgeBase[]>([]), documents = ref<DocumentItem[]>([]), baseId = ref<number>()
const topK = ref(3), question = ref(''), dataset = ref('[]'), busy = ref(false), loading = ref(true), error = ref('')
const debug = ref<DebugResult>(), runs = ref<(EvaluationRun & { dataset: string; knowledgeBaseId: number })[]>([]), history = ref<RunSummary[]>([])
const latest = computed(() => runs.value[0])
// Stored runs survive reloads; a run only kept in memory (e.g. the history request failed) is still shown first.
const allRuns = computed(() => {
  const stored = new Set(history.value.map(run => run.id))
  const pending = runs.value.filter(run => run.knowledgeBaseId === baseId.value && !(run.id && stored.has(run.id)))
  return [...pending.map(run => ({ ...run, isMemory: true })), ...history.value.map(run => ({ ...run, isMemory: false }))]
})
const validTopK = computed(() => Number.isInteger(topK.value) && topK.value >= 1 && topK.value <= 20)
let documentRequest = 0
watch(baseId, async id => {
  debug.value = undefined; documents.value = []; history.value = []; const current = ++documentRequest
  if (!id) return
  try {
    const [docs, stored] = await Promise.all([listDocuments(id), listEvaluationRuns(id).catch(() => [] as RunSummary[])])
    if (current === documentRequest) { documents.value = docs; history.value = stored }
  }
  catch (cause) { if (current === documentRequest) error.value = errorMessage(cause, '文档加载失败') }
})
async function load() {
  loading.value = true; error.value = ''
  try {
    bases.value = (await listKnowledgeBases()).filter(b => b.canManage ?? (auth.isAdmin || b.ownerId === auth.user?.id))
    const requested = Number(route.params.id || route.query.knowledgeBaseId)
    baseId.value = bases.value.find(b => b.id === requested)?.id ?? bases.value[0]?.id
  } catch (cause) { error.value = errorMessage(cause, '知识库加载失败') }
  finally { loading.value = false }
}
onMounted(load)
async function runDebug() {
  if (busy.value || !baseId.value || !validTopK.value || !question.value.trim()) return
  busy.value = true; error.value = ''; debug.value = undefined
  try { debug.value = await debugRetrieval(baseId.value, { question: question.value.trim(), topK: topK.value }) }
  catch (cause) { error.value = errorMessage(cause, '检索失败') }
  finally { busy.value = false }
}
function parseCases(): EvaluationCase[] {
  let value: unknown
  try { value = JSON.parse(dataset.value) } catch { throw new Error('JSON 格式无效，请检查后重试') }
  if (!Array.isArray(value) || value.length < 1 || value.length > 30) throw new Error('评测集必须包含 1–30 个用例')
  return value.map(item => {
    if (!item || typeof item.question !== 'string' || !item.question.trim() || item.question.length > 2000
      || !Array.isArray(item.expectedDocumentIds) || item.expectedDocumentIds.length > 20
      || item.expectedDocumentIds.some((id: unknown) => !Number.isSafeInteger(id) || Number(id) < 1))
      throw new Error('每个用例需要 question（1–2000 字）和 expectedDocumentIds（最多 20 个文档 ID）')
    return { question: item.question.trim(), expectedDocumentIds: [...new Set<number>(item.expectedDocumentIds)] }
  })
}
async function runEvaluation() {
  if (busy.value || !baseId.value || !validTopK.value) return
  error.value = ''
  try {
    const cases = parseCases(); busy.value = true
    const id = baseId.value, result = await evaluateRetrieval(id, { cases, topK: topK.value })
    runs.value.unshift({ ...result, dataset: JSON.stringify(cases), knowledgeBaseId: id })
    runs.value = runs.value.slice(0, 10)
    try { const stored = await listEvaluationRuns(id); if (baseId.value === id) history.value = stored } catch { /* in-memory row still shows the result */ }
  } catch (cause) { error.value = errorMessage(cause, '评测失败') }
  finally { busy.value = false }
}
async function importFile(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file) return
  error.value = ''
  try { if (file.size > 200000) throw new Error('JSON 文件不能超过 200 KB'); dataset.value = await file.text(); parseCases() }
  catch (cause) { error.value = errorMessage(cause, '导入失败') }
  input.value = ''
}
function example() { dataset.value = JSON.stringify([{ question: '请替换为文档中可以回答的问题', expectedDocumentIds: documents.value.filter(d => d.status === 'SUCCESS').slice(0, 1).map(d => d.id) }, { question: '请替换为知识库无法回答的问题', expectedDocumentIds: [] }], null, 2) }
function exportRun() {
  if (!latest.value) return
  const url = URL.createObjectURL(new Blob([JSON.stringify(latest.value, null, 2)], { type: 'application/json' }))
  const link = window.document.createElement('a'); link.href = url; link.download = 'retrieval-evaluation.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
function metric(value: number | null | undefined) { return value == null ? '—' : value.toFixed(3) }
function comparable(run: { datasetHash?: string }) { return !!run.datasetHash && allRuns.value.filter(item => item.datasetHash === run.datasetHash).length > 1 }
function formatTime(iso: string | undefined) {
  if (!iso) return ''
  const date = new Date(iso), now = new Date()
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<template>
  <AppLayout :context-name="bases.find(b => b.id === baseId)?.name">
    <div class="retrieval-lab">
      <div class="page-heading"><div><h1>检索调试与评测</h1><p>检查实际送入问答上下文的片段，用标注问题衡量 Hit@K、MRR 和来源召回。知识库所有者、管理员和拥有管理权限的成员可用。</p></div></div>
      <p v-if="error" role="alert" class="error">{{ error }} <button v-if="!bases.length" @click="load">重试</button></p>
      <p v-if="loading">正在加载知识库…</p>
      <p v-else-if="!bases.length">暂无可管理的知识库。创建知识库并上传文档后即可开始。</p>
      <template v-else>
        <section class="controls panel">
          <label>知识库<select v-model="baseId" aria-label="知识库" :disabled="busy"><option v-for="base in bases" :key="base.id" :value="base.id">{{ base.name }}</option></select></label>
          <label>Top K<input v-model.number="topK" aria-label="Top K" type="number" min="1" max="20" :disabled="busy" /></label>
          <span>1–20 个最终片段；批量运行最多 30 个问题</span>
        </section>
        <div class="lab-grid">
          <section class="panel"><h2>单问题调试</h2><label for="debug-question">调试问题</label><textarea id="debug-question" v-model="question" aria-label="调试问题" maxlength="2000" rows="4" placeholder="输入要检查的问题" :disabled="busy" />
            <button class="primary" data-test="debug" :disabled="busy || !question.trim() || !validTopK" @click="runDebug">{{ busy ? '处理中…' : '运行检索' }}</button>
            <p class="hint">展示经过阈值、去重及上下文长度筛选后的最终结果，不包含全部候选。相关性分数不代表回答准确率。</p>
            <div v-if="debug"><p>Top {{ debug.topK }} · {{ debug.latencyMs }} ms · {{ debug.sufficient ? '达到检索阈值' : '未达到检索阈值' }}</p>
              <p v-if="!debug.chunks.length">未检索到符合条件的片段。</p>
              <article v-for="(chunk, index) in debug.chunks" :key="chunk.chunkId" class="chunk"><strong>#{{ index + 1 }} {{ chunk.documentName }}</strong><small>文档 {{ chunk.documentId }} · 片段 {{ chunk.chunkId }}<template v-if="chunk.pageNumber"> · 页/节 {{ chunk.pageNumber }}</template></small><div class="scores">向量 {{ metric(chunk.vectorScore) }} · 关键词 {{ metric(chunk.keywordScore) }} · 混合 {{ metric(chunk.hybridScore) }}</div><pre>{{ chunk.content }}</pre></article>
            </div>
          </section>
          <section class="panel"><h2>批量评测</h2><p class="hint">每项包含 question 与 expectedDocumentIds；空数组表示预期无法回答。文档 ID 必须属于当前知识库且已索引成功。</p>
            <details><summary>可用文档 ID（{{ documents.length }}）</summary><p v-for="doc in documents" :key="doc.id">{{ doc.id }} · {{ doc.fileName }} · {{ doc.status }}</p></details>
            <div class="actions"><button :disabled="busy" @click="example">填入示例格式</button><label class="file-label">导入 JSON<input type="file" accept=".json,application/json" aria-label="导入评测集" :disabled="busy" @change="importFile" /></label></div>
            <label for="evaluation-dataset">评测集 JSON</label><textarea id="evaluation-dataset" v-model="dataset" aria-label="评测集 JSON" rows="10" :disabled="busy" spellcheck="false" />
            <button class="primary" data-test="evaluate" :disabled="busy || !validTopK" @click="runEvaluation">运行评测</button>
          </section>
        </div>
        <section class="panel"><div class="heading"><h2>运行对比</h2><button :disabled="!latest" @click="exportRun">导出最新结果</button></div><p class="hint">保存当前知识库最近 20 次运行，刷新后仍可查看；使用相同评测集的运行标为「同集」可直接对比；请保持文档和服务配置不变。Hit@K、MRR 与来源召回仅统计有答案用例；无答案用例单独统计未达到检索阈值的比例，并非模型拒答准确率。</p>
          <div class="table-scroll"><table><thead><tr><th>运行时间</th><th>Top K</th><th>用例</th><th>Hit@K</th><th>MRR</th><th>来源召回</th><th>无答案检索拒绝率</th><th>耗时</th></tr></thead><tbody><tr v-for="(run, index) in allRuns" :key="run.id || index" data-test="run-row"><td>{{ run.createdAt ? formatTime(run.createdAt) : '本次会话' }}<span v-if="comparable(run)" class="same-set" title="与其他运行使用同一评测集，可直接对比">同集</span></td><td>{{ run.topK }}</td><td>{{ run.caseCount }}（有答案 {{ run.answerableCount }} / 无答案 {{ run.unanswerableCount }}）</td><td>{{ metric(run.hitAtK) }}</td><td>{{ metric(run.mrr) }}</td><td>{{ metric(run.sourceRecall) }}</td><td>{{ metric(run.unanswerableRejectionRate) }}</td><td>{{ run.latencyMs }} ms</td></tr></tbody></table></div>
          <p v-if="!allRuns.length">尚无运行记录。导入标注问题后开始评测。</p>
          <details v-if="latest"><summary>最新运行的逐题结果</summary><article v-for="(item, i) in latest.cases" :key="i" class="chunk"><strong>{{ i + 1 }}. {{ item.question }}</strong><p>期望文档：{{ item.expectedDocumentIds.join(', ') || '无答案' }} · 检索文档（按片段排名）：{{ item.retrievedDocumentIds.join(', ') || '无' }}</p><p>命中 {{ metric(item.hitAtK) }} · 倒数排名 {{ metric(item.reciprocalRank) }} · 来源召回 {{ metric(item.sourceRecall) }} · {{ item.latencyMs }} ms<span v-if="item.correctRejection !== null"> · {{ item.correctRejection ? '符合无答案预期' : '仍检索到可用上下文' }}</span></p></article></details>
        </section>
      </template>
    </div>
  </AppLayout>
</template>

<style scoped>
.retrieval-lab { max-width: 1320px; }
h2 { font-size: 16px; margin: 0 0 14px; }
.hint { color: var(--muted); line-height: 1.7; font-size: 12px; }
.panel { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 22px; margin: 0 0 20px; }
.controls, .actions, .heading { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
.controls label { display: flex; align-items: center; gap: 8px; }
.controls select, .controls input { margin: 0; }
.heading { justify-content: space-between; margin-bottom: 4px; }
.heading h2 { margin: 0; }
.controls span { font-size: 12px; color: var(--muted); }
.lab-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.lab-grid .panel { margin: 0; }
label { display: block; font-size: 13px; color: var(--el-text-color-regular); }
textarea, select, input[type=number] { display: block; box-sizing: border-box; border: 1px solid #d5dfdb; border-radius: 8px; padding: 9px 11px; font: inherit; font-size: 13px; color: inherit; background: #fff; margin: 8px 0 14px; outline: 0; }
textarea:focus, select:focus, input:focus { border-color: var(--green); box-shadow: 0 0 0 3px #247b6920; }
textarea { width: 100%; resize: vertical; line-height: 1.6; }
select { min-width: 200px; }
input[type=number] { width: 90px; }
button, .file-label { border: 1px solid var(--line); background: #fff; color: var(--el-text-color-regular); border-radius: 8px; padding: 8px 14px; font-size: 13px; cursor: pointer; }
button:hover:not(:disabled), .file-label:hover { border-color: #91bdb4; color: var(--green); }
button:disabled { opacity: .5; cursor: not-allowed; }
.primary { background: var(--green); color: #fff; border-color: var(--green); }
.primary:hover:not(:disabled) { background: #1a725b; color: #fff; }
.error { padding: 12px 14px; background: #fff3ee; color: #a7352b; border-radius: 8px; font-size: 13px; }
.chunk { margin-top: 12px; padding: 14px; background: #f6f9f8; border-radius: 10px; font-size: 13px; }
.chunk small { display: block; margin: 6px 0; color: var(--muted); }
.scores { font-size: 12px; color: var(--green); font-variant-numeric: tabular-nums; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; font-size: 13px; line-height: 1.7; max-height: 300px; overflow: auto; margin: 8px 0 0; }
.table-scroll { overflow: auto; border: 1px solid var(--line); border-radius: 10px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; font-variant-numeric: tabular-nums; }
th { background: #f1f5f2; color: #536d5f; font-weight: 500; font-size: 12px; }
th, td { padding: 12px 14px; border-bottom: 1px solid var(--line); white-space: nowrap; }
tbody tr:last-child td { border-bottom: 0; }
.same-set { margin-left: 8px; padding: 1px 7px; border-radius: 9px; background: #edf6f2; color: var(--green); font-size: 11px; }
summary { cursor: pointer; margin: 14px 0; color: var(--green); font-size: 13px; }
details p { font-size: 13px; }
.file-label input { max-width: 180px; display: block; margin-top: 4px; }
.actions { margin: 14px 0; }
@media (max-width: 900px) { .lab-grid { grid-template-columns: 1fr; } }
</style>
