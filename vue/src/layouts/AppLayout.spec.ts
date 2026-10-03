import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import AppLayout from './AppLayout.vue'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { nickname: '读者' }, isAdmin: false }) }))
vi.mock('@/api/system', () => ({ systemStatus: vi.fn().mockResolvedValue({ llmMode: 'LOCAL_DEMO' }) }))

async function setup(path = '/dashboard') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push(path)
  const wrapper = mount(AppLayout, { props: { contextName: '接口资料' }, attachTo: document.body, global: { plugins: [router, ElementPlus], stubs: { NotificationBell: true } } })
  await flushPromises()
  return { wrapper, router }
}
it('groups navigation with the workspace first and reopens the active group', async () => {
  const { wrapper, router } = await setup()
  expect(wrapper.get('nav a').text()).toBe('工作台')
  const group = wrapper.get('button[aria-controls="nav-learning"]')
  await group.trigger('click')
  expect(group.attributes('aria-expanded')).toBe('false')
  await router.push('/study'); await flushPromises()
  expect(group.attributes('aria-expanded')).toBe('true')
  expect(wrapper.get('a[href="/study"]').attributes('aria-current')).toBe('page')
  wrapper.unmount()
})
it('names the knowledge base and its subpage in breadcrumbs', async () => {
  const { wrapper } = await setup('/knowledge/3/documents')
  expect(wrapper.get('.topbar-title').text()).toContain('接口资料')
  expect(wrapper.get('.topbar-title').text()).toContain('文档')
  wrapper.unmount()
})
it('links a changed tool scope to the matching library', async () => {
  const { wrapper } = await setup('/knowledge/3/analysis')
  await wrapper.setProps({ contextName: '新资料', contextId: 9 } as never)
  const link = wrapper.findAll('a').find(a => a.text() === '新资料')
  expect(link?.attributes('href')).toBe('/knowledge/9')
  wrapper.unmount()
})
it('closes mobile navigation with Escape and restores focus', async () => {
  const { wrapper } = await setup()
  const trigger = wrapper.get<HTMLButtonElement>('[aria-label="打开导航"]')
  trigger.element.focus(); await trigger.trigger('click'); await flushPromises()
  expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
  await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' }); await flushPromises()
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  expect(document.activeElement).toBe(trigger.element)
  wrapper.unmount()
})
