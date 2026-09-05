import { renderMarkdown } from './markdown'

it('renders markdown code blocks while escaping raw html', () => {
  const html = renderMarkdown('```java\nSystem.out.println(1);\n```\n<script>alert(1)</script>')
  expect(html).toContain('<pre>')
  expect(html).toContain('System.out.println(1);')
  expect(html).not.toContain('<script>')
})

