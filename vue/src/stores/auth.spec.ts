import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { useChatStore } from './chat'
import { vi } from 'vitest'
vi.mock('@/api/auth', () => ({ logout: vi.fn().mockResolvedValue(undefined) }))

describe('auth store', () => {
  it('clears a revoked session without calling logout again', async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.setSession('old-token', { id: 1, username: 'user', role: 'USER' })
    auth.clearSession()
    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('ai_kh_token')).toBeNull()
  })
  it('recovers from malformed persisted user data', () => {
    localStorage.setItem('ai_kh_user', '{broken')
    setActivePinia(createPinia())
    expect(() => useAuthStore()).not.toThrow()
    expect(useAuthStore().user).toBeNull()
  })
  it('clears previous account chat state on logout', async () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    const chat = useChatStore()
    chat.messages.push({ role: 'USER', content: 'private' })
    await auth.logout()
    expect(chat.messages).toEqual([])
  })
  it('persists login token and user', () => {
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.setSession('demo-token', { id: 1, username: 'student', nickname: '学生', role: 'USER' })
    expect(localStorage.getItem('ai_kh_token')).toBe('demo-token')
    expect(store.user?.username).toBe('student')
  })
})
