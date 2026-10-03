import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
for (const [name, language] of Object.entries({ java, javascript, typescript, python, sql, json, bash, xml, css })) hljs.registerLanguage(name, language)
const md = new MarkdownIt({ html: false, breaks: true, linkify: true, highlight(code, language) {
  if (language && hljs.getLanguage(language)) return hljs.highlight(code, { language, ignoreIllegals: true }).value
  return ''
} })
// Turn [n] markers into source buttons, but only for citations that actually exist.
md.inline.ruler.before('link', 'citation_ref', (state, silent) => {
  const count = Number(state.env?.citationCount) || 0
  if (!count || state.src.charCodeAt(state.pos) !== 0x5b) return false
  const match = /^\[(\d{1,2})\](?!\()/.exec(state.src.slice(state.pos))
  const index = match ? Number(match[1]) : 0
  if (!match || index < 1 || index > count) return false
  if (!silent) state.push('citation_ref', '', 0).meta = { index }
  state.pos += match[0].length
  return true
})
md.renderer.rules.citation_ref = (tokens, idx) => {
  const index = tokens[idx].meta.index
  return `<button type="button" class="cite-ref" data-cite="${index}" aria-label="查看引用 ${index}">${index}</button>`
}
export function renderMarkdown(value: string, citationCount = 0) { return md.render(value || '', { citationCount }) }
