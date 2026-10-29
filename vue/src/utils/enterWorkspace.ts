import { nextTick } from 'vue'
import { chooseStoryTransition, type StoryTransition } from './storyTransition'

let activeInterrupt: (() => void) | undefined
export function cancelWorkspaceTransition() { activeInterrupt?.() }

// A moving diagonal crease starts at the upper-right corner. Reflecting the
// lifted polygon across it keeps the reverse attached to the front of the page.
function curlFrames(width: number, height: number, top = 0) {
  const w = Math.max(width, 1), h = Math.max(height, 1), slope = .62
  type Point = [number, number]
  const rectangle: Point[] = [[0, 0], [w, 0], [w, h], [0, h]]
  const front: Keyframe[] = [], reverse: Keyframe[] = []
  const polygon = (points: Point[], shift = false) => {
    const padded = points.length ? [...points] : [[0, h] as Point]
    while (padded.length < 5) padded.push(padded[padded.length - 1]!)
    return `polygon(${padded.map(([x, y]) => `${x + (shift ? w : 0)}px ${y + (shift ? h : top)}px`).join(',')})`
  }
  for (let frame = 0; frame <= 40; frame++) {
    const progress = frame / 40, distance = progress * (w + slope * h)
    const side = ([x, y]: Point) => w - x + slope * y - distance
    const cut = (lifted: boolean) => {
      const result: Point[] = []
      rectangle.forEach((a, index) => {
        const b = rectangle[(index + 1) % 4]!, sa = side(a), sb = side(b)
        if (lifted ? sa <= 0 : sa >= 0) result.push(a)
        if (sa * sb < 0) {
          const t = sa / (sa - sb)
          result.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t])
        }
      })
      return result
    }
    const reflected = cut(true).map(([x, y]): Point => {
      const delta = (w - x + slope * y - distance) / (1 + slope * slope)
      return [x + 2 * delta, y - 2 * slope * delta]
    })
    front.push({ clipPath: polygon(cut(false)), offset: progress })
    const crease = (w + slope * h + distance) / Math.sqrt(1 + slope * slope)
    reverse.push({ clipPath: polygon(reflected, true), offset: progress,
      backgroundImage: `linear-gradient(238.2deg, #b7baa1 ${crease - 2}px, #d5d4bd ${crease + 8}px, #fffdf1 ${crease + 30}px, #f5f0dd ${crease + 90}px)`,
      opacity: progress > .82 ? (1 - progress) / .18 : 1 })
  }
  return { front, reverse }
}

