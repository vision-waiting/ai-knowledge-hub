import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeList.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id', name: 'knowledge-detail', component: () => import('@/views/KnowledgeDetail.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/documents', name: 'documents', component: () => import('@/views/DocumentList.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/chat', name: 'chat', component: () => import('@/views/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/chat/history', name: 'chat-history', component: () => import('@/views/ChatHistory.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/admin/users', name: 'admin-users', component: () => import('@/views/AdminUsers.vue'), meta: { requiresAuth: true, admin: true } },
  { path: '/admin/logs', name: 'admin-logs', component: () => import('@/views/AdminLogs.vue'), meta: { requiresAuth: true, admin: true } },
  { path: '/admin/statistics', name: 'admin-statistics', component: () => import('@/views/AdminStatistics.vue'), meta: { requiresAuth: true, admin: true } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async to => {
  const store = useAuthStore()
  if (to.meta.requiresAuth && !store.token) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.admin && !store.isAdmin && store.token) { try { await store.loadMe() } catch { return { name: 'login' } } }
  if (to.meta.admin && !store.isAdmin) return { name: 'dashboard' }
  if (to.name === 'login' && store.token) return { name: 'dashboard' }
  return true
})
export default router

