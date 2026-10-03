import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import StudyAssistant from './StudyAssistant.vue'
import * as api from '@/api/intelligence'

vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn().mockResolvedValue([{id:3,name:'资料库 A',ownerId:1,documentCount:1},{id:4,name:'资料库 B',ownerId:1,documentCount:1}]) }))
vi.mock('@/api/documents', () => ({ listDocuments: vi.fn().mockResolvedValue([{id:1,fileName:'资料.md',status:'SUCCESS',fileType:'md'}]) }))
vi.mock('@/api/intelligence', () => ({ createStudy:vi.fn(),listStudies:vi.fn(),getStudy:vi.fn(),listAttempts:vi.fn(),submitStudy:vi.fn(),listMistakes:vi.fn(),resolveMistake:vi.fn() }))
const study: api.StudySession = {id:7,knowledgeBaseId:3,title:'学习资料',mode:'LOCAL_DEMO',cards:[{front:'回忆端口',back:'8080'}],questions:[{id:1,prompt:'端口？',options:['8080','9000']},{id:2,prompt:'格式？',options:['PDF','EXE']},{id:3,prompt:'方式？',options:['流式','同步']}],createdAt:'2026-09-12T12:00:00',sourceNotice:'原文练习',sources:[]}
async function setup() {
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/knowledge/:id/study',component:StudyAssistant}]})
  await router.push('/knowledge/3/study'); await router.isReady()
  const wrapper=mount(StudyAssistant,{global:{plugins:[ElementPlus,router],stubs:{AppLayout:{template:'<main><slot /></main>'},SaveNoteButton:true}}})
  await flushPromises(); return {wrapper,router}
}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(api.listStudies).mockResolvedValue([study]);vi.mocked(api.listAttempts).mockResolvedValue([]);vi.mocked(api.listMistakes).mockResolvedValue([]);vi.mocked(api.createStudy).mockResolvedValue(study)})
it('keeps partial quiz answers when switching to cards and back',async()=>{
  const {wrapper}=await setup()
  await wrapper.find('input[type=checkbox]').setValue(true)
  await wrapper.find('.tool-primary').trigger('click');await flushPromises()
  const tabs=()=>wrapper.findAll('.tool-results .tool-tabs button')
  await tabs()[1].trigger('click')
  await wrapper.find('input[type=radio]').setValue(true)
  await tabs()[0].trigger('click');await tabs()[1].trigger('click')
  expect(wrapper.find<HTMLInputElement>('input[type=radio]').element.checked).toBe(true)
  wrapper.unmount()
})
it('ignores a late restore response after navigating to another knowledge base',async()=>{
  let finish!: (value:api.StudySession)=>void
  vi.mocked(api.getStudy).mockReturnValue(new Promise(resolve=>{finish=resolve}))
  const {wrapper,router}=await setup()
  await wrapper.find('.tool-history button').trigger('click')
  await router.push('/knowledge/4/study');await flushPromises()
  finish(study);await flushPromises()
  expect(wrapper.find<HTMLSelectElement>('.source-picker select').element.value).toBe('4')
  expect(wrapper.findAll('.tool-result-heading')).toHaveLength(0)
  wrapper.unmount()
})
