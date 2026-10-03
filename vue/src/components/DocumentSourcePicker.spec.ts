import { mount } from '@vue/test-utils'
import DocumentSourcePicker from './DocumentSourcePicker.vue'

const docs = [
  { id: 1, knowledgeBaseId: 3, fileName: '手册.md', fileType: 'md', fileSize: 20, status: 'SUCCESS' },
  { id: 2, knowledgeBaseId: 3, fileName: '资料.md', fileType: 'md', fileSize: 20, status: 'SUCCESS' },
  { id: 3, knowledgeBaseId: 3, fileName: '处理中.md', fileType: 'md', fileSize: 20, status: 'PARSING' },
]
it('does not allow selecting unprocessed documents or exceeding the source limit', async () => {
  const wrapper = mount(DocumentSourcePicker, { props: { knowledgeBases: [{ id: 3, name: '测试库', ownerId: 1, documentCount: 3 }], knowledgeBaseId: 3, documents: docs, modelValue: [1], limit: 1 } })
  const boxes = wrapper.findAll<HTMLInputElement>('input[type=checkbox]')
  expect(boxes[0].element.disabled).toBe(false)
  expect(boxes[1].element.disabled).toBe(true)
  expect(boxes[2].element.disabled).toBe(true)
  await boxes[0].setValue(false)
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([[]])
})
it('allows switching knowledge bases and shows an actionable empty state', async () => {
  const wrapper = mount(DocumentSourcePicker, { props: { knowledgeBases: [{ id: 3, name: '测试库', ownerId: 1, documentCount: 0 }, { id: 4, name: '另一库', ownerId: 1, documentCount: 0 }], knowledgeBaseId: 3, documents: [], modelValue: [] } })
  await wrapper.find('select').setValue('4')
  expect(wrapper.emitted('update:knowledgeBaseId')?.[0]).toEqual([4])
  expect(wrapper.text()).toContain('上传文档')
})
