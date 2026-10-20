import { mount, flushPromises } from '@vue/test-utils'
import BookCover from './BookCover.vue'

it('opens on a key press and emits only once', async () => {
  const wrapper = mount(BookCover)
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }))
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'b' }))
  await flushPromises()
  expect(wrapper.emitted('opened')).toHaveLength(1)
  wrapper.unmount()
})
it('opens by clicking the cover', async () => {
  const wrapper = mount(BookCover)
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.emitted('opened')).toHaveLength(1)
  wrapper.unmount()
})
it('removes its global keyboard handler when unmounted', async () => {
  const wrapper = mount(BookCover)
  wrapper.unmount()
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
  await flushPromises()
  expect(wrapper.emitted('opened')).toBeUndefined()
})
