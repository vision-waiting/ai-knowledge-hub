import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox, ElSelect } from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import Notes from './Notes.vue'
import { createNote, getNote, listNotes, listNoteCategories, updateNote, deleteNote } from '@/api/notes'
import { listSessions } from '@/api/chat'
vi.mock('@/api/chat', () => ({ listSessions: vi.fn() }))
vi.mock('@/api/notes', () => ({ listNotes: vi.fn(), listNoteCategories: vi.fn(), getNote: vi.fn(), createNote: vi.fn(), updateNote: vi.fn(), deleteNote: vi.fn() }))
const note = { id: 7, title: 'Java note', content: '**safe**\n<script>alert(1)</script>', category: 'Java', createdAt: '2026-09-11T12:00:00', updatedAt: '2026-09-11T12:00:00' }
const body = () => new DOMWrapper(document.body)
async function setup(path = '/notes') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/notes', component: Notes }, { path: '/knowledge/:id/chat', component: { template: '<div>Conversation</div>' } }] })
  await router.push(path); await router.isReady()
  // Real Teleport avoids the ElSelect ref-update cycle caused by a global teleport stub.
  const wrapper = mount({ template: '<router-view />' }, { attachTo: document.body, global: { plugins: [ElementPlus, router], stubs: { AppLayout: { template: '<main><slot /></main>' } } } })
  await flushPromises(); return Object.assign(wrapper, { router })
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(listNotes).mockResolvedValue([note]); vi.mocked(listNoteCategories).mockResolvedValue(['Java']) })
it('does not overwrite a manually started draft when a deep-linked note arrives late', async () => {
  let resolveNote!: (value: typeof note) => void
  vi.mocked(getNote).mockReturnValueOnce(new Promise(resolve => { resolveNote = resolve }))
  const wrapper = await setup('/notes?note=7')
  try {
    expect(getNote).toHaveBeenCalledWith(7)
    await wrapper.get('[data-test="create-note"]').trigger('click'); await flushPromises()
    await body().get('input[aria-label="笔记标题"]').setValue('My new note')
    await body().get('textarea[aria-label="笔记内容"]').setValue('Keep this draft')
    resolveNote(note); await flushPromises()
    expect((body().get('input[aria-label="笔记标题"]').element as HTMLInputElement).value).toBe('My new note')
    expect((body().get('textarea[aria-label="笔记内容"]').element as HTMLTextAreaElement).value).toBe('Keep this draft')
  } finally { wrapper.unmount() }
})
it('filters by the supplied search and safely previews markdown', async () => {
  const wrapper = await setup()
  await wrapper.get('input[aria-label="搜索笔记"]').setValue('Java')
  await wrapper.get('[data-test="search-notes"]').trigger('click'); await flushPromises()
  expect(listNotes).toHaveBeenLastCalledWith({ keyword: 'Java', category: undefined })
  await wrapper.get('[data-test="edit-note-7"]').trigger('click'); await flushPromises()
  expect(body().get('.note-preview strong').text()).toBe('safe')
  expect(body().find('.note-preview script').exists()).toBe(false)
  wrapper.unmount()
})
it('preserves edited content when update fails', async () => {
  vi.mocked(updateNote).mockRejectedValue(new Error('Save offline'))
  const wrapper = await setup()
  await wrapper.get('[data-test="edit-note-7"]').trigger('click'); await flushPromises()
  await body().get('textarea[aria-label="笔记内容"]').setValue('Edited draft')
  await body().get('[data-test="save-note"]').trigger('click'); await flushPromises()
  expect((body().get('textarea[aria-label="笔记内容"]').element as HTMLTextAreaElement).value).toBe('Edited draft')
  expect(body().text()).toContain('Save offline')
  wrapper.unmount()
})
it('requires confirmation before deleting a note', async () => {
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
  const wrapper = await setup()
  await wrapper.get('[data-test="delete-note-7"]').trigger('click'); await flushPromises()
  expect(confirm).toHaveBeenCalled()
  expect(deleteNote).not.toHaveBeenCalled()
  confirm.mockRestore(); wrapper.unmount()
})
it('shows a retryable list failure rather than a misleading empty state', async () => {
  vi.mocked(listNotes).mockRejectedValue(new Error('List offline'))
  const wrapper = await setup()
  expect(wrapper.text()).toContain('List offline')
  expect(wrapper.text()).not.toContain('还没有笔记')
  wrapper.unmount()
})

