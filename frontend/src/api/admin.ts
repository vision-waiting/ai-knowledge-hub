import { http } from './http'
export const users = () => http.get('/admin/users')
export const logs = () => http.get('/admin/logs')
export const statistics = () => http.get('/admin/statistics')

