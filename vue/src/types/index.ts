export interface User { id: number; username: string; nickname?: string; role: 'ADMIN' | 'USER'; status?: number; createdAt?: string; updatedAt?: string }
export interface KnowledgeBase { starred?: boolean; starCount?: number; canWrite?: boolean; canManage?: boolean; shared?: boolean; sharingLocked?: boolean; id: number; name: string; description?: string; ownerId: number; ownerUsername?: string; ownerNickname?: string; documentCount: number; createdAt?: string; updatedAt?: string }
export interface DocumentItem { id: number; knowledgeBaseId: number; fileName: string; fileType: string; fileSize: number; pageCount?: number; status: string; errorMessage?: string; summary?: string; tags?: string[]; createdAt?: string; pageReferenceType?: 'PAGE' | 'SECTION'; summaryMode?: string; tagMode?: string }
export interface Citation { index: number; documentId: number; documentName: string; pageNumber?: number; chunkId: number; excerpt: string }
export interface RetrievalTraceChunk { chunkId: number; documentId: number; pageNumber?: number; vectorScore: number; keywordScore: number; hybridScore: number }
export interface RetrievalTrace { rewrittenQuery: string; topK: number; sufficient: boolean; latencyMs: number; chunks: RetrievalTraceChunk[] }
export interface ChatMessage { id?: number; role: 'USER' | 'ASSISTANT'; content: string; citations?: Citation[]; retrieval?: RetrievalTrace | null; mode?: string; model?: string; feedback?: string; feedbackReason?: string; error?: string; createdAt?: string }
export interface SearchResult { documentId: number; documentName: string; chunkId: number; pageNumber?: number; chunkIndex?: number; excerpt: string; score: number }
export interface ChatSession { id: number; knowledgeBaseId: number; title: string; createdAt?: string; updatedAt?: string }
export interface DocumentChunk { id: number; content: string; pageNumber?: number; chunkIndex?: number }
export interface DashboardOverview { knowledgeBaseCount: number; documentCount: number; chatCount: number; aiCallCount: number; failedDocumentCount: number; processingCount?: number; userCount?: number; chunkCount?: number; todayQuestionCount?: number; weeklyQuestions?: { date: string; count: number }[]; weeklyUploads?: { date: string; count: number }[]; documentTypes?: { name: string; count: number }[]; knowledgeBaseRanking?: { name: string; count: number }[] }
export interface SystemStatus { llmMode: string; embeddingMode: string; vectorStore: string; redisEnabled: boolean }
export interface OperationLog { id: number; userId?: number; username?: string; operation?: string; method?: string; requestUri?: string; success?: number; ip?: string; createdAt?: string }
export interface Result<T> { code: number; message: string; data: T; timestamp: number }

export interface RecentSession { id: number; knowledgeBaseId: number; knowledgeBaseName: string; title: string; updatedAt?: string }
export interface RecentDocument { id: number; knowledgeBaseId: number; knowledgeBaseName: string; fileName: string; status: string; createdAt?: string }
export interface RecentActivity { sessions: RecentSession[]; documents: RecentDocument[]; failedDocuments: RecentDocument[]; dueReviewCount: number }
export interface KnowledgeGap { question: string; askCount: number; userCount: number; lastAskedAt: string }
export interface KnowledgeInsights { days: number; answeredCount: number; insufficientCount: number; helpfulCount: number; notHelpfulCount: number; openIssueCount: number; resolvedIssueCount: number; gaps: KnowledgeGap[]; hiddenGapCount: number }