it('creates a note and refreshes the saved list', async () => {
  vi.mocked(createNote).mockResolvedValue({ ...note, title: 'New title', content: 'New content', category: '未分类' })
  const wrapper = await setup()
  await wrapper.get('[data-test="create-note"]').trigger('click'); await flushPromises()
  await body().get('input[aria-label="笔记标题"]').setValue(' New title ')
  await body().get('textarea[aria-label="笔记内容"]').setValue('New content')
  await body().get('[data-test="save-note"]').trigger('click'); await flushPromises()
  expect(createNote).toHaveBeenCalledWith({ title: 'New title', content: 'New content', category: '未分类', sourceSessionId: null })
  expect(listNotes).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
it('deletes only after confirmation and refreshes the list', async () => {
  // Element Plus declares prompt data intersected with Action, although confirm resolves an Action.
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
  vi.mocked(deleteNote).mockResolvedValue(undefined)
  const wrapper = await setup()
  await wrapper.get('[data-test="delete-note-7"]').trigger('click'); await flushPromises()
  expect(deleteNote).toHaveBeenCalledWith(7)
  expect(listNotes).toHaveBeenCalledTimes(2)
  confirm.mockRestore(); wrapper.unmount()
})
it('opens a deep-linked note and routes to its owned source conversation', async () => {
  vi.mocked(getNote).mockResolvedValue({ ...note, sourceSessionId: 11 })
  vi.mocked(listSessions).mockResolvedValue([{ id: 11, knowledgeBaseId: 3, title: 'Source' }])
  const wrapper = await setup('/notes?note=7')
  expect(getNote).toHaveBeenCalledWith(7)
  await body().get('[data-test="note-source"]').trigger('click'); await flushPromises()
  expect(wrapper.router.currentRoute.value.fullPath).toBe('/knowledge/3/chat?session=11')
  wrapper.unmount()
})
it('retains a draft when navigation is cancelled', async () => {
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
  const wrapper = await setup()
  await wrapper.get('[data-test="edit-note-7"]').trigger('click'); await flushPromises()
  await body().get('textarea[aria-label="笔记内容"]').setValue('Unsaved draft')
  await wrapper.router.push('/knowledge/3/chat'); await flushPromises()
  expect(wrapper.router.currentRoute.value.path).toBe('/notes')
  expect((body().get('textarea[aria-label="笔记内容"]').element as HTMLTextAreaElement).value).toBe('Unsaved draft')
  confirm.mockRestore(); wrapper.unmount()
})
it('filters by category and persists edited note fields', async () => {
  vi.mocked(updateNote).mockResolvedValue({ ...note, title: 'Revised' })
  const wrapper = await setup()
  const select = wrapper.getComponent(ElSelect)
  select.vm.$emit('update:modelValue', 'Java')
  await flushPromises()
  select.vm.$emit('change', 'Java'); await flushPromises()
  expect(listNotes).toHaveBeenLastCalledWith({ keyword: undefined, category: 'Java' })
  await wrapper.get('[data-test="edit-note-7"]').trigger('click'); await flushPromises()
  await body().get('input[aria-label="笔记标题"]').setValue('Revised')
  await body().get('[data-test="save-note"]').trigger('click'); await flushPromises()
  expect(updateNote).toHaveBeenCalledWith(7, { title: 'Revised', content: note.content, category: 'Java', sourceSessionId: null })
  wrapper.unmount()
})
it('keeps the note available when its source conversation was deleted', async () => {
  vi.mocked(getNote).mockResolvedValue({ ...note, sourceSessionId: 11 })
  vi.mocked(listSessions).mockResolvedValue([])
  const wrapper = await setup('/notes?note=7')
  await body().get('[data-test="note-source"]').trigger('click'); await flushPromises()
  expect(body().text()).toContain('来源会话已删除或不可访问')
  expect((body().get('textarea[aria-label="笔记内容"]').element as HTMLTextAreaElement).value).toBe(note.content)
  expect(wrapper.router.currentRoute.value.path).toBe('/notes')
  wrapper.unmount()
})
