import { http } from './http'

export interface SourceCoverage { documentId: number; fileName: string; includedChunks: number; totalChunks: number; includedCharacters: number; totalCharacters: number }
export interface Analysis { id: number; knowledgeBaseId: number; type: 'SUMMARY' | 'COMPARE'; title: string; content: string; mode: string; createdAt: string; documentIds: number[]; focus?: string; sourceNotice: string; sources: SourceCoverage[] }
export interface StudyCard { front: string; back: string }
export interface StudyQuestion { id: number; prompt: string; options: string[] }
export interface StudySession { id: number; knowledgeBaseId: number; title: string; mode: string; cards: StudyCard[]; questions: StudyQuestion[]; createdAt: string; sourceNotice: string; sources: SourceCoverage[] }
export interface GradedQuestion { questionId: number; prompt: string; options: string[]; selectedIndex: number; correctIndex: number; explanation: string; correct: boolean }
export interface StudyGrade { id?: number; score: number; total: number; results: GradedQuestion[]; createdAt?: string }
export interface StudyMistake extends GradedQuestion { id: number; studyId: number; title: string; resolved: boolean; createdAt: string }

export const createAnalysis = (kbId: number, payload: { type: Analysis['type']; documentIds: number[]; focus?: string }) => http.post(`/knowledge-bases/${kbId}/analysis`, payload, { timeout: 180000 }) as Promise<Analysis>
export const listAnalyses = (kbId: number) => http.get(`/knowledge-bases/${kbId}/analysis`) as Promise<Analysis[]>
export const createStudy = (kbId: number, payload: { documentIds: number[]; questionCount: number }) => http.post(`/knowledge-bases/${kbId}/study`, payload, { timeout: 180000 }) as Promise<StudySession>
export const listStudies = (kbId?: number) => http.get(kbId ? `/knowledge-bases/${kbId}/study` : '/study') as Promise<StudySession[]>
export const getStudy = (id: number) => http.get(`/study/${id}`) as Promise<StudySession>
export const submitStudy = (id: number, answers: number[]) => http.post(`/study/${id}/submit`, { answers }) as Promise<StudyGrade>
export const listAttempts = (id: number) => http.get(`/study/${id}/attempts`) as Promise<StudyGrade[]>
export const listMistakes = (includeResolved = false) => http.get('/study/mistakes', { params: { includeResolved } }) as Promise<StudyMistake[]>
export const resolveMistake = (id: number, resolved: boolean) => http.put(`/study/mistakes/${id}/resolve`, { resolved })
