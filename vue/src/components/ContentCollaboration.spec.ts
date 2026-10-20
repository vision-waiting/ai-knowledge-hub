import { mount, flushPromises } from '@vue/test-utils'
import ContentCollaboration from './ContentCollaboration.vue'
import { http } from '@/api/http'
vi.mock('@/api/http', () => ({ http: { get: vi.fn(), post: vi.fn() } }))
const entry = { id: 8, kind: 'FAQ', title: '原题', body: '原文', sortOrder: 0, updatedAt: '', version: 2 }
beforeEach(() => { vi.clearAllMocks(); vi.mocked(http.get).mockResolvedValue({ items: [], hasMore: false } as never) })
it('submits a reader suggestion against the displayed version without publishing it', async () => {
  const w = mount(ContentCollaboration, { props: { knowledgeBaseId: 3, canManage: false, userId: 7, mode: 'suggest', entry } })
  await w.get('[aria-label="建议标题"]').setValue('新题')
  await w.get('[aria-label="建议正文"]').setValue('新文')
  await w.get('[aria-label="修改原因"]').setValue('纠错')
  await w.get('form').trigger('submit'); await flushPromises()
  expect(http.post).toHaveBeenCalledWith('/knowledge-bases/3/suggestions', { contentId: 8, baseVersion: 2, title: '新题', body: '新文', reason: '纠错' })
  expect(w.text()).toContain('已提交，等待维护者审核')
  expect(w.text()).not.toContain('合入修改'); w.unmount()
})
it('shows both versions and preserves a conflict message without removing the suggestion', async () => {
  vi.mocked(http.get).mockResolvedValue({ items: [{ id: 4, authorId: 7, authorName: 'reader', originalTitle: '原题', originalBody: '原文', title: '新题', body: '新文', reason: '纠错', status: 'OPEN', baseVersion: 2 }], hasMore: false } as never)
  vi.mocked(http.post).mockRejectedValueOnce(new Error('原内容已有新版本'))
  const w = mount(ContentCollaboration, { props: { knowledgeBaseId: 3, canManage: true, userId: 1, mode: 'review' } })
  await flushPromises()
  expect(w.text()).toContain('原文'); expect(w.text()).toContain('新文')
  await w.get('[data-test="merge-4"]').trigger('click'); await flushPromises()
  expect(w.get('[role="alert"]').text()).toContain('原内容已有新版本')
  expect(w.find('[data-test="merge-4"]').exists()).toBe(true); w.unmount()
})
it('does not offer merge or restoration controls to readers', async () => {
  vi.mocked(http.get).mockResolvedValue([{ version: 1, content: entry, action: 'CREATED' }] as never)
  const w = mount(ContentCollaboration, { props: { knowledgeBaseId: 3, canManage: false, userId: 7, mode: 'history', entry } })
  await flushPromises(); expect(w.text()).toContain('版本 1'); expect(w.text()).not.toContain('恢复此版本'); w.unmount()
})
it('clears history loading when switching to a suggestion while the request is pending', async () => {
  let resolve!: (value: never) => void
  vi.mocked(http.get).mockReturnValueOnce(new Promise(r => { resolve = r }))
  const w = mount(ContentCollaboration, { props: { knowledgeBaseId: 3, canManage: false, mode: 'history', entry } })
  await w.setProps({ mode: 'suggest' })
  resolve([] as never); await flushPromises()
  expect(w.text()).not.toContain('正在加载')
  w.unmount()
})
