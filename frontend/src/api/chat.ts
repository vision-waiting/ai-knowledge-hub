import { http } from './http'
export const listSessions = () => http.get('/chat/sessions')
export const listMessages = (sessionId: number) => http.get(`/chat/sessions/${sessionId}/messages`)
export const streamUrl = (knowledgeBaseId: number, question: string, sessionId?: number) => {
  const params = new URLSearchParams({ knowledgeBaseId: String(knowledgeBaseId), question })
  if (sessionId) params.set('sessionId', String(sessionId))
  return `${http.defaults.baseURL}/chat/stream?${params.toString()}`
}

