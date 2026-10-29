import { getPageTurnDuration } from './readingPreferences'

export interface StoryTransition {
  kind: 'paper'
  duration: number
  direction: 1
}

// All sections belong to the same book: a consistent upper-right curl.
export function chooseStoryTransition(_from: string, _to: string): StoryTransition {
  return { kind: 'paper', duration: getPageTurnDuration(), direction: 1 }
}
