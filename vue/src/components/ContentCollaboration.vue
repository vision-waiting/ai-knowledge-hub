<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { http } from '@/api/http'
import { errorMessage, formatDate } from '@/utils/errors'
export interface ContentEntry { id: number; kind: string; title: string; body: string; aliases?: string; sourceDocumentId?: number; sortOrder: number; updatedAt: string; version: number }
interface Suggestion { id: number; contentId: number; baseVersion: number; authorId: number; authorName: string; originalTitle: string; originalBody: string; title: string; body: string; reason: string; status: string; reviewerName?: string; createdAt: string }
interface Revision { version: number; content: ContentEntry; actorName?: string; contributorName?: string; action: string; createdAt: string }
const props = defineProps<{ knowledgeBaseId: number; canManage: boolean; userId?: number; mode: 'suggest' | 'review' | 'history'; entry?: ContentEntry }>()
const emit = defineEmits<{ changed: []; close: [] }>()
const title = ref(''), body = ref(''), reason = ref(''), error = ref(''), message = ref(''), loading = ref(false), busy = ref(false)
const suggestions = ref<Suggestion[]>([]), revisions = ref<Revision[]>([]), status = ref('OPEN'), offset = ref(0), hasMore = ref(false)
let generation = 0
const root = () => `/knowledge-bases/${props.knowledgeBaseId}`
async function load() {
  const current = ++generation; error.value = ''; suggestions.value = []; revisions.value = []; hasMore.value = false
  if (props.mode === 'suggest') { loading.value = false; return }
  loading.value = true
  try {
    if (props.mode === 'review') {
      const result = await http.get(`${root()}/suggestions`, { params: { status: status.value, offset: offset.value } }) as unknown as { items: Suggestion[]; hasMore: boolean }
      if (current === generation) { suggestions.value = result.items; hasMore.value = result.hasMore }
    } else if (props.entry) {
      const result = await http.get(`${root()}/content/${props.entry.id}/history`) as unknown as Revision[]
      if (current === generation) revisions.value = result
    }
  } catch (cause) { if (current === generation) error.value = errorMessage(cause) }
  finally { if (current === generation) loading.value = false }
}
watch(() => [props.knowledgeBaseId, props.mode, props.entry?.id], () => {
  title.value = props.entry?.title || ''; body.value = props.entry?.body || ''; reason.value = ''; message.value = ''; offset.value = 0; void load()
}, { immediate: true })
onBeforeUnmount(() => generation++)
async function mutate(path: string, payload?: unknown) {
  if (busy.value) return
  const current = generation; busy.value = true; error.value = ''; message.value = ''
  try {
    await http.post(`${root()}${path}`, payload)
    if (current !== generation) return
    if (props.mode === 'suggest') message.value = '已提交，等待维护者审核。'
    else { await load(); message.value = '操作已完成。' }
    emit('changed')
  } catch (cause) { if (current === generation) error.value = errorMessage(cause) }
  finally { busy.value = false }
}
function submit() {
  if (!props.entry || !title.value.trim() || !body.value.trim() || !reason.value.trim()) { error.value = '请填写标题、正文和修改原因。'; return }
  void mutate('/suggestions', { contentId: props.entry.id, baseVersion: props.entry.version, title: title.value.trim(), body: body.value, reason: reason.value.trim() })
}
function restore(version: number) {
  if (window.confirm(`将当前条目恢复为版本 ${version}？这会创建新版本，并保留现有历史。`)) void mutate(`/content/${props.entry?.id}/history/${version}/restore`)
}
function page(step: number) { offset.value = Math.max(0, offset.value + step * 20); void load() }
const actionName: Record<string, string> = { CREATED: '创建', EDITED: '直接编辑', MERGED: '建议合入', RESTORED: '历史恢复', BASELINE: '初始快照' }
</script>
<template>
  <section class="collaboration" :aria-label="mode === 'review' ? '修改建议' : mode === 'history' ? '版本历史' : '提出修改建议'">
    <div class="collaboration-heading"><h2>{{ mode === 'review' ? '修改建议' : mode === 'history' ? `版本历史 · ${entry?.title}` : '提出修改建议' }}</h2><button v-if="mode !== 'review'" :disabled="busy" @click="emit('close')">收起</button></div>
    <p v-if="error" role="alert">{{ error }} <button v-if="mode !== 'suggest'" :disabled="busy" @click="load">重新加载</button></p>
    <p v-if="message" role="status">{{ message }}</p><p v-if="loading" role="status">正在加载…</p>
    <form v-if="mode === 'suggest' && entry && !message" @submit.prevent="submit">
      <p class="muted">基于版本 {{ entry.version }}。审核通过前，原内容保持不变。请在原因中附上依据或参考来源。</p>
      <details><summary>查看原内容</summary><h3>{{ entry.title }}</h3><pre>{{ entry.body }}</pre></details>
      <label>建议标题<input v-model="title" aria-label="建议标题" maxlength="200" required :disabled="busy" /></label>
      <label>建议正文（Markdown）<textarea v-model="body" aria-label="建议正文" maxlength="20000" rows="8" required :disabled="busy" /></label>
      <label>修改原因与依据<textarea v-model="reason" aria-label="修改原因" maxlength="1000" rows="3" required :disabled="busy" /></label>
      <button class="submit-action" type="submit" :disabled="busy">{{ busy ? '正在提交…' : '提交修改建议' }}</button>
    </form>
    <template v-if="mode === 'review'">
      <div class="review-toolbar"><label>状态 <select v-model="status" :disabled="busy || loading" @change="offset = 0; load()"><option value="OPEN">待审核</option><option value="MERGED">已合入</option><option value="CLOSED">已关闭</option></select></label><button :disabled="busy || loading" @click="load">刷新</button></div>
      <p class="muted">建议和审核记录对本库的阅读者可见。只有维护者可以合入；内容已更新时，需要重新提交建议。</p>
      <article v-for="suggestion in suggestions" :key="suggestion.id" class="suggestion">
        <h3>{{ suggestion.title }}</h3><p class="muted">{{ suggestion.authorName || '原贡献者' }} · 基于版本 {{ suggestion.baseVersion }} · {{ formatDate(suggestion.createdAt) }}</p>
        <p class="reason">{{ suggestion.reason }}</p>
        <div class="comparison"><section><h4>修改前</h4><strong>{{ suggestion.originalTitle }}</strong><pre>{{ suggestion.originalBody }}</pre></section><section><h4>修改后</h4><strong>{{ suggestion.title }}</strong><pre>{{ suggestion.body }}</pre></section></div>
        <div v-if="suggestion.status === 'OPEN'" class="review-toolbar"><button v-if="canManage" :data-test="`merge-${suggestion.id}`" class="submit-action" :disabled="busy || loading" @click="mutate(`/suggestions/${suggestion.id}/merge`)">合入修改</button><button v-if="canManage || suggestion.authorId === userId" :disabled="busy || loading" @click="mutate(`/suggestions/${suggestion.id}/close`)">{{ canManage ? '关闭建议' : '撤回建议' }}</button></div>
        <p v-else class="muted">{{ suggestion.status === 'MERGED' ? '已合入' : '已关闭' }} · {{ suggestion.reviewerName || '维护者' }}</p>
      </article>
      <p v-if="!loading && !error && !suggestions.length">暂无{{ { OPEN: '待审核', MERGED: '已合入', CLOSED: '已关闭' }[status as 'OPEN'] }}的修改建议。阅读知识条目时，可以点击「建议修改」参与完善。</p>
      <div v-if="offset || hasMore" class="review-toolbar"><button :disabled="!offset || busy || loading" @click="page(-1)">上一页</button><span>第 {{ offset / 20 + 1 }} 页</span><button :disabled="!hasMore || busy || loading" @click="page(1)">下一页</button></div>
    </template>
    <template v-if="mode === 'history'">
      <p class="muted">展示最近 100 个版本。恢复会生成新版本，保留原有历史。</p>
      <details v-for="revision in revisions" :key="revision.version" class="revision"><summary>版本 {{ revision.version }} · {{ actionName[revision.action] || revision.action }} · {{ revision.actorName || '历史内容' }} · {{ formatDate(revision.createdAt) }}</summary><p v-if="revision.contributorName">贡献者：{{ revision.contributorName }}</p><h3>{{ revision.content.title }}</h3><pre>{{ revision.content.body }}</pre><p v-if="revision.content.aliases">别名：{{ revision.content.aliases }}</p><p v-if="revision.content.sourceDocumentId">来源文档 #{{ revision.content.sourceDocumentId }}</p><button v-if="canManage && revision.version !== revisions[0]?.version" :disabled="busy || loading" @click="restore(revision.version)">恢复此版本</button></details>
    </template>
  </section>
