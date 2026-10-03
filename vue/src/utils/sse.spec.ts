import { describe, expect, it } from 'vitest'
import { createSseParser } from './sse'

describe('SSE framing', () => {
  it('preserves token whitespace across CRLF and network boundaries', () => {
    const events: { event: string; data: string }[] = []
    const parser = createSseParser(event => events.push(event))
    for (const piece of ['event: message\r', '\ndata:   hello\r', '\n\r', '\nevent: meta\ndata: {"mode":"LOCAL_DEMO"}\n\n']) parser.push(piece)
    parser.finish()
    expect(events).toEqual([{ event: 'message', data: '  hello' }, { event: 'meta', data: '{"mode":"LOCAL_DEMO"}' }])
  })
  it('joins multiline data and flushes the final event', () => {
    const events: { event: string; data: string }[] = []
    const parser = createSseParser(event => events.push(event))
    parser.push(': heartbeat\nevent: message\ndata: first\ndata: second')
    parser.finish()
    expect(events).toEqual([{ event: 'message', data: 'first\nsecond' }])
  })
})
