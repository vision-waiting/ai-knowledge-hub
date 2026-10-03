import { mount, flushPromises } from '@vue/test-utils'
import ReviewPlan from './ReviewPlan.vue'
import { http } from '@/api/http'
vi.mock('@/api/http',()=>({http:{get:vi.fn(),post:vi.fn()}}))
it('reveals an answer before grading and refreshes the due list',async()=>{
 vi.mocked(http.get).mockResolvedValue([{id:7,front:'问题',back:'解释',studyId:1,knowledgeBaseId:2,intervalDays:0,repetitions:0,dueAt:'2026-01-01'}] as never)
 const w=mount(ReviewPlan,{global:{stubs:{AppLayout:{template:'<main><slot /></main>'},RouterLink:true}}});await flushPromises()
 expect(w.text()).not.toContain('解释');await w.get('[data-test="reveal-7"]').trigger('click');expect(w.text()).toContain('解释')
 await w.get('[data-test="good-7"]').trigger('click');await flushPromises();expect(http.post).toHaveBeenCalledWith('/review/7',{rating:'GOOD'});w.unmount()
})
