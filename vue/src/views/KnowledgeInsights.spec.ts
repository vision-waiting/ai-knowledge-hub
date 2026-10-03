import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import KnowledgeInsights from './KnowledgeInsights.vue'
import { getKnowledgeBase } from '@/api/knowledge'
import { knowledgeInsights } from '@/api/dashboard'

vi.mock('@/api/knowledge', () => ({ getKnowledgeBase: vi.fn() }))
vi.mock('@/api/dashboard', () => ({ knowledgeInsights: vi.fn() }))

const base = { id: 3, name: '接口资料', ownerId: 1, documentCount: 2, canManage: true }
async function setup() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/knowledge/:id/insights', component: KnowledgeInsights }] })
  await router.push('/knowledge/3/insights')
  const wrapper = mount(KnowledgeInsights, { global: { plugins: [router, ElementPlus], stubs: { AppLayout: { template: '<main><slot /></main>' } } } })
  await flushPromises()
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getKnowledgeBase).mockResolvedValue(base as never)
  vi.mocked(knowledgeInsights).mockResolvedValue({
    days: 30, answeredCount: 20, insufficientCount: 5, helpfulCount: 8, notHelpfulCount: 2,
    openIssueCount: 2, resolvedIssueCount: 1, gaps: [{ question: '如何配置 SSO？', askCount: 3, userCount: 2, lastAskedAt: '2026-10-01T09:00:00' }], hiddenGapCount: 2,
  })
})

it('lists knowledge gaps with counts and hides single-asker questions', async () => {
  const wrapper = await setup()
  expect(knowledgeInsights).toHaveBeenCalledWith(3, 30)
  const text = wrapper.text()
  expect(text).toContain('如何配置 SSO？')
  expect(text).toContain('3 次提问')
  expect(text).toContain('2 名用户')
  expect(text).toContain('隐藏 2 个单次提问')
  expect(text).toContain('20')
  wrapper.unmount()
})

it('reports a healthy knowledge base when nothing was left unanswered', async () => {
  vi.mocked(knowledgeInsights).mockResolvedValue({
    days: 30, answeredCount: 9, insufficientCount: 0, helpfulCount: 4, notHelpfulCount: 0,
    openIssueCount: 0, resolvedIssueCount: 0, gaps: [], hiddenGapCount: 0,
  })
  const wrapper = await setup()
  expect(wrapper.text()).toContain('没有「资料不足」类回答')
  expect(wrapper.find('.gap-item').exists()).toBe(false)
  wrapper.unmount()
})

it('explains why gaps are withheld when every question was asked once', async () => {
  vi.mocked(knowledgeInsights).mockResolvedValue({
    days: 30, answeredCount: 4, insufficientCount: 3, helpfulCount: 1, notHelpfulCount: 0,
    openIssueCount: 0, resolvedIssueCount: 0, gaps: [], hiddenGapCount: 3,
  })
  const wrapper = await setup()
  expect(wrapper.text()).toContain('不符合展示条件')
  wrapper.unmount()
})
