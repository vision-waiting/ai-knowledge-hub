import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { enterWorkspace } from '@/utils/enterWorkspace'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeList.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id', name: 'knowledge-detail', component: () => import('@/views/KnowledgeDetail.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/documents', name: 'documents', component: () => import('@/views/DocumentList.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/chat', name: 'chat', component: () => import('@/views/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/chat/history', name: 'chat-history', component: () => import('@/views/ChatHistory.vue'), meta: { requiresAuth: true } },
  { path: '/analysis', name: 'analysis', component: () => import('@/views/DocumentAnalysis.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/analysis', name: 'knowledge-analysis', component: () => import('@/views/DocumentAnalysis.vue'), meta: { requiresAuth: true } },
  { path: '/study', name: 'study', component: () => import('@/views/StudyAssistant.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/study', name: 'knowledge-study', component: () => import('@/views/StudyAssistant.vue'), meta: { requiresAuth: true } },
  { path: '/notes', name: 'notes', component: () => import('@/views/Notes.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', name: 'notifications', component: () => import('@/views/Notifications.vue'), meta: { requiresAuth: true } },
  { path: '/search', name: 'search', component: () => import('@/views/Search.vue'), meta: { requiresAuth: true } },
  { path: '/workspace', name: 'workspace', component: () => import('@/views/KnowledgeWorkspace.vue'), meta: { requiresAuth: true } },
  { path: '/retrieval', name: 'retrieval', component: () => import('@/views/RetrievalLab.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/retrieval', name: 'knowledge-retrieval', component: () => import('@/views/RetrievalLab.vue'), meta: { requiresAuth: true } },
  { path: '/knowledge/:id/insights', name: 'knowledge-insights', component: () => import('@/views/KnowledgeInsights.vue'), meta: { requiresAuth: true } },
  { path: '/web-sources', name: 'web-sources', component: () => import('@/views/WebSources.vue'), meta: { requiresAuth: true } },
  { path: '/review-plan', name: 'review-plan', component: () => import('@/views/ReviewPlan.vue'), meta: { requiresAuth: true } },
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
  if ((to.name === 'login' || to.name === 'register') && store.token) return { name: 'dashboard' }
  return true
})
let finishPageTurn: ((failure?: unknown) => void) | undefined
router.beforeResolve((to, from) => {
  if (!to.meta.requiresAuth || !from.meta.requiresAuth || to.path === from.path || document.documentElement.classList.contains('entering-workspace')) return
  const navigation = new Promise<void>((resolve, reject) => { finishPageTurn = failure => failure ? reject(failure) : resolve() })
  void enterWorkspace(() => navigation, true).catch(() => { /* Router reports navigation errors; the book overlay always cleans up. */ })
})
router.afterEach((_to, _from, failure) => { finishPageTurn?.(failure); finishPageTurn = undefined })
router.onError(error => { finishPageTurn?.(error); finishPageTurn = undefined })
export default router

