import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import SaveNoteButton from './SaveNoteButton.vue'
import { createNote } from '@/api/notes'
vi.mock('@/api/notes', () => ({ createNote: vi.fn() }))
const note = { id: 7, title: 'Answer', content: '**answer**', category: 'Java', createdAt: '', updatedAt: '' }
async function setup() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/notes', component: { template: '<div />' } }] })
  await router.push('/notes'); await router.isReady()
  return mount(SaveNoteButton, { props: { content: '**answer**', title: 'Answer', sourceSessionId: 11 }, global: { plugins: [ElementPlus, router], stubs: { teleport: true } } })
}
beforeEach(() => vi.clearAllMocks())
it('saves the selected answer with editable title and category and links to the note', async () => {
  vi.mocked(createNote).mockResolvedValue(note)
  const wrapper = await setup()
  await wrapper.get('[data-test="open-save-note"]').trigger('click'); await flushPromises()
  await wrapper.get('input[aria-label="笔记分类"]').setValue('Java')
  await wrapper.get('[data-test="confirm-save-note"]').trigger('click'); await flushPromises()
  expect(createNote).toHaveBeenCalledWith({ title: 'Answer', category: 'Java', content: '**answer**', sourceSessionId: 11 })
  expect(wrapper.get('a').attributes('href')).toBe('/notes?note=7')
  wrapper.unmount()
})
it('keeps the dialog and edited title open after a save failure', async () => {
  vi.mocked(createNote).mockRejectedValue(new Error('Offline'))
  const wrapper = await setup()
  await wrapper.get('[data-test="open-save-note"]').trigger('click'); await flushPromises()
  await wrapper.get('input[aria-label="笔记标题"]').setValue('My draft')
  await wrapper.get('[data-test="confirm-save-note"]').trigger('click'); await flushPromises()
  expect((wrapper.get('input[aria-label="笔记标题"]').element as HTMLInputElement).value).toBe('My draft')
  expect(wrapper.text()).toContain('Offline')
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})
