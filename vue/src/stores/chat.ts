import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { listMessages, streamUrl } from '@/api/chat'
import { createSseParser } from '@/utils/sse'
import type { ChatMessage, ChatSession, Citation } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const historyLoading = ref(false)
  const status = ref('')
  const sessionId = ref<number | undefined>()
  const knowledgeBaseId = ref<number>()
  let controller: AbortController | null = null
  let generation = 0

  function stop() {
    generation++
    controller?.abort()
    controller = null
    if (loading.value) {
      const last = messages.value[messages.value.length - 1]
      if (last?.role === 'ASSISTANT') last.error = '已停止生成'
    }
    loading.value = false
    status.value = ''
    historyLoading.value = false
  }
  function clear() { stop(); messages.value = []; sessionId.value = undefined }
  function selectKnowledgeBase(id: number) {
    if (knowledgeBaseId.value !== id) { clear(); knowledgeBaseId.value = id }
  }
  async function openSession(session: ChatSession) {
    clear()
    knowledgeBaseId.value = session.knowledgeBaseId
    historyLoading.value = true
    const current = generation
    try {
      const saved = await listMessages(session.id)
      if (current !== generation) return
      messages.value = saved
      sessionId.value = session.id
    } finally { if (current === generation) historyLoading.value = false }
  }
  async function send(id: number, question: string, provider?: string) {
    selectKnowledgeBase(id)
    if (!question.trim() || loading.value || historyLoading.value) return
    messages.value.push({ role: 'USER', content: question })
    const assistant = reactive<ChatMessage>({ role: 'ASSISTANT', content: '', citations: [] })
    messages.value.push(assistant)
    loading.value = true
    status.value = provider === 'cloud' ? '正在检索资料并连接云端 API' : '正在检索资料并准备回答'
    const active = new AbortController()
    controller = active
    const current = ++generation
    let completed = false
    const parser = createSseParser(({ event, data }) => {
      if (current !== generation) return
      if (event === 'status') status.value = data
      if (event === 'message') { assistant.content += data; status.value = '' }
      if (event === 'citation') assistant.citations?.push(JSON.parse(data) as Citation)
      if (event === 'meta') { const meta = JSON.parse(data); assistant.mode = meta.mode; assistant.model = meta.model; assistant.id = meta.messageId || assistant.id; assistant.retrieval = meta.retrieval ?? assistant.retrieval; sessionId.value = meta.sessionId || sessionId.value }
      if (event === 'session') sessionId.value = JSON.parse(data).sessionId
      if (event === 'done') { const payload = JSON.parse(data); sessionId.value = payload.data?.sessionId || payload.data || sessionId.value; completed = true }
      if (event === 'error') {
        let detail = data
        try { detail = JSON.parse(data).message || data } catch { /* Plain-text errors are valid SSE payloads. */ }
        assistant.error = detail || '问答处理失败，请重试'
        completed = true
      }
    })
    try {
      const response = await fetch(streamUrl(id, question, sessionId.value, provider), {
        headers: { Authorization: `Bearer ${localStorage.getItem('ai_kh_token') || ''}`, Accept: 'text/event-stream' }, signal: active.signal,
      })
      if (current !== generation) { await response.body?.cancel(); return }
      if (!response.ok || !response.body) {
        let message = `问答请求失败 (${response.status})`
        try { message = (await response.json()).message || message } catch { /* Non-JSON gateway responses use the HTTP status. */ }
        throw new Error(message)
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      try {
        while (current === generation) {
          const { value, done } = await reader.read()
          if (done) break
          parser.push(decoder.decode(value, { stream: true }))
        }
        parser.push(decoder.decode())
        parser.finish()
      } finally { reader.releaseLock() }
      if (!completed && current === generation) assistant.error = '连接已中断，回答可能不完整'
    } catch (error) {
      if (current === generation && (error as Error).name !== 'AbortError') assistant.error = (error as Error).message || '连接问答服务失败'
    } finally {
      if (current === generation) { loading.value = false; status.value = ''; controller = null }
    }
  }
  const lastAnswer = computed(() => messages.value[messages.value.length - 1])
  return { messages, loading, historyLoading, status, sessionId, knowledgeBaseId, lastAnswer, send, stop, clear, selectKnowledgeBase, openSession }
})
