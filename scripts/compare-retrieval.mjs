import { readFile, writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
const report = JSON.parse(await readFile(process.argv[2] || 'retrieval-report.json', 'utf8'))
const results = report.results.filter(r => r.sources.length).map(row => {
  const vector = [...row.chunks].filter(c => c.vectorScore > 0).sort((a,b)=>b.vectorScore-a.vectorScore)
  const keyword = [...row.chunks].filter(c => c.keywordScore > 0).sort((a,b)=>b.keywordScore-a.keywordScore)
  const score = c => [vector, keyword].reduce((sum,list) => {
    const rank = list.findIndex(x=>x.chunkId===c.chunkId)
    return sum + (rank < 0 ? 0 : 1/(61+rank))
  },0)
  const rrf = [...row.chunks].sort((a,b)=>score(b)-score(a)||a.chunkId-b.chunkId)
  const rr = list => { const i=list.findIndex(c=>row.sources.includes(c.documentName)); return i<0?0:1/(i+1) }
  return { id: row.id, weightedRR: rr(row.chunks), rrfRR: rr(rrf) }
})
assert.ok(results.length, 'No answerable cases')
const mean = field => results.reduce((s,r)=>s+r[field],0)/results.length
await writeFile('retrieval-comparison.json', JSON.stringify({
  limitation: 'Reranks only captured candidates; not a full-recall RRF benchmark. No production ranking changed.',
  weightedMRR: mean('weightedRR'), rrfMRR: mean('rrfRR'), results
},null,2))