</template>
<style scoped>
.collaboration { margin: 20px 0; padding-block: 20px; border-block: 1px solid var(--line); }
.collaboration-heading, .review-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.collaboration-heading { justify-content: space-between; }
h2 { font-size: 20px; margin: 0 0 12px; } h3 { font-size: 16px; margin: 12px 0; } h4 { font-size: 13px; color: var(--muted); margin: 0 0 12px; }
p { line-height: 1.7; font-size: 14px; overflow-wrap: anywhere; }
form { max-width: 850px; } form label { display: block; margin: 16px 0; font-size: 14px; }
input, textarea { display: block; width: 100%; box-sizing: border-box; margin-top: 7px; }
button, input, textarea, select { font: inherit; font-size: 14px; padding: 9px 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface, white); color: var(--text); }
button, summary { cursor: pointer; } button:disabled { cursor: default; opacity: .55; }
button:hover:not(:disabled) { border-color: var(--green); }
button:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible, summary:focus-visible { outline: 2px solid var(--green); outline-offset: 3px; }
.submit-action { background: var(--green); color: white; border-color: var(--green); }
.suggestion { padding-block: 20px; border-bottom: 1px solid var(--line); }
.comparison { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; margin: 20px 0; }
.comparison section { padding: 16px; background: var(--bg, #f4f7f5); border: 1px solid var(--line); border-radius: 8px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; font-size: 14px; line-height: 1.8; max-height: 420px; overflow: auto; }
.revision { padding-block: 14px; border-bottom: 1px solid var(--line); }
summary { line-height: 1.8; overflow-wrap: anywhere; }
[role=alert] { color: #a7352b; }
@media (max-width: 700px) { .comparison { grid-template-columns: minmax(0, 1fr); gap: 12px; } }
</style>
