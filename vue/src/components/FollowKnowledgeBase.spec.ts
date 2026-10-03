import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/notifications'
import FollowKnowledgeBase from './FollowKnowledgeBase.vue'
import { vi } from 'vitest'

vi.mock('@/api/notifications', () => ({ getSubscription: vi.fn(), followKnowledgeBase: vi.fn(), unfollowKnowledgeBase: vi.fn() }))
const options = { props: { knowledgeBaseId: 10 }, global: { stubs: { 'el-button': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' } } } }
beforeEach(() => { vi.clearAllMocks(); setActivePinia(createPinia()); useAuthStore().token = 'token'; vi.mocked(api.getSubscription).mockResolvedValue({ following: false }) })

it('requires explicit follow and can unfollow after it is persisted', async () => {
  vi.mocked(api.followKnowledgeBase).mockResolvedValue({ following: true })
  vi.mocked(api.unfollowKnowledgeBase).mockResolvedValue({ following: false })
  const wrapper = mount(FollowKnowledgeBase, options)
  await flushPromises()
  expect(api.followKnowledgeBase).not.toHaveBeenCalled()
  await wrapper.get('button').trigger('click'); await flushPromises()
  expect(api.followKnowledgeBase).toHaveBeenCalledWith(10)
  expect(wrapper.text()).toContain('已关注更新')
  await wrapper.get('button').trigger('click'); await flushPromises()
  expect(api.unfollowKnowledgeBase).toHaveBeenCalledWith(10)
  expect(wrapper.text()).toContain('关注更新')
  wrapper.unmount()
})

it('ignores stale state when switching knowledge bases', async () => {
  let finish!: (result: { following: boolean }) => void
  vi.mocked(api.getSubscription).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const wrapper = mount(FollowKnowledgeBase, options)
  await wrapper.setProps({ knowledgeBaseId: 11 }); await flushPromises()
  finish({ following: true }); await flushPromises()
  expect(wrapper.text()).not.toContain('已关注更新')
  wrapper.unmount()
})

it('preserves the existing state on failed mutation and reports an inline error', async () => {
  vi.mocked(api.followKnowledgeBase).mockRejectedValue(new Error('服务暂不可用'))
  const wrapper = mount(FollowKnowledgeBase, options)
  await flushPromises(); await wrapper.get('button').trigger('click'); await flushPromises()
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  expect(wrapper.text()).not.toContain('已关注更新')
  expect((wrapper.get('button').element as HTMLButtonElement).disabled).toBe(false)
  wrapper.unmount()
})
