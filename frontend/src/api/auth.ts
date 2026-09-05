import { http } from './http'
import type { User } from '@/types'
export const login = (payload: { username: string; password: string }) => http.post('/auth/login', payload) as Promise<{ token: string; user: User }>
export const me = () => http.get('/auth/me') as Promise<User>
export const logout = () => http.post('/auth/logout')

