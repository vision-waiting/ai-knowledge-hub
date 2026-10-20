// Reuse existing SQL chunks: no source documents or old Qdrant collections are deleted.
import { execFileSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'

const root = new URL('../', import.meta.url)
const settings = Object.fromEntries((await readFile(new URL('.env', root), 'utf8')).split(/\r?\n/)
  .filter(line => /^[A-Z_]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1).trim()]))
const collection = settings.QDRANT_COLLECTION
assert.match(collection || '', /^knowledge_chunks_qwen3_[a-z0-9_]+$/, 'Migration must target a separate local-model collection')
assert.equal(settings.EMBEDDING_MODEL, 'qwen3-embedding:0.6b')
assert.equal(settings.EMBEDDING_BASE_URL, 'http://127.0.0.1:11434/v1')
const qdrant = `http://127.0.0.1:${settings.QDRANT_PORT || 6333}/collections/${collection}`
const sql = "SELECT JSON_OBJECT('id',c.id,'documentId',c.document_id,'knowledgeBaseId',c.knowledge_base_id,'pageNumber',c.page_number,'content',c.content) FROM document_chunk c JOIN document d ON d.id=c.document_id WHERE d.status='SUCCESS' ORDER BY c.id"
const command = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --default-character-set=utf8mb4 --batch --raw --skip-column-names'
const raw = execFileSync('docker', ['exec', '-i', 'ai-knowledge-hub-mysql', 'sh', '-c', command], { input: sql, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
const chunks = raw.trim() ? raw.trim().split(/\r?\n/).map(line => JSON.parse(line)) : []
await writeFile(new URL('.runtime/local-ai-chunks.json', root), JSON.stringify(chunks, null, 2))
async function request(url, method, body) {
  const response = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(120_000) })
  const result = await response.json()
  assert.ok(response.ok, `${method} ${url}: ${response.status}`)
  return result
}
let dimension
for (const chunk of chunks) {
  const result = await request(`${settings.EMBEDDING_BASE_URL}/embeddings`, 'POST', { model: settings.EMBEDDING_MODEL, input: chunk.content })
  const vector = result.data[0].embedding
  assert.ok(vector.length > 0 && vector.every(Number.isFinite))
  if (!dimension) {
    dimension = vector.length
    const existing = await fetch(qdrant)
    if (existing.status === 404) await request(qdrant, 'PUT', { vectors: { size: dimension, distance: 'Cosine' } })
    else {
      assert.equal(existing.status, 200)
      assert.equal((await existing.json()).result.config.params.vectors.size, dimension)
    }
  }
  assert.equal(vector.length, dimension)
  const { content, id, ...payload } = chunk
  await request(`${qdrant}/points?wait=true`, 'PUT', { points: [{ id, vector, payload: { ...payload, chunkId: id } }] })
  console.log(`Indexed chunk ${id} (${vector.length} dimensions)`)
}
const report = { collection, model: settings.EMBEDDING_MODEL, chunks: chunks.length, dimension, finishedAt: new Date().toISOString() }
await writeFile(new URL('.runtime/local-ai-migration.json', root), JSON.stringify(report, null, 2))
console.log(JSON.stringify(report))
