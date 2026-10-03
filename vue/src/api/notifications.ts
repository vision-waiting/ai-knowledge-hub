import { http } from './http'

export interface KnowledgeNotification {
  id: number
  knowledgeBaseId: number
  knowledgeBaseName: string
  documentId: number | null
  documentName: string
  type: 'UPLOADED' | 'REINDEXED' | 'SUCCESS' | 'FAILED' | 'DELETED'
  message: string
  read: boolean
  createdAt: string
}
export interface NotificationPage { items: KnowledgeNotification[]; total: number; unreadCount: number }
export const getSubscription = (id: number) => http.get(`/knowledge-bases/${id}/subscription`) as Promise<{ following: boolean }>
export const followKnowledgeBase = (id: number) => http.put(`/knowledge-bases/${id}/subscription`) as Promise<{ following: boolean }>
export const unfollowKnowledgeBase = (id: number) => http.delete(`/knowledge-bases/${id}/subscription`) as Promise<{ following: boolean }>
export const listNotifications = (params: { unreadOnly?: boolean; page?: number; pageSize?: number } = {}) => http.get('/notifications', { params }) as Promise<NotificationPage>
export const getUnreadCount = () => http.get('/notifications/unread-count') as Promise<{ count: number }>
export const markNotificationRead = (id: number) => http.put(`/notifications/${id}/read`) as Promise<void>
export const markAllNotificationsRead = () => http.put('/notifications/read-all') as Promise<void>
export const notifyReadStateChanged = () => window.dispatchEvent(new Event('knowledge-notifications-changed'))
