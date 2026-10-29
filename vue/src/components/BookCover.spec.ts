import { mount, flushPromises } from '@vue/test-utils'
import BookCover from './BookCover.vue'
import { updateReadingPreferences } from '@/utils/readingPreferences'

beforeEach(() => updateReadingPreferences({ turnSpeed: 'relaxed', focusMode: false }))

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

it.each(['quick', 'off'] as const)('respects the %s animation preference when opening the cover', async turnSpeed => {
  updateReadingPreferences({ turnSpeed })
  const animate = vi.fn().mockReturnValue({ finished: Promise.resolve(), cancel() {} })
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: animate })
  const wrapper = mount(BookCover)
  try {
    await wrapper.get('button').trigger('click'); await flushPromises()
    expect(wrapper.emitted('opened')).toHaveLength(1)
    if (turnSpeed === 'off') expect(animate).not.toHaveBeenCalled()
    else expect(animate.mock.calls.map(([, options]) => options.duration)).toEqual([600, 700, 700])
  } finally {
    wrapper.unmount()
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate
  }
})
