import { http } from './http'
import type { SearchResult } from '@/types'
export interface SearchFilters { fileName?: string; fileType?: string; tag?: string; offset?: number; limit?: number }
export const searchKnowledge = (knowledgeBaseId: number, q: string, filters: SearchFilters = {}) =>
  http.get('/search', { params: { knowledgeBaseId, q, ...filters } }) as Promise<SearchResult[]>
