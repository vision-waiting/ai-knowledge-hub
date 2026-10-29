const key = 'ai_kh_reading_preferences'
beforeEach(() => { localStorage.clear(); vi.resetModules() })
afterEach(() => vi.restoreAllMocks())

it('starts with the existing slow curl and the illustrated spread', async () => {
  const { readingPreferences, getPageTurnDuration } = await import('./readingPreferences')
  expect(readingPreferences).toMatchObject({ turnSpeed: 'relaxed', focusMode: false })
  expect(getPageTurnDuration()).toBe(1500)
})

it('remembers both choices after reloading the module', async () => {
  const { updateReadingPreferences, getPageTurnDuration } = await import('./readingPreferences')
  updateReadingPreferences({ turnSpeed: 'quick', focusMode: true })
  expect(getPageTurnDuration()).toBe(600)
  vi.resetModules()
  const reloaded = await import('./readingPreferences')
  expect(reloaded.readingPreferences).toMatchObject({ turnSpeed: 'quick', focusMode: true })
  reloaded.updateReadingPreferences({ turnSpeed: 'off' })
  expect(reloaded.getPageTurnDuration()).toBe(0)
  expect(reloaded.readingPreferences.focusMode).toBe(true)
})

it.each(['{broken', 'null', '{"turnSpeed":"invalid","focusMode":"false"}'])('ignores malformed saved preferences: %s', async saved => {
  localStorage.setItem(key, saved)
  const { readingPreferences } = await import('./readingPreferences')
  expect(readingPreferences).toMatchObject({ turnSpeed: 'relaxed', focusMode: false })
})

it('keeps controls usable and reports that storage is unavailable', async () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage blocked') })
  const { readingPreferences, updateReadingPreferences, preferencesSaved } = await import('./readingPreferences')
  expect(() => updateReadingPreferences({ focusMode: true })).not.toThrow()
  expect(readingPreferences.focusMode).toBe(true)
  expect(preferencesSaved.value).toBe(false)
})
