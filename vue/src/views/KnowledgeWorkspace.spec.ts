import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import Workspace from './KnowledgeWorkspace.vue'
import { http } from '@/api/http'
import { listKnowledgeBases } from '@/api/knowledge'
vi.mock('@/api/http',()=>({http:{get:vi.fn(),post:vi.fn(),put:vi.fn(),delete:vi.fn()}}))
vi.mock('@/api/knowledge',()=>({listKnowledgeBases:vi.fn()}))
async function setup(){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/workspace',component:Workspace}]});await router.push('/workspace');await router.isReady()
 return mount(Workspace,{global:{plugins:[router],stubs:{AppLayout:{template:'<main><slot /></main>'},SourceDrawer:true}}})
}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(listKnowledgeBases).mockResolvedValue([{id:2,name:'Team',ownerId:1,documentCount:0,canManage:true}]);vi.mocked(http.get).mockResolvedValue({items:[],canManage:true,hasMore:false} as never)})
it('publishes a reviewed FAQ and reloads its list',async()=>{
 const w=await setup();await flushPromises();await w.get('[data-test="new-entry"]').trigger('click')
 await w.get('input[aria-label="标题"]').setValue('缓存是什么？');await w.get('textarea[aria-label="正文"]').setValue('审核后的解释')
 await w.get('form').trigger('submit');await flushPromises()
 expect(http.post).toHaveBeenCalledWith('/knowledge-bases/2/content',expect.objectContaining({kind:'FAQ',title:'缓存是什么？',body:'审核后的解释'}));w.unmount()
})
it('resolves a reported issue without exposing private sessions',async()=>{
 const w=await setup();await flushPromises();vi.mocked(http.get).mockResolvedValue([{messageId:7,question:'问题',answer:'回答',reason:'过时',status:'OPEN'}] as never)
 await w.get('[data-test="tab-feedback"]').trigger('click');await flushPromises();await w.get('[data-test="resolve-7"]').trigger('click');await flushPromises()
 expect(http.put).toHaveBeenCalledWith('/feedback/issues/7',{status:'RESOLVED'});expect(w.find('a[href*="chat"]').exists()).toBe(false);w.unmount()
})
it('does not offer management actions to a reader',async()=>{
 vi.mocked(listKnowledgeBases).mockResolvedValue([{id:2,name:'Team',ownerId:1,documentCount:0,canManage:false}])
 const w=await setup();await flushPromises();expect(w.find('[data-test="tab-feedback"]').exists()).toBe(false);expect(w.find('[data-test="new-entry"]').exists()).toBe(false);w.unmount()
})
