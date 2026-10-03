import { http } from './http'
import type { SystemStatus } from '@/types'
export const systemStatus = () => http.get('/system/status') as Promise<SystemStatus>
