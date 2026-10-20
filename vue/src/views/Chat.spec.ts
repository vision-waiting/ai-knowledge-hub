import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import Chat from './Chat.vue'

const state = vi.hoisted(() => ({ chat: null as any }))
vi.mock('@/stores/chat', () => ({ useChatStore: () => state.chat }))
vi.mock('@/api/knowledge', () => ({ getKnowledgeBase: vi.fn().mockResolvedValue({ id: 1, name: '接口资料' }) }))
vi.mock('@/api/chat', () => ({ listSessions: vi.fn().mockResolvedValue([]), deleteSession: vi.fn() }))
vi.mock('@/api/system', () => ({ chatModels: vi.fn().mockResolvedValue({ defaultProvider: 'local', options: [
  { id: 'local', label: '本地模型', model: 'qwen-local', configured: true },
  { id: 'cloud', label: '云端 API', model: 'cloud-model', configured: true },
] }) }))
vi.mock('@/api/http', () => ({ http: { get: vi.fn().mockResolvedValue({ items: [{ title: '如何申请接口密钥？' }] }) } }))
it('keeps the reader position and offers a jump to the latest message', async () => {
  state.chat = reactive({ messages: [{ role: 'ASSISTANT', content: '开头' }], selectKnowledgeBase: vi.fn(), stop: vi.fn(), clear: vi.fn() })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/knowledge/:id/chat', component: Chat }, { path: '/knowledge/:id', component: { template: '<div />' } }] })
  await router.push('/knowledge/1/chat')
  const wrapper = mount(Chat, { global: { plugins: [router, ElementPlus], stubs: { AppLayout: { template: '<main><slot /></main>' }, ChatMessage: true } } })
  await flushPromises()
  const messages = wrapper.get<HTMLElement>('.messages')
  Object.defineProperties(messages.element, { scrollHeight: { value: 1500 }, clientHeight: { value: 400 } })
  messages.element.scrollTop = 100
  await messages.trigger('scroll')
  state.chat.messages[0].content += '新的内容'; await flushPromises()
  expect(messages.element.scrollTop).toBe(100)
  const jump = wrapper.findAll('button').find(button => button.text().includes('回到最新'))
  expect(jump).toBeDefined()
  await jump!.trigger('click'); await flushPromises()
  expect(messages.element.scrollTop).toBe(1500)
  messages.element.scrollTop = 1100
  await messages.trigger('scroll')
  state.chat.messages[0].citations = [{ index: 1, documentId: 1, chunkId: 1, documentName: '引用', excerpt: '原文' }]
  await flushPromises()
  expect(messages.element.scrollTop).toBe(1500)
  wrapper.unmount()
})

it('suggests questions from the knowledge base FAQ when available', async () => {
  state.chat = reactive({ messages: [], selectKnowledgeBase: vi.fn(), stop: vi.fn(), clear: vi.fn() })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/knowledge/:id/chat', component: Chat }, { path: '/knowledge/:id', component: { template: '<div />' } }] })
  await router.push('/knowledge/1/chat')
  const wrapper = mount(Chat, { global: { plugins: [router, ElementPlus], stubs: { AppLayout: { template: '<main><slot /></main>' }, ChatMessage: true } } })
  await flushPromises()
  expect(wrapper.get('.question-examples').text()).toContain('如何申请接口密钥？')
  expect(wrapper.get<HTMLSelectElement>('#chat-model-provider').element.value).toBe('local')
  expect(wrapper.text()).toContain('qwen-local')
  await wrapper.get('#chat-model-provider').setValue('cloud')
  expect(wrapper.get('#chat-model-description').text()).toContain('相关资料片段发送至云端')
  expect(wrapper.text()).toContain('cloud-model')
  wrapper.unmount()
})
