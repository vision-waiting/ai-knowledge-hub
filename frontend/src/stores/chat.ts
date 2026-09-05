import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { streamUrl } from '@/api/chat'
import type { ChatMessage, Citation } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const sessionId = ref<number | undefined>()
  let controller: AbortController | null = null
  async function send(knowledgeBaseId: number, question: string) {
    if (!question.trim() || loading.value) return
    messages.value.push({ role: 'USER', content: question })
    const assistant: ChatMessage = { role: 'ASSISTANT', content: '', citations: [] }
    messages.value.push(assistant); loading.value = true; controller = new AbortController()
    try {
      const response = await fetch(streamUrl(knowledgeBaseId, question, sessionId.value), { headers: { Authorization: `Bearer ${localStorage.getItem('ai_kh_token') || ''}` }, signal: controller.signal })
      if (!response.ok || !response.body) throw new Error('stream failed')
      const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
      while (true) {
        const { value, done } = await reader.read(); if (done) break; buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n'); buffer = blocks.pop() || ''
        for (const block of blocks) {
          const event = block.match(/^event:\s*(.+)$/m)?.[1]; const data = block.match(/^data:\s*(.*)$/m)?.[1] || ''
          if (event === 'message') assistant.content += data
          if (event === 'citation') { try { assistant.citations?.push(JSON.parse(data) as Citation) } catch { /* ignore malformed event */ } }
          if (event === 'done') { try { sessionId.value = JSON.parse(data).data } catch { /* keep existing session */ } }
          if (event === 'error') assistant.content = data || '问答处理失败，请稍后重试'
        }
      }
    } catch (error) { if ((error as Error).name !== 'AbortError') assistant.content = '连接问答服务失败，请检查后端是否启动。' }
    finally { loading.value = false; controller = null }
  }
  function stop() { controller?.abort(); loading.value = false }
  function clear() { stop(); messages.value = []; sessionId.value = undefined }
  const lastAnswer = computed(() => messages.value[messages.value.length - 1])
  return { messages, loading, sessionId, lastAnswer, send, stop, clear }
})
