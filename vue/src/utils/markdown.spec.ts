import { renderMarkdown } from './markdown'

it('renders markdown code blocks while escaping raw html', () => {
  const html = renderMarkdown('```java\nSystem.out.println(1);\n```\n<script>alert(1)</script>')
  expect(html).toContain('<pre>')
  expect(html).toContain('System')
  expect(html).toContain('hljs-number')
  expect(html).not.toContain('<script>')
})
it('escapes code content with an unsupported language', () => {
  const html = renderMarkdown('```unknown\n<script>alert(1)</script>\n```')
  expect(html).not.toContain('<script>')
  expect(html).toContain('&lt;script&gt;')
})

it('links citation markers only when the citation exists', () => {
  const html = renderMarkdown('结论 [1]，另见 [3] 与 `[1]`', 2)
  expect(html).toContain('data-cite="1"')
  expect(html).not.toContain('data-cite="3"')
  expect(html).toContain('<code>[1]</code>')
  expect(renderMarkdown('结论 [1]')).not.toContain('cite-ref')
})
