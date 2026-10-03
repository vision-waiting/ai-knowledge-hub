import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/notifications'
import Notifications from './Notifications.vue'
import { vi } from 'vitest'

vi.mock('@/api/notifications', () => ({ listNotifications: vi.fn(), markNotificationRead: vi.fn(), markAllNotificationsRead: vi.fn(), notifyReadStateChanged: vi.fn() }))
const item = { id: 1, knowledgeBaseId: 10, knowledgeBaseName: '课程资料', documentId: 20, documentName: '讲义.txt', type: 'SUCCESS' as const, message: '讲义.txt：已完成处理', read: false, createdAt: '2026-09-11T12:00:00' }
const options = { global: { directives: { loading: {} }, stubs: { AppLayout: { template: '<div><slot /></div>' }, RouterLink: { template: '<a><slot /></a>' }, 'el-button': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' }, 'el-icon': true, 'el-alert': { props: ['title'], template: '<div role="alert">{{ title }}</div>' }, 'el-switch': true, 'el-empty': { props: ['description'], template: '<div>{{ description }}<slot /></div>' }, 'el-pagination': true } } }
beforeEach(() => { vi.clearAllMocks(); setActivePinia(createPinia()); useAuthStore().token = 'token'; vi.mocked(api.listNotifications).mockResolvedValue({ items: [item], total: 1, unreadCount: 1 }) })

it('marks a private notification read and refreshes the unread badge', async () => {
  vi.mocked(api.markNotificationRead).mockResolvedValue()
  const wrapper = mount(Notifications, options)
  await flushPromises()
  expect(wrapper.text()).toContain('讲义.txt：已完成处理')
  vi.mocked(api.listNotifications).mockResolvedValue({ items: [{ ...item, read: true }], total: 1, unreadCount: 0 })
  await wrapper.findAll('button').find(button => button.text() === '标为已读')!.trigger('click'); await flushPromises()
  expect(api.markNotificationRead).toHaveBeenCalledWith(1)
  expect(wrapper.text()).not.toContain('标为已读')
  expect(api.notifyReadStateChanged).toHaveBeenCalled()
  wrapper.unmount()
})

it('shows a failed read operation without falsely marking the item as read', async () => {
  vi.mocked(api.markNotificationRead).mockRejectedValue(new Error('offline'))
  const wrapper = mount(Notifications, options)
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '标为已读')!.trigger('click'); await flushPromises()
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  expect(wrapper.text()).toContain('标为已读')
  wrapper.unmount()
})

it('marks all reminders read with one action', async () => {
  vi.mocked(api.markAllNotificationsRead).mockResolvedValue()
  const wrapper = mount(Notifications, options)
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '全部已读')!.trigger('click'); await flushPromises()
  expect(api.markAllNotificationsRead).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
