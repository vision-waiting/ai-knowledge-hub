// Read-only retrieval requests; requires an existing indexed KB and bearer token.
import { readFile, writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
const base = process.env.EVAL_API_URL || 'http://127.0.0.1:8080/api'
const kb = process.env.EVAL_KB_ID
const token = process.env.EVAL_TOKEN
assert.ok(kb && token, 'Set EVAL_KB_ID and EVAL_TOKEN')
const cases = JSON.parse(await readFile(new URL('./fixtures/retrieval-baseline.json', import.meta.url)))
const results = []
for (const item of cases) {
  const started = performance.now()
  const response = await fetch(`${base}/knowledge-bases/${kb}/retrieval/debug`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: item.standaloneQuestion || item.question, topK: 5 }), signal: AbortSignal.timeout(60000)
  })
  assert.ok(response.ok, `Request failed: ${response.status}`)
  const payload = await response.json(); assert.equal(payload.code, 200)
  const data = payload.data
  const rank = data.chunks.findIndex(c => item.sources.includes(c.documentName)) + 1
  results.push({ ...item, latencyMs: performance.now() - started, hitAt5: item.sources.length ? Number(rank > 0) : null,
    reciprocalRank: item.sources.length ? (rank ? 1 / rank : 0) : null,
    correctRejection: item.sources.length ? null : !data.sufficient, chunks: data.chunks,
    citationSupport: 'requires-human-review' })
}
const times = results.map(r => r.latencyMs).sort((a,b) => a-b)
const report = { generatedAt: new Date().toISOString(), datasetKind: 'constructed-project-fixtures',
  kb, count: results.length, p50Ms: times[Math.ceil(times.length*.5)-1], p95Ms: times[Math.ceil(times.length*.95)-1], results }
await writeFile(process.env.EVAL_OUTPUT || 'retrieval-report.json', JSON.stringify(report, null, 2))
console.log(JSON.stringify({ count: report.count, p50Ms: report.p50Ms, p95Ms: report.p95Ms }))
