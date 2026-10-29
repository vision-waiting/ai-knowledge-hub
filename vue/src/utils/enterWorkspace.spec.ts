import { enterWorkspace, cancelWorkspaceTransition } from './enterWorkspace'
import { chooseStoryTransition } from './storyTransition'

const page = '<div id="app"><div class="login-page"><div class="login-book"><section class="login-story">介绍</section><section class="login-panel"><div class="login-card"><input type="password" value="secret-password" /></div></section></div></div></div>'
beforeEach(() => { document.body.innerHTML = page; vi.stubGlobal('matchMedia', () => ({ matches: false })) })
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); document.documentElement.className = ''; document.body.innerHTML = '' })

it('skips the book overlay for reduced motion and focuses the destination', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  await enterWorkspace(async () => {
    expect(document.querySelector('.book-transition-overlay')).toBeNull()
    document.querySelector('#app')!.innerHTML = '<main class="main-area"><h1>工作台</h1></main>'
  })
  expect(document.activeElement?.tagName).toBe('H1')
})

it('preserves the book over navigation without copying passwords or accessible duplicate content', async () => {
  await enterWorkspace(async () => {
    const overlay = document.querySelector<HTMLElement>('.book-transition-overlay')!
    expect(overlay).not.toBeNull()
    expect(overlay.getAttribute('aria-hidden')).toBe('true')
    expect(overlay.hasAttribute('inert')).toBe(true)
    expect(overlay.querySelector<HTMLInputElement>('input')!.value).toBe('')
    expect(overlay.innerHTML).not.toContain('secret-password')
    expect(overlay.querySelector('.login-panel')).not.toBeNull()
  })
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
})

it('lifts a separate paper reverse from the upper right and cleans up after finishing', async () => {
  let finish!: () => void
  const finished = new Promise<void>(resolve => { finish = resolve })
  const animate = vi.fn().mockReturnValue({ finished, cancel: vi.fn() })
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: animate })
  const entering = enterWorkspace(async () => {})
  await new Promise(resolve => setTimeout(resolve, 0))
  expect(document.querySelector('.page-curl-reverse')).not.toBeNull()
  expect(animate.mock.calls.some(([frames]) => frames.length > 20 && frames[0].clipPath?.startsWith('polygon('))).toBe(true)
  expect(document.querySelector('.book-transition-overlay')).not.toBeNull()
  finish()
  await entering
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
  delete (HTMLElement.prototype as Partial<HTMLElement>).animate
})

it('restores the login page when navigation fails, without retrying', async () => {
  const navigate = vi.fn().mockRejectedValue(new Error('route failed'))
  await expect(enterWorkspace(navigate)).rejects.toThrow('route failed')
  expect(navigate).toHaveBeenCalledOnce()
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
  expect(document.documentElement.classList.contains('entering-workspace')).toBe(false)
  expect(document.querySelector('.login-panel')).not.toBeNull()
  expect(document.querySelector('#app')?.hasAttribute('inert')).toBe(false)
})

it('navigates without animation support', async () => {
  const navigate = vi.fn().mockResolvedValue(undefined)
  await enterWorkspace(navigate)
  expect(navigate).toHaveBeenCalledOnce()
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
})

it('skips the replica and restores focus when the user turns page animations off', async () => {
  await enterWorkspace(async () => {
    expect(document.querySelector('.book-transition-overlay')).toBeNull()
    document.querySelector('#app')!.innerHTML = '<main class="main-area"><h1>知识库</h1></main>'
  }, false, { kind: 'paper', direction: 1, duration: 0 })
  expect(document.activeElement?.textContent).toBe('知识库')
  expect(document.querySelector('#app')!.hasAttribute('inert')).toBe(false)
})

it('keeps the original backdrop present while the next route loads', async () => {
  document.querySelector<HTMLElement>('.login-page')!.style.backgroundColor = 'rgb(225, 229, 217)'
  await enterWorkspace(async () => {
    const backdrop = document.querySelector<HTMLElement>('.book-transition-backdrop')
    expect(backdrop).not.toBeNull()
    expect(backdrop!.style.backgroundColor).toBe('rgb(225, 229, 217)')
  })
})

