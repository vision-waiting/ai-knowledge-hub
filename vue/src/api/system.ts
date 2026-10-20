import { http } from './http'
import type { SystemStatus } from '@/types'
export const systemStatus = () => http.get('/system/status') as Promise<SystemStatus>
export interface ChatModelOption { id: string; label: string; model: string; configured: boolean }
export interface ChatModels { defaultProvider: string; options: ChatModelOption[] }
export const chatModels = () => http.get('/system/chat-models') as Promise<ChatModels>
