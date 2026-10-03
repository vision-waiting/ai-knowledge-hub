import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElSelect } from 'element-plus'
import Search from './Search.vue'
import SourceDrawer from '@/components/SourceDrawer.vue'
import { searchKnowledge } from '@/api/search'
import { listKnowledgeBases } from '@/api/knowledge'
import { createMemoryHistory, createRouter } from 'vue-router'
vi.mock('@/api/search', () => ({ searchKnowledge: vi.fn() }))
vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn() }))
const result = { documentId: 5, documentName: 'water.pdf', chunkId: 7, chunkIndex: 2, pageNumber: 3, excerpt: '水 <script>alert(1)</script>', score: 1 }
async function setup(path = '/search') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/search', component: Search }] })
  await router.push(path)
  const wrapper = mount(Search, { global: { plugins: [ElementPlus, router], stubs: { AppLayout: { template: '<main><slot /></main>' }, SourceDrawer: true } } })
  await flushPromises(); return wrapper
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(listKnowledgeBases).mockResolvedValue([{id: 1,name: 'Mine'}, {id:2,name:'Shared'}] as never); vi.mocked(searchKnowledge).mockResolvedValue([result]) })
it('keeps the requested library scope and rejects inaccessible scopes', async () => {
  const wrapper = await setup('/search?knowledgeBaseId=2')
  expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe(2)
  wrapper.unmount()
  const denied = await setup('/search?knowledgeBaseId=99')
  expect(denied.getComponent(ElSelect).props('modelValue')).toBeUndefined()
  expect(denied.text()).toContain('不可访问')
  denied.unmount()
})
it('highlights literal terms safely and opens the matching source chunk', async () => {
  const wrapper = await setup()
  await wrapper.get('input[placeholder="输入关键词"]').setValue('水')
  await wrapper.get('button.el-button').trigger('click'); await flushPromises()
  expect(wrapper.find('mark').text()).toBe('水')
  expect(wrapper.find('script').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('100%')
  await wrapper.get('[data-test="source-7"]').trigger('click')
  expect(wrapper.getComponent(SourceDrawer).props('citation')).toMatchObject({chunkId:7, documentId:5})
  wrapper.unmount()
})
it('discards responses after switching the knowledge base', async () => {
  let resolve!: (value: typeof result[]) => void
  vi.mocked(searchKnowledge).mockReturnValueOnce(new Promise(done => {resolve=done}))
  const wrapper = await setup()
  await wrapper.get('input[placeholder="输入关键词"]').setValue('水')
  await wrapper.get('button.el-button').trigger('click')
  wrapper.getComponent(ElSelect).vm.$emit('update:modelValue', 2); await flushPromises()
  resolve([result]); await flushPromises()
  expect(wrapper.text()).not.toContain('water.pdf')
  wrapper.unmount()
})
it('shows a retryable knowledge base load error', async () => {
  vi.mocked(listKnowledgeBases).mockRejectedValueOnce(new Error('Offline'))
  const wrapper = await setup()
  expect(wrapper.text()).toContain('Offline')
  await wrapper.get('[data-test="retry-bases"]').trigger('click'); await flushPromises()
  expect(listKnowledgeBases).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
it('passes filters and uses bounded pagination with a lookahead result', async () => {
  vi.mocked(searchKnowledge).mockResolvedValueOnce(Array.from({length:21}, (_,i) => ({...result,chunkId:i+10}))).mockResolvedValueOnce([])
  const wrapper = await setup()
  await wrapper.get('input[placeholder="输入关键词"]').setValue('水')
  await wrapper.get('input[aria-label="文件名筛选"]').setValue('water')
  await wrapper.get('input[aria-label="标签筛选"]').setValue('science')
  await wrapper.get('button.el-button').trigger('click'); await flushPromises()
  expect(searchKnowledge).toHaveBeenLastCalledWith(1,'水',{fileName:'water',fileType:undefined,tag:'science',offset:0,limit:21})
  expect(wrapper.findAll('.search-result')).toHaveLength(20)
  await wrapper.findAll('.search-pagination button')[1]!.trigger('click'); await flushPromises()
  expect(searchKnowledge).toHaveBeenLastCalledWith(1,'水',expect.objectContaining({offset:20,limit:21}))
  expect(wrapper.text()).toContain('未找到匹配内容')
  wrapper.unmount()
})
it('retries failed searches without showing an empty success state', async () => {
  vi.mocked(searchKnowledge).mockRejectedValueOnce(new Error('Search offline')).mockResolvedValueOnce([result])
  const wrapper = await setup()
  await wrapper.get('input[placeholder="输入关键词"]').setValue('水')
  await wrapper.get('button.el-button').trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('Search offline')
  expect(wrapper.text()).not.toContain('未找到匹配内容')
  await wrapper.get('[role="alert"] button').trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('water.pdf')
  wrapper.unmount()
})
