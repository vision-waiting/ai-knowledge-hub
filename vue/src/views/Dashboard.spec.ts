import { mount } from '@vue/test-utils'
import { vi } from 'vitest'
import Dashboard from './Dashboard.vue'
import { routerKey } from 'vue-router'
import { flushPromises } from '@vue/test-utils'
import { overview, recentActivity } from '@/api/dashboard'

vi.mock('@/api/dashboard', () => ({ overview: vi.fn().mockResolvedValue({ knowledgeBaseCount: 2, documentCount: 4, chatCount: 8, aiCallCount: 8, failedDocumentCount: 0 }), recentActivity: vi.fn().mockResolvedValue({ sessions: [], documents: [], failedDocuments: [], dueReviewCount: 0 }) }))

it('discloses that displayed statistics are stale when refresh fails', async () => {
  const wrapper = mount(Dashboard, { global: { provide: { [routerKey as symbol]: { push: vi.fn() } }, directives: { loading: {} }, stubs: { WorkspaceLibraries: true, 'el-skeleton': true, 'el-icon': true, RouterLink: true, 'el-button': { template: '<button><slot /></button>' }, 'el-alert': true, AppLayout: { template: '<div><slot /></div>' }, StatisticsView: true } } })
  await flushPromises()
  vi.mocked(overview).mockRejectedValueOnce(new Error('网络不可用'))
  await wrapper.get('[aria-label="刷新统计"]').trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('上次成功加载的数据')
  wrapper.unmount()
})

it('renders actual overview data without a fabricated chart', async () => {
  const wrapper = mount(Dashboard, { global: { provide: { [routerKey as symbol]: { push: vi.fn() } }, directives: { loading: {} }, stubs: { WorkspaceLibraries: true, 'el-skeleton': true, 'el-icon': true, RouterLink: true, 'el-button': true, 'el-alert': true, AppLayout: { template: '<div><slot /></div>' }, StatisticsView: { props: ['overview'], template: '<div>{{ overview?.documentCount }} 份文档</div>' } } } })
  await new Promise(resolve => setTimeout(resolve, 0))
  expect(wrapper.text()).toContain('4 份文档')
  expect(wrapper.find('.fake-chart').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('本地演示数据')
})
