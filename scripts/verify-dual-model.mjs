import assert from 'node:assert/strict'
import { readFile, writeFile, mkdir } from 'node:fs/promises'

const base = process.env.SMOKE_API_URL || 'http://127.0.0.1:8080/api'
const login = await fetch(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: process.env.SMOKE_USER || 'student', password: process.env.SMOKE_PASSWORD || '123456' }) }).then(r => r.json())
assert.equal(login.code, 200, 'Login failed')
const headers = { Authorization: `Bearer ${login.data.token}` }
async function api(path, method = 'GET', body) {
  const response = await fetch(base + path, { method, headers: { ...headers, ...(body && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) }, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(200_000) })
  const json = await response.json()
  assert.equal(json.code, 200, `${path}: ${json.message}`)
  return json.data
}
const models = await api('/system/chat-models')
assert.equal(models.defaultProvider, 'local')
assert.ok(!JSON.stringify(models).match(/apiKey|api-key|baseUrl|base-url/i), 'Public metadata must not contain credentials or provider endpoint')
const cases = JSON.parse(await readFile(new URL('./fixtures/dual-model-questions.json', import.meta.url), 'utf8'))
const report = { startedAt: new Date().toISOString(), models, results: [], note: 'Keyword checks are a screening aid, not a semantic accuracy score. Review answer and citations against expected content.' }
await mkdir(new URL('../.runtime/', import.meta.url), { recursive: true })
const save = () => writeFile(new URL('../.runtime/dual-model-acceptance.json', import.meta.url), JSON.stringify(report, null, 2))
const knowledge = await api('/knowledge-bases', 'POST', { name: `双模型验收 ${Date.now()}`, description: 'Temporary public project manuals; created by verify-dual-model.mjs' })
const sessions = new Map()
async function chat(test, provider) {
  const query = new URLSearchParams({ knowledgeBaseId: String(knowledge.id), question: test.question, provider })
  if (test.followup) query.set('sessionId', String(sessions.get(test.followup)))
  const started = Date.now()
  const response = await fetch(`${base}/chat/stream?${query}`, { headers, signal: AbortSignal.timeout(130_000) })
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let raw = '', firstTokenMs
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    raw += decoder.decode(value, { stream: true })
    if (firstTokenMs == null && /event:\s*message/.test(raw)) firstTokenMs = Date.now() - started
  }
  raw += decoder.decode()
  const events = raw.replace(/\r\n/g, '\n').split('\n\n').map(block => ({
    event: block.split('\n').find(line => line.startsWith('event:'))?.slice(6).trim(),
    data: block.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).replace(/^ /, '')).join('\n'),
  }))
  const failure = events.find(event => event.event === 'error')
  assert.ok(!failure, failure?.data)
  assert.ok(events.some(event => event.event === 'done'), 'Missing done event')
  const meta = JSON.parse(events.find(event => event.event === 'meta').data)
  sessions.set(test.id, meta.sessionId)
  const history = await api(`/chat/sessions/${meta.sessionId}/messages`)
  const answer = history.at(-1)
  assert.equal(answer.model, meta.model)
  let error
  try {
    assert.equal(answer.mode, test.mode || (provider === 'cloud' ? 'CLOUD_API' : 'LOCAL_MODEL'))
    if (!test.mode) {
      assert.equal(meta.model, models.options.find(option => option.id === provider).model)
      assert.ok(answer.citations.length, 'Answer must cite sources')
      assert.ok(answer.citations.some(citation => citation.documentName === test.source), 'Expected source not cited')
    } else { assert.equal(answer.citations.length, 0); assert.equal(answer.model, null) }
  } catch (cause) { error = cause.message }
  return { id: test.id, provider, question: test.question, expected: test.expected, answer: answer.content, mode: meta.mode, model: meta.model, citations: answer.citations, retrieval: answer.retrieval, firstTokenMs, totalMs: Date.now() - started, keywordCheck: (test.terms || []).every(term => new RegExp(term, 'i').test(answer.content)), error }
}
try {
  for (const name of ['平台使用手册.md', '文档接入规范.md']) {
    const text = await readFile(new URL(`../knowledge-data/workspace/${name}`, import.meta.url))
    const form = new FormData(); form.set('file', new Blob([text], { type: 'text/markdown' }), name)
    const doc = await api(`/knowledge-bases/${knowledge.id}/documents`, 'POST', form)
    const deadline = Date.now() + 240_000
    while (true) {
      const current = await api(`/documents/${doc.id}`)
      if (current.status === 'SUCCESS') break
      assert.notEqual(current.status, 'FAILED', current.errorMessage)
      assert.ok(Date.now() < deadline, 'Document processing timed out')
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    console.log(`Indexed ${name}`)
  }
  for (const test of cases) {
    try {
      const result = await chat(test, 'local'); report.results.push(result)
      console.log(`${test.id} local ${result.mode} ${result.totalMs}ms keywords=${result.keywordCheck} checks=${!result.error}`)
    } catch (error) { report.results.push({ id: test.id, provider: 'local', error: error.message }); console.log(`${test.id} FAILED: ${error.message}`) }
    await save()
  }
  // Only public project manuals are sent to the already configured cloud provider.
  if (process.env.ACCEPTANCE_SKIP_CLOUD !== 'true' && models.options.some(option => option.id === 'cloud' && option.configured)) {
    for (const test of [cases[1], cases[11]]) {
      try {
        const result = await chat(test, 'cloud'); report.results.push(result)
        console.log(`${test.id} cloud ${result.mode} ${result.totalMs}ms keywords=${result.keywordCheck}`)
      } catch (error) { report.results.push({ id: test.id, provider: 'cloud', error: error.message }); console.log(`cloud FAILED: ${error.message}`) }
      await save()
    }
  }
} finally {
  await api(`/knowledge-bases/${knowledge.id}`, 'DELETE')
  report.finishedAt = new Date().toISOString()
  await save()
  console.log('Temporary acceptance knowledge base removed. Report: .runtime/dual-model-acceptance.json')
}
if (report.results.some(result => result.error || result.keywordCheck === false)) process.exitCode = 1