it('freezes the current illustration pose instead of restarting its animation', async () => {
  const style = document.createElement('style')
  style.textContent = '.test-moving { animation-name: test-turn; transform: matrix(1, 0, 0, 1, 12, 0); opacity: .7; }'
  document.body.append(style)
  document.querySelector('.login-story')!.innerHTML = '<span class="test-moving">插画</span>'
  await enterWorkspace(async () => {
    const clone = document.querySelector<HTMLElement>('.book-transition-overlay .test-moving')!
    expect(clone.style.transform).toBe('matrix(1, 0, 0, 1, 12, 0)')
    expect(clone.style.opacity).toBe('0.7')
  })
})

it('reads computed animation styles only for animated elements when the browser supports discovery', async () => {
  const source = document.querySelector<HTMLElement>('.login-page')!
  source.querySelector('.login-story')!.innerHTML = '<span class="moving" style="transform:translateX(12px);opacity:.7">插画</span>' + '<span class="static">正文</span>'.repeat(100)
  const moving = source.querySelector('.moving')!
  Object.defineProperty(source, 'getAnimations', { value: () => [{ effect: { target: moving } }] })
  const styles = vi.spyOn(window, 'getComputedStyle')
  await enterWorkspace(async () => {
    expect(document.querySelector<HTMLElement>('.book-transition-overlay .moving')!.style.opacity).toBe('0.7')
    expect(styles.mock.calls.filter(([element]) => element.classList.contains('static'))).toHaveLength(0)
  })
})

it('keeps offscreen cards as same-height placeholders only in the transition copy', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><div class="knowledge-grid"><article class="knowledge-card"><p>屏幕外长内容</p></article></div></main></div>'
  const card = document.querySelector('.knowledge-card')!
  vi.spyOn(card, 'getBoundingClientRect').mockReturnValue({ top: 3000, bottom: 3240, left: 0, right: 300, width: 300, height: 240 } as DOMRect)
  await enterWorkspace(async () => {
    const copy = document.querySelector<HTMLElement>('.book-transition-overlay .knowledge-card')!
    expect(copy.children).toHaveLength(0)
    expect(copy.style.height).toBe('240px')
    expect(card.textContent).toBe('屏幕外长内容')
  }, true)
})

it('uses the current workspace page for bookmark navigation', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>知识库</h1></main></div>'
  await enterWorkspace(async () => {
    expect(document.querySelector('.book-transition-overlay .main-area')?.textContent).toBe('知识库')
    expect(document.querySelector('.book-transition-overlay .login-page')).toBeNull()
  }, true)
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
  expect(document.activeElement?.tagName).toBe('H1')
})

it('preserves nested horizontal and vertical reading positions in the outgoing copy', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><div class="reading-scroll">长文档</div></main></div>'
  const scroller = document.querySelector<HTMLElement>('.reading-scroll')!
  scroller.scrollTop = 240
  scroller.scrollLeft = 70
  await enterWorkspace(async () => {
    const copy = document.querySelector<HTMLElement>('.book-transition-overlay .reading-scroll')!
    expect(copy.scrollTop).toBe(240)
    expect(copy.scrollLeft).toBe(70)
  }, true)
})

it('curls the visible reading page rather than opening a central aperture', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area">内容</main></div>'
  vi.spyOn(document.querySelector('.main-area')!, 'getBoundingClientRect').mockReturnValue({ left: 400, right: 1000, top: 100, bottom: 1000, width: 600, height: 900 } as DOMRect)
  vi.stubGlobal('innerWidth', 1000)
  vi.stubGlobal('innerHeight', 800)
  const frames: Keyframe[][] = []
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: (value: Keyframe[]) => {
    frames.push(value)
    return { finished: Promise.resolve(), cancel() {} }
  } })
  try {
    await enterWorkspace(async () => {}, true, chooseStoryTransition('/knowledge', '/analysis'))
    expect(frames.some(value => value.length > 20 && String(value[0]?.clipPath).startsWith('polygon('))).toBe(true)
  } finally { delete (HTMLElement.prototype as Partial<HTMLElement>).animate }
})

