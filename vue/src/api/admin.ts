import { http } from './http'
import type { User, OperationLog, DashboardOverview } from '@/types'
export const users = () => http.get('/admin/users') as Promise<User[]>
export const getUser = (id: number) => http.get(`/admin/users/${id}`) as Promise<User>
export const updateUser = (id: number, payload: { username: string; nickname: string; role: string; status: number; password?: string }) => http.put(`/admin/users/${id}`, payload) as Promise<User>
export const logs = () => http.get('/admin/logs') as Promise<OperationLog[]>
export const statistics = () => http.get('/admin/statistics') as Promise<DashboardOverview>
export const createUser = (payload: { username: string; password: string; nickname: string; role: string }) => http.post('/admin/users', payload)
export const updateRole = (id: number, role: string) => http.put(`/admin/users/${id}/role`, { role })
export const updateStatus = (id: number, status: number) => http.put(`/admin/users/${id}/status`, { status })
