import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('ai_kh_token') || '')
  const storedUser = localStorage.getItem('ai_kh_user')
  const user = ref<User | null>(storedUser ? JSON.parse(storedUser) : null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  function setSession(nextToken: string, nextUser: User) { token.value = nextToken; user.value = nextUser; localStorage.setItem('ai_kh_token', nextToken); localStorage.setItem('ai_kh_user', JSON.stringify(nextUser)) }
  async function login(username: string, password: string) { const result = await authApi.login({ username, password }); setSession(result.token, result.user); return result }
  async function loadMe() { if (token.value) user.value = await authApi.me(); if (user.value) localStorage.setItem('ai_kh_user', JSON.stringify(user.value)) }
  async function logout() { try { await authApi.logout() } finally { token.value = ''; user.value = null; localStorage.removeItem('ai_kh_token'); localStorage.removeItem('ai_kh_user') } }
  return { token, user, isAdmin, setSession, login, loadMe, logout }
})

