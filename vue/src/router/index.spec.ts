import router from './index'

it('declares protected dashboard and public login routes', () => {
  expect(router.hasRoute('dashboard')).toBe(true)
  expect(router.hasRoute('login')).toBe(true)
})

