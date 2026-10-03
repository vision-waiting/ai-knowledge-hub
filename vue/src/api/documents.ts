import { http } from './http'
import type { DocumentItem, DocumentChunk } from '@/types'
export const listDocuments = (knowledgeBaseId: number) => http.get(`/knowledge-bases/${knowledgeBaseId}/documents`) as Promise<DocumentItem[]>
export const uploadDocument = (knowledgeBaseId: number, file: File) => { const body = new FormData(); body.append('file', file); return http.post(`/knowledge-bases/${knowledgeBaseId}/documents`, body, { timeout: 120000 }) as Promise<DocumentItem> }
export const getDocument = (id: number) => http.get(`/documents/${id}`) as Promise<DocumentItem>
export const getDocumentChunks = (id: number) => http.get(`/documents/${id}/chunks`) as Promise<DocumentChunk[]>
export const deleteDocument = (id: number) => http.delete(`/documents/${id}`)
export const reindexDocument = (id: number) => http.post(`/documents/${id}/reindex`) as Promise<DocumentItem>
export const downloadDocument = (id: number) => http.get(`/documents/${id}/download`, { responseType: 'blob' })
export async function saveDocument(id: number, name: string) {
  const blob = await downloadDocument(id) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export interface DocumentVersion { document: DocumentItem; versionNumber: number; previousVersionId?: number; deletedAt?: string }
export const listVersions = (id: number) => http.get(`/documents/${id}/versions`) as Promise<DocumentVersion[]>
export const listTrash = (id: number) => http.get(`/knowledge-bases/${id}/trash`) as Promise<DocumentItem[]>
export const restoreDocument = (id: number) => http.post(`/documents/${id}/restore`) as Promise<DocumentItem>
export const uploadVersion = (id: number, file: File) => { const body = new FormData(); body.append('file', file); return http.post(`/documents/${id}/versions`, body, {timeout:120000}) as Promise<DocumentItem> }
