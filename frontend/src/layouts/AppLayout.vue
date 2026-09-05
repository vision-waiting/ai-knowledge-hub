<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const route = useRoute(); const router = useRouter(); const auth = useAuthStore()
const nav = [{ path: '/dashboard', label: '工作台', icon: '⌂' }, { path: '/knowledge', label: '知识库', icon: '▣' }, { path: '/chat/history', label: '聊天记录', icon: '☷' }]
function isActive(path: string) { return route.path === path || route.path.startsWith(path + '/') }
</script>
<template>
  <div class="app-shell"><aside class="sidebar"><div class="brand"><span>AI</span><div>Knowledge Hub<small>智能知识中心</small></div></div><nav><router-link v-for="item in nav" :key="item.path" :to="item.path" :class="{ active: isActive(item.path) }"><b>{{ item.icon }}</b>{{ item.label }}</router-link><template v-if="auth.isAdmin"><div class="nav-caption">管理</div><router-link to="/admin/users" :class="{ active: isActive('/admin') }"><b>⚙</b>管理后台</router-link></template></nav><div class="sidebar-footer"><div class="user-mini"><span>{{ (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1) }}</span><div>{{ auth.user?.nickname || auth.user?.username }}<small>{{ auth.user?.role === 'ADMIN' ? '管理员' : '普通用户' }}</small></div></div><button @click="auth.logout().then(() => router.push('/login'))">退出登录</button></div></aside><main class="main-area"><header class="topbar"><div><span class="breadcrumb">AI Knowledge Hub</span><span class="crumb-separator">/</span><strong>{{ route.meta.title || '工作台' }}</strong></div><div class="top-actions"><span class="mode-badge">● 本地演示模式</span><router-link class="quick-link" to="/knowledge">＋ 快速提问</router-link></div></header><section class="page-body"><slot /></section></main></div>
</template>

