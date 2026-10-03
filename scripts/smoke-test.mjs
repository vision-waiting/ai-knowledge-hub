import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const base = process.env.SMOKE_API_URL || 'http://127.0.0.1:8082/api'
const password = process.env.SMOKE_PASSWORD || '123456'
const username = process.env.SMOKE_USER || 'student'
const login = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) }).then(r => r.json())
assert.equal(login.code, 200)
const headers = { Authorization: `Bearer ${login.data.token}` }
async function request(path, method = 'GET', body) {
  const response = await fetch(base + path, { method, headers: { ...headers, ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) }, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined })
  const json = await response.json()
  assert.ok(response.ok, `${method} ${path}: ${json.message}`)
  return json.data
}
const state = await request('/system/status')
console.log('Runtime:', JSON.stringify(state))
const knowledge = await request('/knowledge-bases', 'POST', { name: `API verification ${Date.now()}`, description: 'Disposable public technical fixture' })
let document
try {
  const source = await readFile(new URL('../knowledge-data/spring/Spring-Boot-guide.md', import.meta.url))
  const form = new FormData(); form.set('file', new Blob([source], { type: 'text/markdown' }), 'Spring-Boot-guide.md')
  document = await request(`/knowledge-bases/${knowledge.id}/documents`, 'POST', form)
  async function ready() {
    const start = Date.now()
    const statuses = new Set()
    while (Date.now() - start < 120_000) {
      document = await request(`/documents/${document.id}`); statuses.add(document.status)
      if (document.status === 'SUCCESS') { console.log('Processing:', [...statuses].join(' -> ')); return }
      assert.notEqual(document.status, 'FAILED', document.errorMessage)
      await new Promise(resolve => setTimeout(resolve, 350))
    }
    throw new Error('Processing timeout')
  }
  await ready()
  const chunks = await request(`/documents/${document.id}/chunks`); assert.ok(chunks.length)
  const download = await fetch(`${base}/documents/${document.id}/download`, { headers })
  assert.equal(Buffer.compare(Buffer.from(await download.arrayBuffer()), source), 0)
  await request(`/documents/${document.id}/reindex`, 'POST'); await ready()
  assert.equal((await request(`/documents/${document.id}/chunks`)).length, chunks.length)
  const foreign = await fetch(`${base}/documents/${document.id}/chunks`)
  assert.equal(foreign.status, 401)
  async function chat(question, sessionId) {
    const query = new URLSearchParams({ knowledgeBaseId: String(knowledge.id), question })
    if (sessionId) query.set('sessionId', String(sessionId))
    const started = Date.now()
    const response = await fetch(`${base}/chat/stream?${query}`, { headers, signal: AbortSignal.timeout(130_000) })
    assert.equal(response.status, 200)
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let text = ''; let firstToken
    while (true) {
      const { done, value } = await reader.read(); if (done) break
      text += decoder.decode(value, { stream: true })
      if (!firstToken && text.includes('event:message')) firstToken = Date.now() - started
    }
    assert.ok(!text.includes('event:error'), 'SSE error: ' + text.slice(-500))
    assert.ok(text.includes('event:done'), 'Missing completion event')
    const meta = text.match(/event:meta\r?\ndata:(.*)/)
    assert.ok(meta, 'Missing session metadata')
    return { text, sessionId: JSON.parse(meta[1]).sessionId, firstToken, total: Date.now() - started }
  }
  const answer = await chat('Spring Boot 自动配置根据哪些条件生效？')
  assert.ok(answer.text.includes('event:citation'))
  const history = await request(`/chat/sessions/${answer.sessionId}/messages`)
  assert.equal(history.length, 2); assert.ok(history[1].citations.length); assert.equal(history[1].mode, state.llmMode)
  console.log('Answer:', JSON.stringify({ firstTokenMs: answer.firstToken, totalMs: answer.total, citations: history[1].citations.length, mode: history[1].mode }))
  const followup = await chat('它和 Spring Framework 有什么区别？', answer.sessionId)
  assert.equal(followup.sessionId, answer.sessionId)
  assert.equal((await request(`/chat/sessions/${answer.sessionId}/messages`)).length, 4)
  const refusal = await chat('quantum banana spaceship 987654321')
  assert.ok(!refusal.text.includes('event:citation'))
  const refusalHistory = await request(`/chat/sessions/${refusal.sessionId}/messages`)
  assert.equal(refusalHistory[1].mode, 'INSUFFICIENT_CONTEXT')
  await request(`/chat/sessions/${refusal.sessionId}`, 'DELETE')
  console.log('Verified upload, download, reindex, authentication, streamed answer, follow-up, citations, restored history, refusal and session deletion.')
} finally {
  // Only the knowledge base created by this run is eligible for cleanup.
  try { await request(`/knowledge-bases/${knowledge.id}`, 'DELETE'); console.log('Disposable verification knowledge base removed.') }
  catch (error) { console.error('Verification data cleanup needs attention:', error.message) }
}
