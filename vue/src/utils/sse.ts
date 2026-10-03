export function createSseParser(onEvent: (event: { event: string; data: string }) => void) {
  let buffer = ''
  let event = 'message'
  let data: string[] = []
  function dispatch() {
    if (data.length) onEvent({ event, data: data.join('\n') })
    event = 'message'
    data = []
  }
  function line(value: string) {
    if (!value) return dispatch()
    if (value.startsWith(':')) return
    const colon = value.indexOf(':')
    const field = colon < 0 ? value : value.slice(0, colon)
    let content = colon < 0 ? '' : value.slice(colon + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (field === 'event') event = content || 'message'
    if (field === 'data') data.push(content)
  }
  function push(value: string) {
    buffer += value
    let newline: number
    while ((newline = buffer.indexOf('\n')) >= 0) {
      line(buffer.slice(0, newline).replace(/\r$/, ''))
      buffer = buffer.slice(newline + 1)
    }
  }
  return { push, finish() { if (buffer) line(buffer.replace(/\r$/, '')); buffer = ''; dispatch() } }
}
