import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/knowledge'
import KnowledgeList from './KnowledgeList.vue'
import KnowledgeCard from '@/components/KnowledgeCard.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn(), createKnowledgeBase: vi.fn(), updateKnowledgeBase: vi.fn(), deleteKnowledgeBase: vi.fn() }))
beforeEach(() => {
  localStorage.clear(); setActivePinia(createPinia())
  useAuthStore().user = { id: 7, username: 'owner', role: 'USER' }
  vi.mocked(api.listKnowledgeBases).mockResolvedValue([
    { id: 1, name: '个人资料', ownerId: 7, shared: false, documentCount: 0 },
    { id: 2, name: '共享资料', ownerId: 8, ownerUsername: 'publisher', shared: true, documentCount: 0 }
  ])
})
function render() { return mount(KnowledgeList, { global: { plugins: [ElementPlus], stubs: { AppLayout: { template: '<div><slot /></div>' } } } }) }
it('separates personal and shared libraries and displays publishers', async () => {
  const wrapper = render(); await flushPromises()
  expect(wrapper.findAllComponents(KnowledgeCard).map(c => c.props('item').id)).toEqual([1])
  await wrapper.get('#tab-shared').trigger('click'); await flushPromises()
  expect(wrapper.findAllComponents(KnowledgeCard).map(c => c.props('item').id)).toEqual([2])
  expect(wrapper.text()).toContain('发布者：publisher')
  wrapper.unmount()
})
it('lets users choose sharing when creating and forces sharing for admins', async () => {
  for (const role of ['USER', 'ADMIN'] as const) {
    useAuthStore().user!.role = role
    const wrapper = render(); await flushPromises()
    await wrapper.get('.page-heading button').trigger('click'); await flushPromises()
    const toggle = wrapper.findComponent({ name: 'ElSwitch' })
    expect(toggle.props('modelValue')).toBe(role === 'ADMIN')
    expect(toggle.props('disabled')).toBe(role === 'ADMIN')
    wrapper.unmount()
  }
})
it('locks sharing when an admin edits another administrators library', async () => {
  useAuthStore().user!.role = 'ADMIN'
  const wrapper = render(); await flushPromises()
  const card = wrapper.findComponent(KnowledgeCard)
  card.vm.$emit('edit', { id: 9, name: '管理员发布', ownerId: 9, shared: true, sharingLocked: true, documentCount: 0 })
  await flushPromises()
  expect(wrapper.findComponent({ name: 'ElSwitch' }).props('disabled')).toBe(true)
  wrapper.unmount()
})