/** Preserve a noninteractive paper copy while the real route loads underneath it. */
export async function enterWorkspace(navigate: () => Promise<unknown>, workspace = false, plan: StoryTransition = chooseStoryTransition('', '')) {
  const root = document.documentElement
  const app = document.querySelector<HTMLElement>('#app')
  const wasInert = app?.hasAttribute('inert')
  const motionPreference = window.matchMedia?.('(prefers-reduced-motion: reduce)')
  const reduced = motionPreference?.matches || plan.duration <= 0
  const source = document.querySelector<HTMLElement>(workspace ? '.app-shell' : '.login-page')
  const animations: Animation[] = []
  let overlay: HTMLElement | undefined
  let interrupted = false
  const interrupt = () => { interrupted = true; animations.forEach(animation => animation.cancel()) }
  activeInterrupt = interrupt
  const skip = (event: KeyboardEvent) => { if (event.key === 'Escape') interrupt() }
  const motionChanged = () => { if (motionPreference?.matches) interrupt() }
  const play = (target: HTMLElement | null, frames: Keyframe[], options: KeyframeAnimationOptions) => {
    try {
      const animation = target?.animate?.(frames, options)
      if (animation) { animations.push(animation); return animation.finished.catch(() => {}) }
    } catch { /* Animation support must never block navigation. */ }
    return Promise.resolve()
  }
  root.classList.add('entering-workspace')
  try {
    if (!reduced && source) {
      const bounds = source.getBoundingClientRect()
      const paper = source.cloneNode(true) as HTMLElement
      const originals = source.querySelectorAll<HTMLElement>('*')
      const copies = paper.querySelectorAll<HTMLElement>('*')
      const backgroundColor = getComputedStyle(source).backgroundColor
      const animated = source.getAnimations
        ? new Set(source.getAnimations({ subtree: true }).map(animation => (animation.effect as KeyframeEffect | null)?.target)) : undefined
      const scrollPositions: Array<{ copy: HTMLElement; top: number; left: number }> = []
      // Read the live tree while layout is clean. Nothing is mounted or mutated
      // until all styles and scroll offsets have been captured.
      const rootScroll = { top: source.scrollTop, left: source.scrollLeft }
      originals.forEach((element, index) => {
        const top = element.scrollTop, left = element.scrollLeft
        if (top || left) scrollPositions.push({ copy: copies[index]!, top, left })
        if (workspace && element.matches('.knowledge-grid > .knowledge-card')) {
          const rect = element.getBoundingClientRect()
          // Keep grid row heights, but don't lay out or paint invisible card
          // contents in the short-lived transition replica.
          if (rect.height > 0 && (rect.bottom < -64 || rect.top > innerHeight + 64)) {
            const copy = copies[index]!
            copy.replaceChildren()
            Object.assign(copy.style, { height: `${rect.height}px`, minHeight: `${rect.height}px`, boxSizing: 'border-box' })
          }
        }
        if (!animated || animated.has(element)) {
          const style = getComputedStyle(element)
          if (animated || (style.animationName && style.animationName !== 'none')) {
            Object.assign(copies[index]!.style, { transform: style.transform, opacity: style.opacity, filter: style.filter })
          }
        }
      })
      const originalBook = source.querySelector<HTMLElement>('.login-book')
      const copiedBook = paper.querySelector<HTMLElement>('.login-book')
      if (originalBook && copiedBook && bounds.width) {
        const bookBounds = originalBook.getBoundingClientRect()
        Object.assign(copiedBook.style, { position: 'absolute', left: `${bookBounds.left - bounds.left}px`, top: `${bookBounds.top - bounds.top}px`, width: `${bookBounds.width}px`, height: `${bookBounds.height}px`, minHeight: '0' })
      }
      paper.querySelectorAll('[id]').forEach(node => node.removeAttribute('id'))
      // A visual copy must never retain entered passwords, even in detached markup.
      paper.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach(input => {
        input.value = ''
        input.removeAttribute('value')
        input.placeholder = '••••••••'
      })
      Object.assign(paper.style, { position: 'absolute', top: `${bounds.top}px`, left: `${bounds.left}px`, width: `${bounds.width}px`, height: `${bounds.height}px`, minHeight: '0', margin: '0', zIndex: '5' })
      if (workspace) paper.style.backgroundColor = backgroundColor
      overlay = document.createElement('div')
      overlay.className = 'book-transition-overlay book-world'
      if (workspace) { overlay.classList.add('story-transition'); overlay.dataset.storyKind = plan.kind }
      overlay.setAttribute('aria-hidden', 'true')
      overlay.setAttribute('inert', '')
      const backdrop = document.createElement('div')
      backdrop.className = 'book-transition-backdrop'
      backdrop.style.backgroundColor = backgroundColor
      if (workspace) {
        const surface = document.createElement('div')
        surface.className = 'story-transition-surface'
        surface.append(paper)
        overlay.append(backdrop, surface)
      } else overlay.append(backdrop, paper)
      document.body.append(overlay)
      // cloneNode does not copy scroll positions. Restore them only after the
      // replica participates in layout, otherwise the browser clamps them to 0.
      scrollPositions.forEach(({ copy, top, left }) => { copy.scrollTop = top; copy.scrollLeft = left })
      if (rootScroll.top) paper.scrollTop = rootScroll.top
      if (rootScroll.left) paper.scrollLeft = rootScroll.left
      app?.setAttribute('inert', '')
      window.addEventListener('resize', interrupt, { once: true })
      window.addEventListener('keydown', skip)
      motionPreference?.addEventListener?.('change', motionChanged)
    }
    await navigate()
    await nextTick()
    if (overlay && !interrupted) {
      const sheet = overlay.querySelector<HTMLElement>(workspace ? '.main-area' : '.login-panel')
      const backdrop = overlay.querySelector<HTMLElement>('.book-transition-backdrop')
      const book = overlay.querySelector<HTMLElement>(workspace ? '.book-spread' : '.login-book')
      const duration = plan.duration
      let reverse: HTMLElement | null = null
      let frames: ReturnType<typeof curlFrames> | undefined
      if (sheet) {
        const rect = sheet.getBoundingClientRect()
        const visibleTop = Math.max(0, rect.top)
        const visibleHeight = Math.max(1, Math.min(window.innerHeight, rect.bottom) - visibleTop)
        frames = curlFrames(rect.width, visibleHeight, visibleTop - rect.top)
        const shadow = document.createElement('div')
        shadow.className = 'page-curl-shadow'
        reverse = document.createElement('div')
        reverse.className = 'page-curl-reverse'
        Object.assign(shadow.style, { left: `${rect.left}px`, top: `${visibleTop}px`, width: `${rect.width}px`, height: `${visibleHeight}px` })
        Object.assign(reverse.style, { left: `${-rect.width}px`, top: `${-visibleHeight}px`, width: `${rect.width * 3}px`, height: `${visibleHeight * 3}px` })
        shadow.append(reverse)
        overlay.append(shadow)
        sheet.style.willChange = 'clip-path'
      }
      // The arriving illustration should already be settled when the paper reveals it.
      app?.querySelectorAll('.knowledge-orbit').forEach(orbit => {
        orbit.getAnimations?.({ subtree: true }).forEach(animation => {
          if (animation.effect?.getTiming().iterations !== Infinity) animation.finish()
        })
      })
      // Native Web Animations keeps the same page turn on browsers without View Transitions.
      await Promise.all([
        play(sheet, frames?.front || [], { duration, easing: 'cubic-bezier(.42,0,.32,1)', fill: 'both' }),
        play(reverse, frames?.reverse || [], { duration, easing: 'cubic-bezier(.42,0,.32,1)', fill: 'both' }),
        play(backdrop, [{ opacity: 1 }, { opacity: 1, offset: .12 }, { opacity: 0, offset: .8 }, { opacity: 0 }], { duration, easing: 'linear', fill: 'forwards' }),
        play(book, [{ boxShadow: book ? getComputedStyle(book).boxShadow : 'none' }, { boxShadow: 'none' }], { duration: 450, easing: 'ease-out', fill: 'forwards' }),
        play(overlay, [{ opacity: 1 }, { opacity: 1, offset: .9 }, { opacity: 0 }], { duration, easing: 'linear', fill: 'forwards' }),
      ])
    }
  } finally {
    if (activeInterrupt === interrupt) activeInterrupt = undefined
    animations.forEach(animation => animation.cancel())
    overlay?.remove()
    window.removeEventListener('resize', interrupt)
    window.removeEventListener('keydown', skip)
    motionPreference?.removeEventListener?.('change', motionChanged)
    if (!wasInert) app?.removeAttribute('inert')
    root.classList.remove('entering-workspace')
  }
  const heading = document.querySelector<HTMLElement>('.main-area h1') || document.querySelector<HTMLElement>('.main-area')
  if (heading) {
    if (!heading.hasAttribute('tabindex')) {
      heading.setAttribute('tabindex', '-1')
      heading.addEventListener('blur', () => heading.removeAttribute('tabindex'), { once: true })
    }
    heading.focus({ preventScroll: true })
  }
}
