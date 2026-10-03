import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import KnowledgeDetail from './KnowledgeDetail.vue'
import { getKnowledgeBase } from '@/api/knowledge'
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 1 }, isAdmin: false }) }))
vi.mock('@/api/knowledge', () => ({ getKnowledgeBase: vi.fn() }))
vi.mock('@/api/documents', () => ({ listDocuments: vi.fn().mockResolvedValue([]) }))
async function setup() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/knowledge/3')
  const wrapper = mount(KnowledgeDetail, { global: { plugins: [router, ElementPlus], stubs: { AppLayout: { template: '<main><slot /></main>' }, FollowKnowledgeBase: true } } })
  await flushPromises(); return wrapper
}
it('does not present a failed request as valid zero statistics', async () => {
  vi.mocked(getKnowledgeBase).mockRejectedValueOnce(new Error('读取失败'))
  const wrapper = await setup()
  expect(wrapper.text()).toContain('读取失败')
  expect(wrapper.find('.metrics-row').exists()).toBe(false)
  expect(wrapper.findAll('button').some(b => b.text().includes('重试'))).toBe(true)
  wrapper.unmount()
})
it('keeps document and search links scoped and explains the read-only empty state', async () => {
  vi.mocked(getKnowledgeBase).mockResolvedValueOnce({ id: 3, name: '共享资料', ownerId: 9, canWrite: false, documentCount: 0 })
  const wrapper = await setup()
  expect(wrapper.find('a[href="/search?knowledgeBaseId=3"]').exists()).toBe(true)
  expect(wrapper.find('a[href="/knowledge/3/documents?status=FAILED"]').exists()).toBe(true)
  expect(wrapper.text()).toContain('暂无可检索资料')
  expect(wrapper.text()).not.toContain('上传资料')
  wrapper.unmount()
})
