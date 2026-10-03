<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Collection, DataAnalysis, Document, Fold, House, Setting, SwitchButton, Reading, Notebook, ArrowDown, Close } from '@element-plus/icons-vue'
import NotificationBell from '@/components/NotificationBell.vue'
import { useAuthStore } from '@/stores/auth'
import { systemStatus } from '@/api/system'
import type { SystemStatus } from '@/types'

defineProps<{ contextName?: string; contextId?: number }>()
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const mobileOpen = ref(false), sidebar = ref<HTMLElement>(), menuButton = ref<HTMLButtonElement>()
const system = ref<SystemStatus>()
const groups = [
  { id: 'common', label: '常用', items: [
    { path: '/dashboard', label: '工作台', icon: House }, { path: '/knowledge', label: '知识库', icon: Collection },
    { path: '/search', label: '全文搜索', icon: Document }, { path: '/chat/history', label: '聊天记录', icon: ChatDotRound },
  ] },
  { id: 'learning', label: '学习与整理', items: [
    { path: '/analysis', label: '总结与对比', icon: Document }, { path: '/study', label: '学习助手', icon: Reading },
    { path: '/review-plan', label: '复习计划', icon: Reading }, { path: '/notes', label: '个人笔记', icon: Notebook },
  ] },
  { id: 'content', label: '内容维护', items: [
    { path: '/workspace', label: '知识内容与维护', icon: Collection }, { path: '/web-sources', label: '网页资料', icon: Document },
    { path: '/retrieval', label: '检索评测', icon: DataAnalysis },
  ] },
  { id: 'admin', label: '管理', items: [
    { path: '/admin/users', label: '用户管理', icon: Setting }, { path: '/admin/logs', label: '操作日志', icon: Document },
    { path: '/admin/statistics', label: '统计分析', icon: DataAnalysis },
  ] },
]
const visibleGroups = computed(() => groups.filter(group => group.id !== 'admin' || auth.isAdmin))
const expanded = ref<Record<string, boolean>>({ common: true, learning: true, content: true, admin: true })
const knowledgePage = computed(() => /^\/knowledge\/\d+(\/|$)/.test(route.path))
const subpages: Record<string, string> = { documents: '文档', chat: '智能问答', analysis: '总结与对比', study: '学习助手', retrieval: '检索实验室', insights: '知识缺口' }
const titles: Record<string, string> = { '/profile': '个人中心', '/notifications': '更新提醒' }
const title = computed(() => knowledgePage.value ? subpages[route.path.split('/')[3] || ''] || '概览' : groups.flatMap(group => group.items).find(item => active(item.path))?.label || titles[route.path] || '知识中心')
const mode = computed(() => !system.value ? '服务状态未知' : system.value.llmMode === 'LOCAL_DEMO' ? '原文检索' : 'AI 问答')
const modeDetail = computed(() => !system.value ? '暂时无法获取服务状态' : system.value.llmMode === 'LOCAL_DEMO' ? '当前根据资料原文整理回答' : system.value.embeddingMode === 'LOCAL_DEMO' ? 'AI 问答 · 本地向量检索' : 'AI 问答 · 向量检索')
function active(path: string) { return route.path === path || route.path.startsWith(path + '/') }
async function closeNavigation() {
  if (!mobileOpen.value) return
  mobileOpen.value = false
  await nextTick()
  menuButton.value?.focus()
}
async function openNavigation() {
  mobileOpen.value = true
  await nextTick()
  sidebar.value?.querySelector<HTMLButtonElement>('button[aria-label="关闭导航"]')?.focus()
}
function navigationKey(event: KeyboardEvent) {
  if (!mobileOpen.value) return
  if (event.key === 'Escape') { event.preventDefault(); void closeNavigation(); return }
  if (event.key !== 'Tab') return
  const controls = Array.from(sidebar.value?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])') || []).filter(el => !el.closest('[hidden]'))
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
function resize() { if (window.innerWidth >= 768 && mobileOpen.value) void closeNavigation() }
async function logout() { try { await auth.logout() } finally { await router.push('/login') } }
watch(() => route.path, () => {
  for (const group of groups) if (group.items.some(item => active(item.path))) expanded.value[group.id] = true
  void closeNavigation()
}, { immediate: true })
onMounted(async () => {
  window.addEventListener('resize', resize)
  try { system.value = await systemStatus() } catch { system.value = undefined }
})
onBeforeUnmount(() => window.removeEventListener('resize', resize))
</script>

<template>
  <div class="app-shell">
    <div v-if="mobileOpen" class="nav-backdrop" @click="closeNavigation" />
    <aside id="primary-navigation" ref="sidebar" :class="['sidebar', { 'mobile-open': mobileOpen }]" :role="mobileOpen ? 'dialog' : undefined" :aria-modal="mobileOpen ? true : undefined" aria-label="主导航面板" @keydown="navigationKey">
      <div class="brand-row"><router-link class="brand" to="/dashboard"><span>智</span><div>智能知识中心<small>知识管理 · 智能问答</small></div></router-link><button v-if="mobileOpen" class="nav-close" aria-label="关闭导航" @click="closeNavigation"><el-icon><Close /></el-icon></button></div>
      <nav aria-label="主导航">
        <section v-for="group in visibleGroups" :key="group.id" class="nav-group">
          <div v-if="group.id === 'common'" class="nav-caption">{{ group.label }}</div>
          <button v-else class="nav-group-toggle" :aria-expanded="expanded[group.id]" :aria-controls="`nav-${group.id}`" @click="expanded[group.id] = !expanded[group.id]">{{ group.label }}<el-icon :class="{ collapsed: !expanded[group.id] }"><ArrowDown /></el-icon></button>
          <div :id="`nav-${group.id}`" :hidden="!expanded[group.id]" class="nav-items">
            <router-link v-for="item in group.items" :key="item.path" :to="item.path" :class="{ active: active(item.path) }" :aria-current="active(item.path) ? 'page' : undefined" @click="closeNavigation"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</router-link>
          </div>
        </section>
      </nav>
      <div class="sidebar-note">整理资料，让知识随时可用。</div>
    </aside>
    <main class="main-area" :inert="mobileOpen || undefined">
      <header class="topbar">
        <div class="topbar-title">
          <button ref="menuButton" class="mobile-menu" aria-label="打开导航" aria-controls="primary-navigation" :aria-expanded="mobileOpen" @click="openNavigation"><el-icon><Fold /></el-icon></button>
          <template v-if="knowledgePage"><router-link class="breadcrumb" to="/knowledge">知识库</router-link><span class="crumb-separator">/</span><router-link class="breadcrumb context-name" :to="`/knowledge/${contextId || route.params.id}`" :title="contextName">{{ contextName || '当前知识库' }}</router-link><span class="crumb-separator">/</span></template>
          <span v-else class="breadcrumb">知识中心</span><span v-if="!knowledgePage" class="crumb-separator">/</span><strong>{{ title }}</strong>
        </div>
        <div class="top-actions">
          <span class="service-status" :title="modeDetail"><span aria-hidden="true">●</span> {{ mode }}</span>
          <NotificationBell />
          <router-link to="/profile" class="topbar-account" aria-label="个人中心"><span class="account-avatar">{{ (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1) }}</span><span class="account-info"><strong>{{ auth.user?.nickname || auth.user?.username }}</strong><small>{{ auth.isAdmin ? '管理员' : '普通用户' }}</small></span></router-link>
          <el-button text :icon="SwitchButton" title="退出登录" aria-label="退出登录" @click="logout" />
        </div>
      </header>
      <section class="page-body"><slot /></section>
    </main>
  </div>
</template>
