import { readFile, mkdir, writeFile } from 'node:fs/promises'

// This imports durable knowledge through the same authenticated API as uploads in the UI.
// Credentials are supplied by the operator and are never written to the report.
const base = (process.env.KNOWLEDGE_API_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const username = process.env.KNOWLEDGE_USER
const password = process.env.KNOWLEDGE_PASSWORD
if (!username || !password) throw new Error('Set KNOWLEDGE_USER and KNOWLEDGE_PASSWORD before importing.')
const loginResponse = await fetch(`${base}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }), signal: AbortSignal.timeout(30_000),
})
const login = await loginResponse.json()
if (!loginResponse.ok || !login.data?.token) throw new Error('Login failed. Check the configured account.')
const headers = { Authorization: `Bearer ${login.data.token}` }
async function request(path, method = 'GET', data) {
  const form = data instanceof FormData
  const response = await fetch(base + path, { method, headers: { ...headers, ...(!form && data ? { 'Content-Type': 'application/json' } : {}) }, body: form ? data : data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(35_000) })
  const payload = await response.json()
  if (!response.ok) throw new Error(`${method} ${path}: ${payload.message}`)
  return payload.data
}
const workspaces = [
  { name: '平台使用手册', description: '从知识库创建、文档上传到引用核查，掌握日常使用流程与账号权限。', question: '如何查看回答引用的原文并下载文件？' },
  { name: '文档接入规范', description: '了解支持的格式、处理状态、失败排查和索引重建，持续维护可用的团队资料。', question: '支持哪些文件格式，文件大小上限是多少？' },
  { name: '开发接口知识库', description: '认证、文档、流式问答和管理接口说明，为系统集成与开发提供依据。', question: '流式问答有哪些 SSE 事件，如何判断回答完整？' },
]
const existing = await request('/knowledge-bases')
const report = []
for (const entry of workspaces) {
  let knowledge = existing.find(item => item.name === entry.name && item.ownerId === login.data.user.id)
  if (!knowledge) knowledge = await request('/knowledge-bases', 'POST', { name: entry.name, description: entry.description })
  const filename = entry.name + '.md'
  const documents = await request(`/knowledge-bases/${knowledge.id}/documents`)
  let document = documents.find(item => item.fileName === filename)
  if (!document) {
    const source = await readFile(new URL(`../knowledge-data/workspace/${filename}`, import.meta.url))
    const form = new FormData(); form.set('file', new Blob([source], { type: 'text/markdown' }), filename)
    document = await request(`/knowledge-bases/${knowledge.id}/documents`, 'POST', form)
  }
  const deadline = Date.now() + 180_000
  while (document.status !== 'SUCCESS') {
    if (document.status === 'FAILED') throw new Error(`${filename}: ${document.errorMessage}. Resolve the error and reindex through the document page.`)
    if (Date.now() > deadline) throw new Error(`${filename}: processing timeout; rerun to resume checking without duplicating the upload.`)
    await new Promise(resolve => setTimeout(resolve, 800))
    document = await request(`/documents/${document.id}`)
  }
  const chunks = await request(`/documents/${document.id}/chunks`)
  if (!chunks.length) throw new Error(`${filename}: no indexed text`)
  report.push({ ...entry, id: knowledge.id, documentId: document.id, chunks: chunks.length, url: `/knowledge/${knowledge.id}/chat` })
  console.log(`${entry.name}: ready, ${chunks.length} chunks, /knowledge/${knowledge.id}/chat`)
}
await mkdir(new URL('../output/', import.meta.url), { recursive: true })
await writeFile(new URL('../output/workspaces.json', import.meta.url), JSON.stringify(report, null, 2))
console.log('Imported 3 persistent knowledge bases. Re-running reuses these libraries and files.')
