import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { getUnreadCount } from '@/api/notifications'
import NotificationBell from './NotificationBell.vue'
import { vi } from 'vitest'

vi.mock('@/api/notifications', () => ({ getUnreadCount: vi.fn() }))
const options = { global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, 'el-icon': { template: '<i><slot /></i>' } } } }
beforeEach(() => { vi.useFakeTimers(); vi.clearAllMocks(); setActivePinia(createPinia()); useAuthStore().token = 'owner-token' })
afterEach(() => { vi.useRealTimers(); localStorage.clear() })

it('refreshes modestly and stops polling after unmount', async () => {
  vi.mocked(getUnreadCount).mockResolvedValue({ count: 4 })
  const wrapper = mount(NotificationBell, options)
  await flushPromises()
  expect(wrapper.attributes('aria-label')).toContain('4 条未读')
  await vi.advanceTimersByTimeAsync(60000)
  expect(getUnreadCount).toHaveBeenCalledTimes(2)
  wrapper.unmount()
  await vi.advanceTimersByTimeAsync(120000)
  expect(getUnreadCount).toHaveBeenCalledTimes(2)
})

it('discards another user’s pending result and stops on errors without repeated polling', async () => {
  let finish!: (result: { count: number }) => void
  vi.mocked(getUnreadCount).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const wrapper = mount(NotificationBell, options)
  useAuthStore().token = ''
  await flushPromises()
  finish({ count: 98 }); await flushPromises()
  expect(wrapper.attributes('aria-label')).toContain('0 条未读')
  vi.mocked(getUnreadCount).mockRejectedValue(new Error('offline'))
  useAuthStore().token = 'another-user'
  await flushPromises()
  expect(wrapper.attributes('aria-label')).toContain('暂时无法获取')
  await vi.advanceTimersByTimeAsync(180000)
  expect(getUnreadCount).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
