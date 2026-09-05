import { mount } from '@vue/test-utils'
import FileUpload from './FileUpload.vue'

it('renders upload status and accepted formats', () => {
  const wrapper = mount(FileUpload, { props: { status: 'WAITING' } })
  expect(wrapper.text()).toContain('PDF')
  expect(wrapper.text()).toContain('等待处理')
})

