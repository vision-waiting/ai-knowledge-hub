import { http } from './http'
import type { DocumentItem } from '@/types'
export const listDocuments = (knowledgeBaseId: number) => http.get(`/knowledge-bases/${knowledgeBaseId}/documents`) as Promise<DocumentItem[]>
export const uploadDocument = (knowledgeBaseId: number, file: File) => { const body = new FormData(); body.append('file', file); return http.post(`/knowledge-bases/${knowledgeBaseId}/documents`, body, { headers: { 'Content-Type': 'multipart/form-data' } }) as Promise<DocumentItem> }
export const deleteDocument = (id: number) => http.delete(`/documents/${id}`)
export const reindexDocument = (id: number) => http.post(`/documents/${id}/reindex`) as Promise<DocumentItem>
export const downloadDocument = (id: number) => http.get(`/documents/${id}/download`, { responseType: 'blob' })

