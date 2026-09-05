import { mount } from '@vue/test-utils'
import ChatMessage from './ChatMessage.vue'

it('renders assistant markdown and mode label', () => {
  const wrapper = mount(ChatMessage, { props: { message: { role: 'ASSISTANT', content: '**结论**\n\n`HashMap`', mode: 'LOCAL_DEMO' } } })
  expect(wrapper.find('.markdown-body strong').text()).toBe('结论')
  expect(wrapper.text()).toContain('本地演示')
})

