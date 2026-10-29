import { chooseStoryTransition } from './storyTransition'
import { updateReadingPreferences } from './readingPreferences'

beforeEach(() => updateReadingPreferences({ turnSpeed: 'relaxed', focusMode: false }))

it.each([
  ['/dashboard', '/knowledge'], ['/knowledge', '/knowledge/42'],
  ['/knowledge/42', '/knowledge'], ['/knowledge', '/search'],
  ['/dashboard', '/analysis'], ['/dashboard', '/study'],
  ['/profile', '/notifications'],
])('uses a readable upper-right page curl for %s → %s', (from, to) => {
  expect(chooseStoryTransition(from, to)).toMatchObject({ kind: 'paper', duration: 1500, direction: 1 })
})

it('uses the saved speed for future navigation plans', () => {
  updateReadingPreferences({ turnSpeed: 'quick' })
  expect(chooseStoryTransition('/dashboard', '/knowledge').duration).toBe(600)
  updateReadingPreferences({ turnSpeed: 'off' })
  expect(chooseStoryTransition('/knowledge', '/notes').duration).toBe(0)
})
