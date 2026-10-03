import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'

const base = (process.env.KNOWLEDGE_API_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const { KNOWLEDGE_USER: username, KNOWLEDGE_PASSWORD: password } = process.env
assert.ok(username && password, 'Set KNOWLEDGE_USER and KNOWLEDGE_PASSWORD')
const login = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }), signal: AbortSignal.timeout(30_000) }).then(r => r.json())
assert.ok(login.data?.token, 'Login failed')
const headers = { Authorization: `Bearer ${login.data.token}` }
async function request(path) {
  const response = await fetch(base + path, { headers, signal: AbortSignal.timeout(30_000) })
  assert.ok(response.ok, path)
  return (await response.json()).data
}
const state = await request('/system/status')
const workspaces = JSON.parse(await readFile(new URL('../output/workspaces.json', import.meta.url)))
const report = []
for (const entry of workspaces) {
  const started = Date.now()
  const query = new URLSearchParams({ knowledgeBaseId: String(entry.id), question: entry.question })
  const response = await fetch(`${base}/chat/stream?${query}`, { headers, signal: AbortSignal.timeout(130_000) })
  assert.equal(response.status, 200)
  const stream = await response.text()
  assert.ok(!stream.includes('event:error'), stream.slice(-600))
  assert.ok(stream.includes('event:done'), 'Missing completion')
  const meta = stream.match(/event:meta\r?\ndata:(.*)/)
  assert.ok(meta, 'Missing metadata')
  const sessionId = JSON.parse(meta[1]).sessionId
  const history = await request(`/chat/sessions/${sessionId}/messages`)
  assert.equal(history.length, 2)
  const answer = history[1]
  assert.equal(answer.mode, state.llmMode)
  assert.ok(answer.citations?.length, 'Missing sources')
  assert.ok(answer.citations.every(c => c.documentId === entry.documentId), 'Wrong source document')
  const chunks = await request(`/documents/${entry.documentId}/chunks`)
  assert.ok(answer.citations.every(c => chunks.some(chunk => chunk.id === c.chunkId)), 'Missing cited chunk')
  const download = await fetch(`${base}/documents/${entry.documentId}/download`, { headers })
  assert.equal(download.status, 200)
  const original = await readFile(new URL(`../knowledge-data/workspace/${entry.name}.md`, import.meta.url))
  assert.equal(Buffer.compare(Buffer.from(await download.arrayBuffer()), original), 0)
  report.push({ name: entry.name, sessionId, mode: answer.mode, citations: answer.citations.length, elapsedMs: Date.now() - started })
  console.log(JSON.stringify(report.at(-1)))
}
await writeFile(new URL('../output/workspaces-verification.json', import.meta.url), JSON.stringify({ checkedAt: new Date().toISOString(), results: report }, null, 2))
console.log('All three libraries passed streamed answers, source integrity, restored history and original downloads. Conversations retained.')
