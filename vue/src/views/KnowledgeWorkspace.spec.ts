import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import Workspace from './KnowledgeWorkspace.vue'
import { http } from '@/api/http'
import { listKnowledgeBases } from '@/api/knowledge'
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 1 } }) }))
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
it('sends the displayed version and preserves the draft when another edit wins',async()=>{
 vi.mocked(http.get).mockResolvedValue({items:[{id:8,kind:'FAQ',title:'原题',body:'原文',version:2,sortOrder:0}],hasMore:false} as never)
 vi.mocked(http.put).mockRejectedValueOnce(new Error('原内容已有新版本'))
 const w=await setup();await flushPromises()
 await w.findAll('button').find(b=>b.text()==='编辑')!.trigger('click')
 await w.get('textarea[aria-label="正文"]').setValue('保留的草稿')
 await w.get('form').trigger('submit');await flushPromises()
 expect(http.put).toHaveBeenCalledWith('/knowledge-bases/2/content/8',expect.objectContaining({baseVersion:2,body:'保留的草稿'}))
 expect(w.get('[role="alert"]').text()).toContain('原内容已有新版本')
 expect((w.get('textarea[aria-label="正文"]').element as HTMLTextAreaElement).value).toBe('保留的草稿')
 w.unmount()
})