it('turns the destination page with a curled sheet and releases interaction on Escape', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>知识库</h1></main></div>'
  const pending: Array<() => void> = []
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: () => {
    let finish!: () => void
    const finished = new Promise<Animation>(resolve => { finish = () => resolve({} as Animation) })
    pending.push(finish)
    return { finished, cancel: finish } as unknown as Animation
  } })
  const entering = enterWorkspace(async () => {
    document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>学习助手</h1></main></div>'
  }, true)
  await new Promise(resolve => setTimeout(resolve, 0))
  try {
    expect(document.querySelector('.story-transition-title')).toBeNull()
    expect(document.querySelector('.page-curl-reverse')).not.toBeNull()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await entering
    expect(document.querySelector('.book-transition-overlay')).toBeNull()
    expect(document.querySelector('#app')?.hasAttribute('inert')).toBe(false)
    expect(document.activeElement?.textContent).toBe('学习助手')
  } finally {
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate
    pending.forEach(finish => finish())
    await entering
  }
})

it.each([
  ['/knowledge', 'paper'], ['/knowledge/42', 'paper'], ['/search', 'paper'],
  ['/study', 'paper'], ['/analysis', 'paper'], ['/profile', 'paper'],
])('plays %s without retaining a blocker after completion', async (destination, kind) => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><article data-knowledge-id="42"><h3>研究资料</h3></article><h1>工作台</h1></main></div>'
  const pending: Array<() => void> = []
  const calls: Array<{ frames: Keyframe[]; duration: number }> = []
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: (frames: Keyframe[], options: KeyframeAnimationOptions) => {
    let finish!: () => void
    const finished = new Promise<void>(resolve => { finish = resolve })
    pending.push(finish)
    calls.push({ frames, duration: Number(options.duration) })
    return { finished, cancel: finish }
  } })
  const entering = enterWorkspace(async () => {
    document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>目的页面</h1></main></div>'
  }, true, chooseStoryTransition('/dashboard', destination))
  await new Promise(resolve => setTimeout(resolve, 0))
  try {
    const overlay = document.querySelector<HTMLElement>('.story-transition')!
    expect(overlay.dataset.storyKind).toBe(kind)
    expect(overlay.querySelector('.story-transition-caption')).toBeNull()
    expect(overlay.querySelector('.page-curl-reverse')).not.toBeNull()
    expect(calls.length).toBeGreaterThan(0)
    expect(Math.max(...calls.map(call => call.duration))).toBe(1500)
    if (['focus', 'search', 'paper', 'compare'].includes(kind)) {
      expect(calls.some(call => call.frames.some(frame => frame.clipPath))).toBe(true)
    }
  } finally {
    pending.forEach(finish => finish())
    await entering
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate
  }
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
  expect(document.querySelector('#app')!.hasAttribute('inert')).toBe(false)
})

it('cancels an in-flight transition before a later navigation replaces the page', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>资料</h1></main></div>'
  const pending: Array<() => void> = []
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: () => {
    let finish!: () => void
    const finished = new Promise<void>(resolve => { finish = resolve })
    pending.push(finish)
    return { finished, cancel: finish }
  } })
  const entering = enterWorkspace(async () => {}, true, chooseStoryTransition('/knowledge', '/search'))
  await new Promise(resolve => setTimeout(resolve, 0))
  try {
    cancelWorkspaceTransition()
    await entering
    expect(document.querySelector('.book-transition-overlay')).toBeNull()
    expect(document.querySelector('#app')!.hasAttribute('inert')).toBe(false)
  } finally {
    pending.forEach(finish => finish())
    await entering
    delete (HTMLElement.prototype as Partial<HTMLElement>).animate
  }
})
