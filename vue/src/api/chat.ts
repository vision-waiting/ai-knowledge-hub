import { http } from './http'
import type { ChatSession, ChatMessage } from '@/types'
export const listSessions = () => http.get('/chat/sessions') as Promise<ChatSession[]>
export const listMessages = (sessionId: number) => http.get(`/chat/sessions/${sessionId}/messages`) as Promise<ChatMessage[]>
export const deleteSession = (sessionId: number) => http.delete(`/chat/sessions/${sessionId}`)
export const feedbackMessage = (messageId: number, feedback: 'HELPFUL' | 'NOT_HELPFUL', reason?: string) => http.put(`/chat/messages/${messageId}/feedback`, { feedback, reason })
export const streamUrl = (knowledgeBaseId: number, question: string, sessionId?: number, provider?: string) => {
  const params = new URLSearchParams({ knowledgeBaseId: String(knowledgeBaseId), question })
  if (sessionId) params.set('sessionId', String(sessionId))
  if (provider) params.set('provider', provider)
  return `${http.defaults.baseURL}/chat/stream?${params.toString()}`
}
