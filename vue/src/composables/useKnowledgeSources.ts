import { onBeforeUnmount, ref, watch } from 'vue'
import { listKnowledgeBases } from '@/api/knowledge'
import { listDocuments } from '@/api/documents'
import { errorMessage } from '@/utils/errors'
import type { DocumentItem, KnowledgeBase } from '@/types'

export function useKnowledgeSources(initialId?: number) {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const knowledgeBaseId = ref<number | undefined>(initialId)
  const documents = ref<DocumentItem[]>([])
  const documentIds = ref<number[]>([])
  const loading = ref(false)
  const error = ref('')
  let request = 0
  let disposed = false
  async function loadDocuments() {
    const current = ++request
    documents.value = []; documentIds.value = []; error.value = ''
    if (!knowledgeBaseId.value) { loading.value = false; return }
    loading.value = true
    try {
      const result = await listDocuments(knowledgeBaseId.value)
      if (!disposed && current === request) documents.value = result
    } catch (cause) { if (!disposed && current === request) error.value = errorMessage(cause, '文档加载失败') }
    finally { if (!disposed && current === request) loading.value = false }
  }
  async function initialize() {
    loading.value = true; error.value = ''
    try {
      const result = await listKnowledgeBases()
      if (disposed) return
      knowledgeBases.value = result
      if (knowledgeBaseId.value && !result.some(kb => kb.id === knowledgeBaseId.value)) {
        knowledgeBaseId.value = undefined
        error.value = '当前知识库不可访问，请选择其他知识库。'
      } else if (!knowledgeBaseId.value && result.length) knowledgeBaseId.value = result[0].id
      else await loadDocuments()
    } catch (cause) { if (!disposed) { error.value = errorMessage(cause, '知识库加载失败'); loading.value = false } }
    finally { if (!knowledgeBaseId.value) loading.value = false }
  }
  watch(knowledgeBaseId, loadDocuments)
  onBeforeUnmount(() => { disposed = true; request++ })
  void initialize()
  return { knowledgeBases, knowledgeBaseId, documents, documentIds, loading, error, reload: initialize }
}
