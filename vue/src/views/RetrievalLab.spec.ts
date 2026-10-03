import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import RetrievalLab from './RetrievalLab.vue'
import { debugRetrieval, evaluateRetrieval, listEvaluationRuns } from '@/api/evaluation'
import { listKnowledgeBases } from '@/api/knowledge'
import { listDocuments } from '@/api/documents'
import { useAuthStore } from '@/stores/auth'
vi.mock('@/api/evaluation', () => ({ debugRetrieval: vi.fn(), evaluateRetrieval: vi.fn(), listEvaluationRuns: vi.fn() }))
vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn() }))
vi.mock('@/api/documents', () => ({ listDocuments: vi.fn() }))
beforeEach(() => {
  vi.clearAllMocks(); setActivePinia(createPinia())
  useAuthStore().user = { id: 1, username: 'owner', role: 'USER' }
  vi.mocked(listKnowledgeBases).mockResolvedValue([{ id: 7, name: 'Owned', ownerId: 1, documentCount: 1 }, { id: 8, name: 'Shared', ownerId: 2, documentCount: 0 }])
  vi.mocked(listDocuments).mockResolvedValue([{ id: 4, knowledgeBaseId: 7, fileName: 'source.txt', fileType: 'txt', fileSize: 1, status: 'SUCCESS' }])
  vi.mocked(listEvaluationRuns).mockResolvedValue([])
})
async function setup() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/retrieval', component: RetrievalLab }] })
  await router.push('/retrieval'); await router.isReady()
  const wrapper = mount(RetrievalLab, { global: { plugins: [router], stubs: { AppLayout: { template: '<main><slot /></main>' } } } })
  await flushPromises(); return wrapper
}
it('lets a manager debug selected chunks and compare two runs of the same batch', async () => {
  vi.mocked(debugRetrieval).mockResolvedValue({ topK: 3, question: 'question', sufficient: true, latencyMs: 12, chunks: [{ chunkId: 2, documentId: 4, documentName: 'source.txt', content: 'source evidence', vectorScore: .8, keywordScore: .6, hybridScore: .74 }] } as never)
  const run = { topK: 3, caseCount: 1, answerableCount: 1, unanswerableCount: 0, hitAtK: 1, mrr: .5, sourceRecall: 1, unanswerableRejectionRate: null, latencyMs: 12, cases: [] }
  vi.mocked(evaluateRetrieval).mockResolvedValueOnce(run as never).mockResolvedValueOnce({ ...run, topK: 5 } as never)
  const wrapper = await setup()
  expect(wrapper.findAll('select[aria-label="知识库"] option').map(x => x.text())).not.toContain('Shared')
  await wrapper.get('textarea[aria-label="调试问题"]').setValue('question')
  await wrapper.get('[data-test="debug"]').trigger('click'); await flushPromises()
  expect(debugRetrieval).toHaveBeenCalledWith(7, { question: 'question', topK: 3 })
  expect(wrapper.text()).toContain('source evidence')
  await wrapper.get('textarea[aria-label="评测集 JSON"]').setValue('[{"question":"question","expectedDocumentIds":[4]}]')
  await wrapper.get('[data-test="evaluate"]').trigger('click'); await flushPromises()
  await wrapper.get('input[aria-label="Top K"]').setValue(5)
  await wrapper.get('[data-test="evaluate"]').trigger('click'); await flushPromises()
  expect(evaluateRetrieval).toHaveBeenLastCalledWith(7, { topK: 5, cases: [{ question: 'question', expectedDocumentIds: [4] }] })
  expect(wrapper.findAll('[data-test="run-row"]')).toHaveLength(2)
  wrapper.unmount()
})
it('shows invalid JSON and backend errors without erasing the input', async () => {
  const wrapper = await setup()
  await wrapper.get('textarea[aria-label="评测集 JSON"]').setValue('invalid')
  await wrapper.get('[data-test="evaluate"]').trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('JSON'); expect(evaluateRetrieval).not.toHaveBeenCalled()
  vi.mocked(debugRetrieval).mockRejectedValue(new Error('Embedding unavailable'))
  await wrapper.get('textarea[aria-label="调试问题"]').setValue('keep me')
  await wrapper.get('[data-test="debug"]').trigger('click'); await flushPromises()
  expect(wrapper.text()).toContain('Embedding unavailable')
  expect((wrapper.get('textarea[aria-label="调试问题"]').element as HTMLTextAreaElement).value).toBe('keep me')
  wrapper.unmount()
})
