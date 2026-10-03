import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import DocumentAnalysis from './DocumentAnalysis.vue'
import { createAnalysis, listAnalyses } from '@/api/intelligence'

vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn().mockResolvedValue([{id: 3, name: '资料库', ownerId: 1, documentCount: 2}]) }))
vi.mock('@/api/documents', () => ({ listDocuments: vi.fn().mockResolvedValue([{id: 1, fileName: '方案一.md', status: 'SUCCESS', fileType: 'md'}, {id: 2, fileName: '方案二.md', status: 'SUCCESS', fileType: 'md'}]) }))
vi.mock('@/api/intelligence', () => ({ createAnalysis: vi.fn(), listAnalyses: vi.fn() }))
async function setup() {
  const router = createRouter({history: createMemoryHistory(), routes:[{path:'/analysis', component:DocumentAnalysis}]})
  await router.push('/analysis'); await router.isReady()
  const wrapper = mount(DocumentAnalysis, {global:{plugins:[ElementPlus,router],stubs:{AppLayout:{template:'<main><slot /></main>'},SaveNoteButton:{props:['content'],template:'<button>{{ content ? "保存为笔记" : "" }}</button>'}}}})
  await flushPromises(); return wrapper
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(listAnalyses).mockResolvedValue([]) })
it('requires two documents for comparison and preserves selection on generation failure', async () => {
  vi.mocked(createAnalysis).mockRejectedValue(new Error('模型暂时不可用'))
  const wrapper = await setup()
  await wrapper.findAll('.tool-tabs button')[1].trigger('click')
  await wrapper.findAll('input[type=checkbox]')[0].setValue(true)
  expect(wrapper.find('.tool-primary').attributes('disabled')).toBeDefined()
  await wrapper.findAll('input[type=checkbox]')[1].setValue(true)
  await wrapper.find('.tool-primary').trigger('click'); await flushPromises()
  expect(createAnalysis).toHaveBeenCalledWith(3,{type:'COMPARE',documentIds:[1,2],focus:''})
  expect(wrapper.text()).toContain('模型暂时不可用')
  expect(wrapper.findAll<HTMLInputElement>('input[type=checkbox]').every(item => item.element.checked)).toBe(true)
  expect(wrapper.find('.tool-primary').attributes('disabled')).toBeUndefined()
  wrapper.unmount()
})
it('shows and saves a generated result even if an earlier history response finishes later', async () => {
  let finishHistory!: (value: never[]) => void
  vi.mocked(listAnalyses).mockReturnValue(new Promise(resolve => { finishHistory = resolve }))
  vi.mocked(createAnalysis).mockResolvedValue({ id: 9, knowledgeBaseId: 3, type:'SUMMARY', title:'新总结', content:'**关键结论**', mode:'OPENAI_COMPATIBLE', createdAt:'2026-09-12T12:00:00', documentIds:[1], sourceNotice:'使用所选文档', sources:[] })
  const wrapper = await setup()
  await wrapper.findAll('input[type=checkbox]')[0].setValue(true)
  await wrapper.find('.tool-primary').trigger('click'); await flushPromises()
  finishHistory([]); await flushPromises()
  expect(wrapper.find('.markdown-body strong').text()).toBe('关键结论')
  expect(wrapper.find('.tool-history').text()).toContain('新总结')
  expect(wrapper.text()).toContain('保存为笔记')
  wrapper.unmount()
})
