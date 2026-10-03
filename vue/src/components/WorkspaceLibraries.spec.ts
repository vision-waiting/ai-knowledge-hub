import { mount, flushPromises } from '@vue/test-utils'
import { vi, it, expect } from 'vitest'
import WorkspaceLibraries from './WorkspaceLibraries.vue'
import { listKnowledgeBases } from '@/api/knowledge'
vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn() }))
const options = { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }, 'el-icon': true } } }
it('offers chat and document links for persisted knowledge bases', async () => {
  vi.mocked(listKnowledgeBases).mockResolvedValue([{ id: 42, name: '接入规范', ownerId: 2, documentCount: 3, description: '团队资料' }])
  const wrapper = mount(WorkspaceLibraries, options)
  await flushPromises()
  expect(wrapper.text()).toContain('接入规范')
  expect(wrapper.find('a[href="/knowledge/42/chat"]').exists()).toBe(true)
  expect(wrapper.find('a[href="/knowledge/42/documents"]').exists()).toBe(true)
})
it('shows a recoverable error instead of invented libraries', async () => {
  vi.mocked(listKnowledgeBases).mockRejectedValue(new Error('offline'))
  const wrapper = mount(WorkspaceLibraries, options)
  await flushPromises()
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  expect(wrapper.find('button').text()).toContain('重试')
})
