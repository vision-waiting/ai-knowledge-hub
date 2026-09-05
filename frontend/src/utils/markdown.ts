import MarkdownIt from 'markdown-it'
const md = new MarkdownIt({ html: false, breaks: true, linkify: true })
export function renderMarkdown(value: string) { return md.render(value || '') }

