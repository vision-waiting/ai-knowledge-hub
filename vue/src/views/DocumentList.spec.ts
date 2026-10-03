import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import DocumentList from './DocumentList.vue'
import * as api from '@/api/documents'
import { ElSelect } from 'element-plus'
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({isAdmin:false,user:{id:1}}) }))
vi.mock('@/api/knowledge', () => ({getKnowledgeBase:vi.fn().mockResolvedValue({ownerId:1})}))
vi.mock('@/api/documents', () => ({listDocuments:vi.fn().mockResolvedValue([]),listTrash:vi.fn().mockResolvedValue([{id:4,fileName:'已删除.txt',fileType:'txt',fileSize:2,status:'TRASHED',tags:[]}]),restoreDocument:vi.fn().mockResolvedValue({}),listVersions:vi.fn().mockResolvedValue([])}))
it('applies the linked document status and resets it across libraries', async () => {
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/knowledge/:id/documents',component:DocumentList},{path:'/:pathMatch(.*)*',component:{template:'<div />'}}]})
 await router.push('/knowledge/2/documents?status=FAILED')
 const wrapper=mount(DocumentList,{global:{plugins:[ElementPlus,router],stubs:{AppLayout:{template:'<main><slot /></main>'},FileUpload:true,SourceDrawer:true}}})
 await flushPromises()
 expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('FAILED')
 await router.push('/knowledge/3/documents'); await flushPromises()
 expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('')
 wrapper.unmount()
})
it('opens trash and restores an item with a refreshed list', async () => {
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/knowledge/:id/documents',component:DocumentList},{path:'/knowledge/:id',component:{template:'<div />'}},{path:'/knowledge/:id/chat',component:{template:'<div />'}}]})
 await router.push('/knowledge/2/documents'); await router.isReady()
 const wrapper=mount(DocumentList,{global:{plugins:[ElementPlus,router],stubs:{AppLayout:{template:'<main><slot /></main>'},FileUpload:true,SourceDrawer:true}}})
 await flushPromises()
 const trash=wrapper.findAll('button').find(b=>b.text().includes('回收站'))
 expect(trash).toBeDefined()
 await trash!.trigger('click'); await flushPromises()
 expect(api.listTrash).toHaveBeenCalledWith(2)
 const restore=wrapper.findAll('button').find(b=>b.text()==='恢复')
 expect(restore).toBeDefined()
 await restore!.trigger('click'); await flushPromises()
 expect(api.restoreDocument).toHaveBeenCalledWith(4)
 wrapper.unmount()
})
