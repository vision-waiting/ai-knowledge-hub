import { mount } from '@vue/test-utils'
import { vi } from 'vitest'
import Dashboard from './Dashboard.vue'

vi.mock('@/api/dashboard', () => ({ overview: vi.fn().mockResolvedValue({ knowledgeBaseCount: 2, documentCount: 4, chatCount: 8, aiCallCount: 8, failedDocumentCount: 0 }) }))

it('shows dashboard value proposition and quick start entry', async () => {
  const wrapper = mount(Dashboard, { global: { stubs: { AppLayout: { template: '<div><slot /></div>' }, StatCard: true }, mocks: { $router: { push: vi.fn() } } } })
  await new Promise(resolve => setTimeout(resolve, 0))
  expect(wrapper.text()).toContain('让回答有据可查')
  expect(wrapper.text()).toContain('开始一次 AI 问答')
})

