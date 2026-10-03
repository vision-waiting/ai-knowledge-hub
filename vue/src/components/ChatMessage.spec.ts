import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { feedbackMessage } from '@/api/chat'
import ChatMessage from './ChatMessage.vue'
import SourceDrawer from './SourceDrawer.vue'
vi.mock('@/api/chat', () => ({ feedbackMessage: vi.fn() }))
it('copies the actual answer and only confirms after clipboard success', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  const wrapper = mount(ChatMessage, { props: { message: { role: 'ASSISTANT', content: '资料结论 [1]' } }, global: { plugins: [ElementPlus], stubs: { SaveNoteButton: true, SourceDrawer: true } } })
  const copy = wrapper.findAll('button').find(b => b.text() === '复制回答')
  expect(copy).toBeDefined()
  await copy!.trigger('click'); await flushPromises()
  expect(writeText).toHaveBeenCalledWith('资料结论 [1]')
  expect(wrapper.text()).toContain('已复制')
  wrapper.unmount()
})

it('opens the exact citation and clears it when the message changes', async () => {
  const citation = { index: 2, documentId: 9, documentName: '接口说明', chunkId: 33, excerpt: '原文', pageNumber: 4 }
  const wrapper = mount(ChatMessage, { props: { message: { role: 'ASSISTANT', content: '回答 [2]', citations: [citation] } }, global: { plugins: [ElementPlus], stubs: { SourceDrawer: true, SaveNoteButton: true } } })
  const source = wrapper.findAll('button').find(button => button.text().includes('接口说明'))
  expect(source).toBeDefined()
  await source!.trigger('click')
  expect(wrapper.getComponent(SourceDrawer).props()).toMatchObject({ documentId: 9, citation })
  await wrapper.setProps({ message: { role: 'ASSISTANT', content: '另一个回答' } })
  expect(wrapper.getComponent(SourceDrawer).props('documentId')).toBeUndefined()
  wrapper.unmount()
})

it('renders assistant markdown and mode label', () => {
  const wrapper = mount(ChatMessage, { props: { message: { role: 'ASSISTANT', content: '**结论**\n\n`HashMap`', mode: 'LOCAL_DEMO' } }, global: { stubs: { SaveNoteButton: true, SourceDrawer: true } } })
  expect(wrapper.find('.markdown-body strong').text()).toBe('结论')
  expect(wrapper.text()).toContain('原文检索')
  expect(wrapper.text()).not.toMatch(/示例|演示/)
})

it('asks for a reason and discloses sharing before submitting negative feedback', async () => {
  vi.mocked(feedbackMessage).mockResolvedValue(undefined as never)
  const wrapper = mount(ChatMessage, { props: { message: { id: 7, role: 'ASSISTANT', content: 'answer' } }, global: { plugins: [ElementPlus], stubs: { SaveNoteButton: true } } })
  await wrapper.get('[data-test="negative-feedback"]').trigger('click')
  expect(feedbackMessage).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('知识库维护者')
  await wrapper.get('textarea[aria-label="改进原因"]').setValue('资料过时')
  await wrapper.get('[data-test="submit-feedback"]').trigger('click'); await flushPromises()
  expect(feedbackMessage).toHaveBeenCalledWith(7, 'NOT_HELPFUL', '资料过时')
  expect(wrapper.text()).toContain('已提交')
  wrapper.unmount()
})


it('opens a citation from the inline marker in the answer body', async () => {
  const citation = { index: 1, documentId: 5, documentName: '部署手册', chunkId: 8, excerpt: '原文' }
  const wrapper = mount(ChatMessage, { props: { message: { role: 'ASSISTANT', content: '需要先配置密钥 [1]。', citations: [citation] } }, global: { plugins: [ElementPlus], stubs: { SourceDrawer: true, SaveNoteButton: true } } })
  await wrapper.get('.markdown-body [data-cite="1"]').trigger('click')
  expect(wrapper.getComponent(SourceDrawer).props()).toMatchObject({ documentId: 5, citation })
  wrapper.unmount()
})
