import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import Login from './Login.vue'
import { login, register } from '@/api/auth'
vi.mock('@/api/auth', () => ({ register: vi.fn(), login: vi.fn() }))

async function registrationForm() {
  localStorage.clear()
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/register', name: 'register', component: Login },
    { path: '/login', name: 'login', component: Login },
    { path: '/dashboard', component: { template: '<div />' } },
  ] })
  await router.push('/register'); await router.isReady()
  const wrapper = mount(Login, { global: { plugins: [createPinia(), router], stubs: { 'el-icon': true } } })
  await wrapper.get('input[autocomplete="username"]').setValue('new_user')
  await wrapper.get('input[name="password"]').setValue('password123')
  return { wrapper, router }
}

it('blocks mismatched confirmation before contacting the server', async () => {
  vi.mocked(register).mockClear()
  const { wrapper } = await registrationForm()
  await wrapper.get('input[name="confirmPassword"]').setValue('different123')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('两次输入的密码不一致')
  expect(register).not.toHaveBeenCalled()
})

it('keeps registration errors visible and lets the user correct the form', async () => {
  vi.mocked(register).mockRejectedValueOnce({ response: { data: { message: '用户名已存在' } } })
  const { wrapper } = await registrationForm()
  await wrapper.get('input[name="confirmPassword"]').setValue('password123')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('用户名已存在')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
})

it('registers and enters the workspace with the returned session', async () => {
  vi.mocked(register).mockResolvedValueOnce({ token: 'registration-token', user: { id: 9, username: 'new_user', nickname: 'new_user', role: 'USER', status: 1 } })
  const { wrapper, router } = await registrationForm()
  await wrapper.get('input[name="confirmPassword"]').setValue('password123')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(register).toHaveBeenCalledWith({ username: 'new_user', password: 'password123', confirmPassword: 'password123', nickname: '' })
  expect(localStorage.getItem('ai_kh_token')).toBe('registration-token')
  expect(router.currentRoute.value.path).toBe('/dashboard')
  localStorage.clear()
})

it('opens a real sign-in form without publishing or prefilling credentials', () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }, { path: '/register', name: 'register', component: Login }] })
  const wrapper = mount(Login, { global: { plugins: [createPinia(), router], stubs: { 'el-icon': true } } })
  expect(wrapper.find<HTMLInputElement>('input[autocomplete="username"]').element.value).toBe('')
  expect(wrapper.find<HTMLInputElement>('input[type="password"]').element.value).toBe('')
  expect(wrapper.text()).not.toMatch(/示例|演示|123456/)
})

it('prevents duplicate sign-in requests and preserves the requested destination', async () => {
  const { wrapper, router } = await registrationForm()
  await router.push('/login?redirect=/dashboard?from=login')
  await wrapper.get('input[name="password"]').setValue('password123')
  let resolve!: (value: Awaited<ReturnType<typeof login>>) => void
  vi.mocked(login).mockReset().mockImplementationOnce(() => new Promise(done => { resolve = done }))
  await wrapper.get('form').trigger('submit')
  await wrapper.get('form').trigger('submit')
  expect(login).toHaveBeenCalledOnce()
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[role="status"]').text()).toContain('正在验证')
  resolve({ token: 'login-token', user: { id: 9, username: 'new_user', nickname: '', role: 'USER', status: 1 } })
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/dashboard?from=login')
  localStorage.clear()
})

it('keeps failed sign-in on the form and allows retrying', async () => {
  const { wrapper, router } = await registrationForm()
  await router.push('/login')
  await wrapper.get('input[name="password"]').setValue('password123')
  vi.mocked(login).mockRejectedValueOnce(new Error('用户名或密码错误'))
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/login')
  expect(wrapper.get('[role="alert"]').text()).toContain('用户名或密码错误')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  expect(document.documentElement.classList.contains('entering-workspace')).toBe(false)
})
