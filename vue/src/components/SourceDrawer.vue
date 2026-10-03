<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDocument, getDocumentChunks, saveDocument } from '@/api/documents'
import { errorMessage } from '@/utils/errors'
import type { Citation, DocumentChunk, DocumentItem } from '@/types'

const props = defineProps<{ documentId?: number; citation?: Citation }>()
const emit = defineEmits<{ close: [] }>()
const document = ref<DocumentItem>()
const chunks = ref<DocumentChunk[]>([])
const loading = ref(false)
const downloading = ref(false)
const error = ref('')
const open = computed({ get: () => Boolean(props.documentId), set: value => { if (!value) emit('close') } })
let request = 0
async function load() {
  const current = ++request
  document.value = undefined; chunks.value = []; error.value = ''
  if (!props.documentId) return
  loading.value = true
  try {
    const [detail, originals] = await Promise.all([getDocument(props.documentId), getDocumentChunks(props.documentId)])
    if (current !== request) return
    document.value = detail; chunks.value = originals
  } catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally { if (current === request) loading.value = false }
}
async function download() {
  if (!props.documentId) return
  downloading.value = true
  try { await saveDocument(props.documentId, document.value?.fileName || props.citation?.documentName || 'document') }
  catch (cause) { ElMessage.error(errorMessage(cause, '下载失败')) }
  finally { downloading.value = false }
}
watch(() => props.documentId, load, { immediate: true })
function highlightedPrefix(chunk: DocumentChunk, excerpt: string): string {
  if (!chunk.content) return ''
  const cleaned = excerpt.replace(/…$/, '').trim()
  if (chunk.content.startsWith(cleaned)) return cleaned
  return ''
}
</script>
<template>
  <el-drawer v-model="open" title="文档原文" size="min(640px, 100%)" class="source-drawer-panel">
    <div class="source-drawer" v-loading="loading">
      <div class="source-heading"><h2>{{ document?.fileName || citation?.documentName }}</h2><el-button :icon="Download" :loading="downloading" @click="download">下载原文件</el-button></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-button v-if="error" @click="load">重新加载原文</el-button>
      <el-alert v-if="document?.status === 'ARCHIVED'" title="这是历史版本。当前回答的引用仍指向当时使用的资料。" type="warning" :closable="false" />
      <el-alert v-if="document?.status === 'TRASHED'" title="该资料已移入回收站，不再参与检索；此处保留历史引用。" type="warning" :closable="false" />
      <template v-if="document"><el-tag size="small" type="info">{{ document.summaryMode === 'OPENAI_COMPATIBLE' ? 'AI 摘要' : '文本摘录' }}</el-tag><p v-if="document.summary" class="source-summary">{{ document.summary }}</p><div class="tag-list"><el-tag v-for="tag in document.tags" :key="tag" type="info">{{ tag }}</el-tag></div></template>
      <div v-if="citation" class="source-highlight"><strong>引用 [{{ citation.index }}] · 第 {{ citation.pageNumber || '-' }} {{ document?.pageReferenceType === 'SECTION' ? '段' : '页' }}</strong><p>{{ citation.excerpt }}</p></div>
      <section v-for="chunk in chunks" :key="chunk.id" :class="['source-chunk', { selected: chunk.id === citation?.chunkId }]"><h3>第 {{ chunk.pageNumber || '-' }} {{ document?.pageReferenceType === 'SECTION' ? '段' : '页' }} · 片段 {{ (chunk.chunkIndex ?? 0) + 1 }}</h3><p v-if="chunk.id === citation?.chunkId && citation.excerpt"><mark>{{ highlightedPrefix(chunk, citation.excerpt) }}</mark>{{ chunk.content.substring(highlightedPrefix(chunk, citation.excerpt).length) }}</p><p v-else>{{ chunk.content }}</p></section>
      <el-empty v-if="!loading && !error && !chunks.length" description="暂无可预览原文" />
    </div>
  </el-drawer>
</template>
