export interface User { id: number; username: string; nickname?: string; role: 'ADMIN' | 'USER' }
export interface KnowledgeBase { id: number; name: string; description?: string; ownerId: number; documentCount: number; createdAt?: string; updatedAt?: string }
export interface DocumentItem { id: number; knowledgeBaseId: number; fileName: string; fileType: string; fileSize: number; pageCount?: number; status: string; errorMessage?: string; summary?: string; createdAt?: string }
export interface Citation { index: number; documentId: number; documentName: string; pageNumber?: number; chunkId: number; excerpt: string }
export interface ChatMessage { id?: number; role: 'USER' | 'ASSISTANT'; content: string; citations?: Citation[]; mode?: string }
export interface DashboardOverview { knowledgeBaseCount: number; documentCount: number; chatCount: number; aiCallCount: number; failedDocumentCount: number }
export interface Result<T> { code: number; message: string; data: T; timestamp: number }

