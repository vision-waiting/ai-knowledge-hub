import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import KnowledgeCard from './KnowledgeCard.vue'

beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()) })
function render(role: 'ADMIN' | 'USER', ownerId: number) {
  useAuthStore().user = { id: 7, username: 'viewer', role }
  return mount(KnowledgeCard, { props: { item: { id: 1, name: '资料', ownerId, ownerUsername: 'creator', documentCount: 0 } }, global: { stubs: { 'el-button': { template: '<button><slot /></button>' }, 'el-icon': true } } })
}
it('shows the creator and management actions to administrators', () => {
  const wrapper = render('ADMIN', 8)
  expect(wrapper.text()).toContain('创建者：creator')
  expect(wrapper.find('[aria-label="删除知识库"]').exists()).toBe(true)
})
it('lets readers open shared libraries without management actions', async () => {
  const wrapper = render('USER', 8)
  expect(wrapper.find('[aria-label="编辑知识库"]').exists()).toBe(false)
  expect(wrapper.find('[aria-label="删除知识库"]').exists()).toBe(false)
  await wrapper.get('article').trigger('click')
  expect(wrapper.emitted('open')).toEqual([[1]])
})
it('preserves management actions for the owner', () => {
  expect(render('USER', 7).find('[aria-label="编辑知识库"]').exists()).toBe(true)
})
it('shows the publisher to readers of a shared library', async () => {
  const wrapper = render('USER', 8)
  await wrapper.setProps({ item: { ...wrapper.props('item'), shared: true } })
  expect(wrapper.text()).toContain('发布者：creator')
  expect(wrapper.text()).toContain('共享知识库')
  expect(wrapper.find('[aria-label="删除知识库"]').exists()).toBe(false)
})
