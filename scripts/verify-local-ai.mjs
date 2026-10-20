import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'

const ollama = 'http://127.0.0.1:11434'
async function post(path, body) {
  const response = await fetch(ollama + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(180_000) })
  const data = await response.json()
  assert.ok(response.ok, `Local inference returned HTTP ${response.status}: ${data.error || ''}`)
  return data
}
const version = await fetch(ollama + '/api/version').then(r => r.json())
const generation = await post('/api/chat', {
  model: 'qwen3.5:4b', stream: false, think: false,
  messages: [{ role: 'user', content: '只回复这一句话，不加其他内容：本地模型已就绪。' }],
  options: { num_ctx: 4096, num_predict: 128 },
})
assert.equal(generation.done, true)
assert.ok(generation.message.content.includes('本地模型已就绪'))
assert.ok(!generation.message.thinking, 'Ordinary local questions should not spend tokens on thinking')
const embedding = await post('/v1/embeddings', { model: 'qwen3-embedding:0.6b', input: ['本项目使用本地模型回答知识库问题。', '退款期限为七天。'] })
assert.equal(embedding.data.length, 2)
assert.equal(embedding.data[0].embedding.length, 1024)
assert.ok(embedding.data.every(item => item.embedding.every(Number.isFinite)))
const metadata = await post('/v1/chat/completions', {
  model: 'qwen3.5:4b', reasoning_effort: 'none', max_tokens: 768,
  messages: [
    { role: 'system', content: '只返回JSON对象，包含summary字符串和tags字符串数组，不添加Markdown。' },
    { role: 'user', content: '请总结：本知识库把文档分成片段，通过向量检索找到资料，再由本地模型回答并标记引用。' },
  ],
})
const structured = JSON.parse(metadata.choices[0].message.content)
assert.ok(structured.summary && Array.isArray(structured.tags))
const loaded = await fetch(ollama + '/api/ps').then(r => r.json())
assert.ok(loaded.models.some(model => model.name === 'qwen3.5:4b'))
const report = {
  verifiedAt: new Date().toISOString(), version: version.version,
  response: generation.message.content,
  generationSeconds: Number((generation.total_duration / 1e9).toFixed(2)),
  generatedTokens: generation.eval_count,
  tokensPerSecond: Number((generation.eval_count / (generation.eval_duration / 1e9)).toFixed(1)),
  embeddingDimensions: embedding.data[0].embedding.length,
  metadata: structured,
  loadedModels: loaded.models.map(({ name, size, size_vram, context_length }) => ({ name, size, size_vram, context_length })),
}
await writeFile(new URL('../.runtime/local-ai-verification.json', import.meta.url), JSON.stringify(report, null, 2))
console.log(JSON.stringify(report, null, 2))
