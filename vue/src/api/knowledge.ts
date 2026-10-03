import { http } from './http'
import type { KnowledgeBase } from '@/types'
export const listKnowledgeBases = (keyword?: string) => http.get('/knowledge-bases', { params: { keyword } }) as Promise<KnowledgeBase[]>
export const getKnowledgeBase = (id: number) => http.get(`/knowledge-bases/${id}`) as Promise<KnowledgeBase>
export const createKnowledgeBase = (payload: { name: string; description?: string; shared?: boolean }) => http.post('/knowledge-bases', payload) as Promise<KnowledgeBase>
export const updateKnowledgeBase = (id: number, payload: { name: string; description?: string; shared?: boolean }) => http.put(`/knowledge-bases/${id}`, payload) as Promise<KnowledgeBase>
export const deleteKnowledgeBase = (id: number) => http.delete(`/knowledge-bases/${id}`)

