<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import DocumentSourcePicker from '@/components/DocumentSourcePicker.vue'
import SaveNoteButton from '@/components/SaveNoteButton.vue'
import { useKnowledgeSources } from '@/composables/useKnowledgeSources'
import { createAnalysis, listAnalyses, type Analysis } from '@/api/intelligence'
import { errorMessage, formatDate } from '@/utils/errors'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const { knowledgeBases, knowledgeBaseId, documents, documentIds, loading, error: sourceError, reload } = useKnowledgeSources(Number(route.params.id) || undefined)
const kind = ref<Analysis['type']>('SUMMARY')
const focus = ref(''); const generating = ref(false); const error = ref(''); const historyLoading = ref(false)
const history = ref<Analysis[]>([]); const result = ref<Analysis>()
const canGenerate = computed(() => knowledgeBaseId.value && documentIds.value.length >= (kind.value === 'COMPARE' ? 2 : 1) && !loading.value && !generating.value)
let generation = 0; let historyRequest = 0
async function loadHistory() {
  const current = ++historyRequest
  if (!knowledgeBaseId.value) { history.value = []; historyLoading.value = false; return }
  historyLoading.value = true
  try {
    const data = await listAnalyses(knowledgeBaseId.value)
    if (current !== historyRequest) return
    history.value = data
    if (!result.value) result.value = data[0]
  } catch (cause) { if (current === historyRequest) error.value = errorMessage(cause, '历史结果加载失败') }
  finally { if (current === historyRequest) historyLoading.value = false }
}
async function generate() {
  if (!canGenerate.value || !knowledgeBaseId.value) return
  const current = ++generation
  historyRequest++; historyLoading.value = false
  generating.value = true; error.value = ''; result.value = undefined
  try {
    const data = await createAnalysis(knowledgeBaseId.value, { type: kind.value, documentIds: [...documentIds.value], focus: focus.value.trim() })
    if (current !== generation) return
    result.value = data; history.value = [data, ...history.value.filter(item => item.id !== data.id)]
  } catch (cause) { if (current === generation) error.value = errorMessage(cause, '生成失败，所选资料已保留，请重试') }
  finally { if (current === generation) generating.value = false }
}
watch(knowledgeBaseId, () => { generation++; generating.value = false; result.value = undefined; history.value = []; error.value = ''; void loadHistory() }, { immediate: true })
watch(() => route.params.id, value => { if (value) knowledgeBaseId.value = Number(value) })
onBeforeUnmount(() => { generation++; historyRequest++ })
</script>
<template><AppLayout :context-id="knowledgeBaseId" :context-name="knowledgeBases.find(base => base.id === knowledgeBaseId)?.name">
  <div class="page-heading"><div><span class="section-kicker">总结与对比</span><h1>文档总结与对比</h1><p>梳理资料重点，比较不同文档，把有价值的结论保存下来。</p></div></div>
  <div class="tools-layout">
    <aside class="tool-panel tool-settings">
      <div class="tool-tabs" role="group" aria-label="分析类型"><button :class="{ active: kind === 'SUMMARY' }" :disabled="generating" @click="kind = 'SUMMARY'">文档总结</button><button :class="{ active: kind === 'COMPARE' }" :disabled="generating" @click="kind = 'COMPARE'">文档对比</button></div>
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" /><el-button v-if="sourceError" text @click="reload">重新加载资料</el-button>
      <DocumentSourcePicker v-model:knowledge-base-id="knowledgeBaseId" v-model="documentIds" :knowledge-bases="knowledgeBases" :documents="documents" :disabled="generating" :loading="loading" />
      <label class="tool-field-label" for="analysis-focus">{{ kind === 'COMPARE' ? '想比较哪些方面？（选填）' : '特别关注的内容（选填）' }}</label>
      <textarea id="analysis-focus" v-model="focus" class="tool-textarea" rows="3" maxlength="500" :disabled="generating" :placeholder="kind === 'COMPARE' ? '例如：功能范围、技术方案、优缺点' : '例如：关键结论、使用步骤、注意事项'" />
      <p class="muted tool-help">{{ kind === 'COMPARE' ? '请选择 2～5 份已处理完成的文档。' : '请选择 1～5 份已处理完成的文档。' }}</p>
      <el-button class="tool-primary" type="primary" :loading="generating" :disabled="!canGenerate" @click="generate">{{ generating ? '正在阅读资料…' : kind === 'COMPARE' ? '开始对比' : '生成总结' }}</el-button>
    </aside>
    <section class="tool-results" aria-label="分析结果">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <div v-if="generating" class="tool-panel tool-empty" role="status"><el-icon class="is-loading"><Refresh /></el-icon><h2>正在整理资料</h2><p>完成后会自动保存到历史结果，请稍候。</p></div>
      <article v-else-if="result" class="tool-panel analysis-result">
        <header class="tool-result-heading"><div><span class="section-kicker">{{ result.type === 'COMPARE' ? '对比结果' : '文档总结' }}</span><h2>{{ result.title }}</h2><p class="muted">{{ formatDate(result.createdAt) }} · {{ result.mode === 'OPENAI_COMPATIBLE' ? 'AI 生成' : '原文整理' }}</p></div><SaveNoteButton :content="result.content" :title="result.title" /></header>
        <p v-if="result.sourceNotice" class="source-notice">{{ result.sourceNotice }}</p>
        <div class="markdown-body" v-html="renderMarkdown(result.content)" />
      </article>
      <div v-else class="tool-panel tool-empty"><el-icon><Document /></el-icon><h2>让长文档更容易理解</h2><p>选择资料生成结构化总结，或对比多份文档中的共同点与差异。</p></div>
      <section class="tool-panel"><div class="section-heading"><h2>历史结果</h2><el-button text :icon="Refresh" :disabled="generating" :loading="historyLoading" @click="loadHistory">刷新</el-button></div><p v-if="!history.length" class="muted">生成后的结果会保存在这里。</p><div class="tool-history"><button v-for="item in history" :key="item.id" :disabled="generating" :class="{ active: result?.id === item.id }" @click="result = item"><span><strong>{{ item.title }}</strong><small>{{ item.type === 'COMPARE' ? '对比' : '总结' }} · {{ formatDate(item.createdAt) }}</small></span><span aria-hidden="true">↗</span></button></div></section>
    </section>
  </div>
</AppLayout></template>
