import { enterWorkspace } from './enterWorkspace'

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

it('uses the current workspace page for bookmark navigation', async () => {
  document.querySelector('#app')!.innerHTML = '<div class="app-shell"><main class="main-area"><h1>知识库</h1></main></div>'
  await enterWorkspace(async () => {
    expect(document.querySelector('.book-transition-overlay .main-area')?.textContent).toBe('知识库')
    expect(document.querySelector('.book-transition-overlay .login-page')).toBeNull()
  }, true)
  expect(document.querySelector('.book-transition-overlay')).toBeNull()
  expect(document.activeElement?.tagName).toBe('H1')
})
