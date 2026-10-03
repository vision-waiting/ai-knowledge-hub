import { http } from './http'
import type { DashboardOverview, KnowledgeInsights, RecentActivity } from '@/types'
export const overview = () => http.get('/dashboard/overview') as Promise<DashboardOverview>
export const trends = () => http.get('/dashboard/trends')

export const recentActivity = () => http.get('/dashboard/recent') as Promise<RecentActivity>
export const knowledgeInsights = (knowledgeBaseId: number, days = 30) => http.get(`/knowledge-bases/${knowledgeBaseId}/insights`, { params: { days } }) as Promise<KnowledgeInsights>
