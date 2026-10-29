import { mount } from '@vue/test-utils'
import ReadingControls from './ReadingControls.vue'
import { readingPreferences, updateReadingPreferences } from '@/utils/readingPreferences'

beforeEach(() => updateReadingPreferences({ turnSpeed: 'relaxed', focusMode: false }))

it('sets the turn speed and provides an accessible way to enter and leave focus mode', async () => {
  const wrapper = mount(ReadingControls)
  await wrapper.get('select').setValue('quick')
  expect(readingPreferences.turnSpeed).toBe('quick')
  await wrapper.get('button').trigger('click')
  expect(readingPreferences.focusMode).toBe(true)
  expect(wrapper.get('button').attributes('aria-pressed')).toBe('true')
  expect(wrapper.get('button').text()).toContain('退出专注')
  await wrapper.get('button').trigger('click')
  expect(readingPreferences.focusMode).toBe(false)
  wrapper.unmount()
})
