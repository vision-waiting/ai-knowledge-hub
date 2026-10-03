import { writeFile } from 'node:fs/promises'

// Creates an isolated example knowledge base; never deletes or edits existing user data.
const base = process.env.KNOWLEDGE_API || 'http://127.0.0.1:8080/api'
let token
async function request(path, options = {}) {
  const response = await fetch(`${base}${path}`, { ...options, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers }, signal: AbortSignal.timeout(180000) })
  const body = await response.json()
  if (!response.ok || body.code !== 200) throw new Error(`${options.method || 'GET'} ${path}: ${response.status} ${body.message}`)
  return body.data
}
const send = (path, method, data) => request(path, {method,headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})
function check(condition, message) { if (!condition) throw new Error(message) }
const login = await send('/auth/login', 'POST', {username:process.env.KNOWLEDGE_USERNAME,password:process.env.KNOWLEDGE_PASSWORD})
token = login.token
check(token, 'Login did not return a token')
const kb = await send('/knowledge-bases','POST',{name:'功能体验：缓存与消息队列',description:'用于验证总结对比、学习测验、笔记与更新提醒的示例资料。'})
await request(`/knowledge-bases/${kb.id}/subscription`,{method:'PUT'})
const examples = [
  ['缓存方案.md', '# 缓存方案\n\n缓存用于减少重复数据库查询，适用于读取频繁且允许短暂数据延迟的页面。缓存命中时直接返回缓存内容，未命中时查询数据库并回填缓存。\n\n数据更新后先提交数据库事务，再删除旧缓存。热点数据设置五分钟过期时间，并在过期时间上加入随机偏移，避免大量缓存同时失效。\n\n主要优势是降低读取延迟、减少数据库负载；主要成本是额外存储和一致性维护。缓存不能代替数据库持久保存订单。\n\n系统需要监测缓存命中率、读取延迟和失效数量。对缓存穿透可以使用空值缓存，空值过期时间设为三十秒。'],
  ['消息队列方案.md', '# 消息队列方案\n\n消息队列用于缓冲突发请求和解耦异步任务，适用于发送通知、生成报表等不要求立即完成的操作。生产者发送消息，消费者从队列读取并执行业务。\n\n消费者完成业务后再确认消息。处理失败时最多重试三次，仍失败的消息进入死信队列并记录原因。业务应使用唯一业务编号实现幂等，避免重复消息产生重复订单。\n\n主要优势是削峰和解耦；主要成本是处理延迟与消息积压管理。消息队列不能直接替代查询缓存。\n\n系统需要监测积压消息数量、消费耗时和失败率。重要消息需要持久化保存，服务重启后继续处理。']
]
const documentIds = []
for (const [name,text] of examples) {
  const form = new FormData(); form.append('file',new Blob([text],{type:'text/markdown'}),name)
  const doc = await request(`/knowledge-bases/${kb.id}/documents`,{method:'POST',body:form})
  documentIds.push(doc.id)
}
console.log(`Created example knowledge base ${kb.id}; waiting for documents.`)
for (const id of documentIds) {
  let ready = false
  for (let i=0;i<90;i++) {
    const doc = await request(`/documents/${id}`)
    if (doc.status === 'FAILED') throw new Error(`Document failed: ${doc.errorMessage}`)
    if (doc.status === 'SUCCESS') {ready=true;break}
    await new Promise(resolve=>setTimeout(resolve,1000))
  }
  check(ready,'Document did not finish processing')
}
console.log('Documents ready; generating summary and comparison.')
const summary = await send(`/knowledge-bases/${kb.id}/analysis`,'POST',{type:'SUMMARY',documentIds:[documentIds[0]],focus:'目的、实施步骤和注意事项'})
check(summary.content.length>20,'Summary is empty')
const comparison = await send(`/knowledge-bases/${kb.id}/analysis`,'POST',{type:'COMPARE',documentIds,focus:'适用场景、优势、成本和监测指标'})
check(comparison.content.length>20,'Comparison is empty')
const analyses = await request(`/knowledge-bases/${kb.id}/analysis`)
check(analyses.some(item=>item.id===summary.id)&&analyses.some(item=>item.id===comparison.id),'Analysis history missing')
console.log('Summary and comparison persisted; generating learning material.')
const study = await send(`/knowledge-bases/${kb.id}/study`,'POST',{documentIds,questionCount:3})
check(study.cards.length>0&&study.questions.length===3,'Learning material missing')
check(study.questions.every(item=>!('correctIndex' in item)&&!('explanation' in item)),'Quiz answer keys leaked before submission')
const first = await send(`/study/${study.id}/submit`,'POST',{answers:study.questions.map(()=>0)})
check(first.results.length===3,'Quiz grading missing')
const second = await send(`/study/${study.id}/submit`,'POST',{answers:first.results.map(item=>item.correctIndex)})
check(second.score===3&&second.total===3,'Server grading incorrect')
check((await request(`/study/${study.id}/attempts`)).length===2,'Attempts not saved')
check((await request(`/study/${study.id}`)).cards.length>0,'Study restore failed')
const mistakes = await request('/study/mistakes?includeResolved=true')
check(mistakes.filter(item=>item.studyId===study.id).every(item=>item.resolved),'Correct answers did not resolve mistakes')
console.log('Quiz grading and restore passed; checking notes and notifications.')
const note = await send('/notes','POST',{title:'缓存与队列对比笔记',content:comparison.content,category:'功能体验'})
await send(`/notes/${note.id}`,'PUT',{title:note.title,content:`${note.content}\n\n## 复习提醒\n区分查询加速与异步任务处理。`,category:'技术学习'})
check((await request('/notes?category='+encodeURIComponent('技术学习')+'&keyword='+encodeURIComponent('复习提醒'))).some(item=>item.id===note.id),'Note edit/search failed')
const notifications = await request('/notifications?pageSize=100')
const ownEvents = notifications.items.filter(item=>item.knowledgeBaseId===kb.id)
check(ownEvents.some(item=>item.type==='UPLOADED')&&ownEvents.some(item=>item.type==='SUCCESS'),'Document notifications missing')
await request(`/notifications/${ownEvents[0].id}/read`,{method:'PUT'})
check((await request('/notifications?pageSize=100')).items.find(item=>item.id===ownEvents[0].id).read,'Read state did not persist')
const result={knowledgeBaseId:kb.id,documentIds,summaryId:summary.id,comparisonId:comparison.id,studyId:study.id,noteId:note.id,mode:study.mode,score:second.score,notifications:ownEvents.length}
await writeFile(new URL('../.runtime/feature-smoke-result.json',import.meta.url),JSON.stringify(result,null,2))
console.log('All four feature workflows passed:',JSON.stringify(result))
