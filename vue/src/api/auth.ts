import { http } from './http'
import type { User } from '@/types'
export interface RegisterPayload { username: string; nickname?: string; password: string; confirmPassword: string }
export const register = (payload: RegisterPayload) => http.post('/auth/register', payload) as Promise<{ token: string; user: User }>
export const login = (payload: { username: string; password: string }) => http.post('/auth/login', payload) as Promise<{ token: string; user: User }>
export const me = () => http.get('/auth/me') as Promise<User>
export const logout = () => http.post('/auth/logout')
export const updateProfile = (payload: { nickname: string }) => http.put('/auth/profile', payload) as Promise<User>
export const changePassword = (payload: { currentPassword: string; newPassword: string }) => http.put('/auth/password', payload)
