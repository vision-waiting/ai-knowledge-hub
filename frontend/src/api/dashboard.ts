import { http } from './http'
import type { DashboardOverview } from '@/types'
export const overview = () => http.get('/dashboard/overview') as Promise<DashboardOverview>
export const trends = () => http.get('/dashboard/trends')

