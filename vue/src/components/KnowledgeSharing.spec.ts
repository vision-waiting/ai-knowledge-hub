import { mount, flushPromises } from '@vue/test-utils'
import KnowledgeSharing from './KnowledgeSharing.vue'
import { http } from '@/api/http'
vi.mock('@/api/http', () => ({ http: { put: vi.fn(), delete: vi.fn() } }))
it('persists a star and makes the canonical share link available', async () => {
  vi.mocked(http.put).mockResolvedValue({ starred: true, starCount: 4 } as never)
  const w = mount(KnowledgeSharing, { props: { item: { id: 3, name: 'Java', ownerId: 1, shared: true, starred: false, starCount: 3, documentCount: 0 } } })
  await w.get('[data-test="star"]').trigger('click'); await flushPromises()
  expect(http.put).toHaveBeenCalledWith('/knowledge-bases/3/star')
  expect(w.get('[data-test="star"]').attributes('aria-pressed')).toBe('true')
  expect(w.get('[aria-label="分享链接"]').element).toHaveProperty('value', `${location.origin}/knowledge/3`)
  w.unmount()
})
