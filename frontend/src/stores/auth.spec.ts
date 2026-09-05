import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

describe('auth store', () => {
  it('persists login token and user', () => {
    setActivePinia(createPinia())
    const store = useAuthStore()
    store.setSession('demo-token', { id: 1, username: 'student', nickname: '学生', role: 'USER' })
    expect(localStorage.getItem('ai_kh_token')).toBe('demo-token')
    expect(store.user?.username).toBe('student')
  })
})

