import { reactive, readonly, ref } from 'vue'

export type TurnSpeed = 'relaxed' | 'quick' | 'off'
interface ReadingPreferences { turnSpeed: TurnSpeed; focusMode: boolean }
const storageKey = 'ai_kh_reading_preferences'
const defaults: ReadingPreferences = { turnSpeed: 'relaxed', focusMode: false }
function load(): ReadingPreferences {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null')
    return {
      turnSpeed: ['relaxed', 'quick', 'off'].includes(saved?.turnSpeed) ? saved.turnSpeed : defaults.turnSpeed,
      focusMode: saved?.focusMode === true,
    }
  } catch { return { ...defaults } }
}
const state = reactive(load())
const saved = ref(true)
export const readingPreferences = readonly(state)
export const preferencesSaved = readonly(saved)
export function updateReadingPreferences(changes: Partial<ReadingPreferences>) {
  Object.assign(state, changes)
  try { localStorage.setItem(storageKey, JSON.stringify(state)); saved.value = true }
  catch { saved.value = false }
}
export function getPageTurnDuration() {
  return { relaxed: 1500, quick: 600, off: 0 }[state.turnSpeed]
}
