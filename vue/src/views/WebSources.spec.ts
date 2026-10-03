import { mount, flushPromises } from '@vue/test-utils'
import WebSources from './WebSources.vue'
import { http } from '@/api/http'
vi.mock('@/api/http',()=>({http:{get:vi.fn().mockResolvedValue([]),post:vi.fn().mockResolvedValue({documentId:4})}}))
vi.mock('@/api/knowledge',()=>({listKnowledgeBases:vi.fn().mockResolvedValue([{id:2,name:'Team',canWrite:true}])}))
it('imports a URL and refreshes the linked sources',async()=>{
 const w=mount(WebSources,{global:{stubs:{AppLayout:{template:'<main><slot /></main>'},RouterLink:true}}});await flushPromises()
 await w.get('input[aria-label="网页地址"]').setValue('https://example.com/manual');await w.get('form').trigger('submit');await flushPromises()
 expect(http.post).toHaveBeenCalledWith('/knowledge-bases/2/web-sources',{url:'https://example.com/manual'},expect.anything())
 expect(w.text()).toContain('已提交');w.unmount()
})
