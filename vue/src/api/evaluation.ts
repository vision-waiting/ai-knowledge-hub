import { http } from './http'
export interface EvaluationCase { question: string; expectedDocumentIds: number[] }
export interface DebugChunk { chunkId: number; documentId: number; documentName: string; pageNumber?: number; chunkIndex?: number; content: string; vectorScore: number; keywordScore: number; hybridScore: number }
export interface DebugResult { question: string; topK: number; sufficient: boolean; latencyMs: number; chunks: DebugChunk[] }
export interface CaseResult { question: string; expectedDocumentIds: number[]; retrievedDocumentIds: number[]; sufficient: boolean; latencyMs: number; hitAtK: number | null; reciprocalRank: number | null; sourceRecall: number | null; correctRejection: boolean | null }
export interface EvaluationRun { topK: number; caseCount: number; answerableCount: number; unanswerableCount: number; hitAtK: number | null; mrr: number | null; sourceRecall: number | null; unanswerableRejectionRate: number | null; latencyMs: number; cases: CaseResult[]; id?: number; datasetHash?: string; createdAt?: string }
export type RunSummary = Omit<EvaluationRun, 'cases'> & { id: number; datasetHash: string; createdAt: string }
export const debugRetrieval = (id: number, payload: { question: string; topK: number }) => http.post(`/knowledge-bases/${id}/retrieval/debug`, payload, { timeout: 120000 }) as Promise<DebugResult>
export const evaluateRetrieval = (id: number, payload: { cases: EvaluationCase[]; topK: number }) => http.post(`/knowledge-bases/${id}/retrieval/evaluate`, payload, { timeout: 300000 }) as Promise<EvaluationRun>
export const listEvaluationRuns = (id: number) => http.get(`/knowledge-bases/${id}/retrieval/runs`) as Promise<RunSummary[]>
