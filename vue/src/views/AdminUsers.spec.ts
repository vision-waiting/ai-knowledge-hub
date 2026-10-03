import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/admin'
import AdminUsers from './AdminUsers.vue'

vi.mock('@/api/admin', () => ({ users: vi.fn(), getUser: vi.fn(), updateUser: vi.fn(), createUser: vi.fn(), updateRole: vi.fn(), updateStatus: vi.fn() }))
const user = { id: 8, username: 'student', nickname: '学生', role: 'USER' as const, status: 1, createdAt: '2026-09-01T12:00:00' }
const body = () => new DOMWrapper(document.body)
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear(); setActivePinia(createPinia())
  useAuthStore().user = { id: 1, username: 'admin', role: 'ADMIN' }
  vi.mocked(api.users).mockResolvedValue([user]); vi.mocked(api.getUser).mockResolvedValue(user)
})
async function setup() {
  const wrapper = mount(AdminUsers, { attachTo: document.body, global: { plugins: [ElementPlus], stubs: { AppLayout: { template: '<main><slot /></main>' } } } })
  await flushPromises(); return wrapper
}
it('loads and displays user details without a password field', async () => {
  const wrapper = await setup()
  try {
    await wrapper.get('[data-test="view-user-8"]').trigger('click'); await flushPromises()
    expect(api.getUser).toHaveBeenCalledWith(8)
    expect(body().text()).toContain('用户详情')
    expect(body().text()).toContain('创建时间')
    expect(body().find('input[type="password"]').exists()).toBe(false)
  } finally { wrapper.unmount() }
})
it('edits profile fields and keeps the draft when saving fails', async () => {
  vi.mocked(api.updateUser).mockRejectedValueOnce(new Error('保存失败')).mockResolvedValue({ ...user, nickname: '新昵称' })
  const wrapper = await setup()
  try {
    await wrapper.get('[data-test="edit-user-8"]').trigger('click'); await flushPromises()
    await body().get('input[aria-label="昵称"]').setValue('新昵称')
    await body().get('[data-test="save-user"]').trigger('click'); await flushPromises()
    expect(api.updateUser).toHaveBeenCalledWith(8, { username: 'student', nickname: '新昵称', role: 'USER', status: 1 })
    expect((body().get('input[aria-label="昵称"]').element as HTMLInputElement).value).toBe('新昵称')
    await body().get('[data-test="save-user"]').trigger('click'); await flushPromises()
    expect(api.users).toHaveBeenCalledTimes(2)
  } finally { wrapper.unmount() }
})

it('ignores an older user request after another editor has opened', async () => {
  const other = { ...user, id: 9, username: 'other' }
  vi.mocked(api.users).mockResolvedValue([user, other])
  let finish!: (value: typeof user) => void
  vi.mocked(api.getUser).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValueOnce(other)
  const wrapper = await setup()
  try {
    await wrapper.get('[data-test="edit-user-8"]').trigger('click')
    await wrapper.get('[data-test="edit-user-9"]').trigger('click'); await flushPromises()
    await body().get('input[aria-label="昵称"]').setValue('Keep this draft')
    finish(user); await flushPromises()
    expect((body().get('input[aria-label="昵称"]').element as HTMLInputElement).value).toBe('Keep this draft')
    await body().get('[data-test="save-user"]').trigger('click'); await flushPromises()
    expect(api.updateUser).toHaveBeenCalledWith(9, expect.objectContaining({ username: 'other', nickname: 'Keep this draft' }))
  } finally { wrapper.unmount() }
})

it('offers password replacement without allowing promotion in the editor', async () => {
  vi.mocked(api.updateUser).mockResolvedValue(user)
  const wrapper = await setup()
  try {
    await wrapper.get('[data-test="edit-user-8"]').trigger('click'); await flushPromises()
    expect(body().find('input[type="radio"][value="ADMIN"]').exists()).toBe(false)
    await body().get('input[aria-label="新密码"]').setValue('newPassword123')
    await body().get('[data-test="save-user"]').trigger('click'); await flushPromises()
    expect(api.updateUser).toHaveBeenCalledWith(8, expect.objectContaining({ password: 'newPassword123', role: 'USER' }))
  } finally { wrapper.unmount() }
})
