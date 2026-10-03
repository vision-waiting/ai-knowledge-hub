import { mount } from '@vue/test-utils'
import FileUpload from './FileUpload.vue'
import { uploadDocument } from '@/api/documents'
import { vi } from 'vitest'
import type { DocumentItem } from '@/types'
vi.mock('@/api/documents', () => ({ uploadDocument: vi.fn() }))

it('renders upload status and accepted formats', () => {
  const wrapper = mount(FileUpload, { props: { status: 'WAITING' }, global: { stubs: { 'el-icon': true } } })
  expect(wrapper.text()).toContain('PDF')
  expect(wrapper.text()).toContain('等待处理')
})
it('blocks duplicate uploads while the first request is pending', async () => {
  let finish!: (value: DocumentItem) => void
  vi.mocked(uploadDocument).mockReturnValue(new Promise(resolve => { finish = resolve }) as ReturnType<typeof uploadDocument>)
  const wrapper = mount(FileUpload, { props: { knowledgeBaseId: 1 }, global: { stubs: { 'el-icon': true } } })
  const input = wrapper.get('input')
  Object.defineProperty(input.element, 'files', { value: [new File(['hello'], 'test.txt', { type: 'text/plain' })] })
  await input.trigger('change')
  await input.trigger('change')
  expect(uploadDocument).toHaveBeenCalledTimes(1)
  expect((input.element as HTMLInputElement).disabled).toBe(true)
  finish({ id: 1, status: 'WAITING', knowledgeBaseId: 1, fileName: 'test.txt', fileType: 'txt', fileSize: 5 })
  await new Promise(resolve => setTimeout(resolve, 0))
  expect(wrapper.emitted('uploaded')?.length).toBe(1)
  expect((input.element as HTMLInputElement).disabled).toBe(false)
})
