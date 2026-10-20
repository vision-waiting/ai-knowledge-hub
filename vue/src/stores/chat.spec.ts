import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'

vi.mock('@/api/chat', () => ({
  streamUrl: (kb: number, question: string, session?: number, provider?: string) => `${kb}/${question}/${session || ''}${provider ? '?provider=' + provider : ''}`,
  listMessages: vi.fn().mockResolvedValue([{ role: 'ASSISTANT', content: 'saved', mode: 'LOCAL_DEMO', citations: [{ chunkId: 5 }] }]),
}))

beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

function response(text: string) {
  return { ok: true, body: new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode(text)); controller.close() } }) }
}

describe('chat isolation and streaming', () => {
  it('sends the selected provider and retains actual model metadata', async () => {
    const chat = useChatStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response('event: status\ndata: 正在排队\n\nevent: message\ndata: 答案\n\nevent: meta\ndata: {"mode":"CLOUD_API","model":"cloud-model"}\n\nevent: done\ndata: {"data":9}\n\n')))
    await chat.send(1, 'question', 'cloud')
    expect(fetch).toHaveBeenCalledWith('1/question/?provider=cloud', expect.anything())
    expect(chat.messages[1]).toMatchObject({ model: 'cloud-model', mode: 'CLOUD_API' })
    expect(chat.status).toBe('')
  })
  it('retains saved answer id from metadata for immediate feedback', async () => {
    const chat = useChatStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response('event: meta\ndata: {"sessionId":9,"messageId":42,"mode":"LOCAL_DEMO"}\n\nevent: done\ndata: {"data":9}\n\n')))
    await chat.send(1, 'question')
    expect(chat.messages[1]?.id).toBe(42)
  })
  it('clears the active session and messages when changing knowledge base', async () => {
    const chat = useChatStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response('event: session\ndata: {"sessionId":12}\n\nevent: done\ndata: {"data":12}\n\n')))
    await chat.send(1, 'first')
    chat.selectKnowledgeBase(2)
    expect(chat.messages).toEqual([])
    expect(chat.sessionId).toBeUndefined()
    await chat.send(2, 'second')
    expect(fetch).toHaveBeenLastCalledWith('2/second/', expect.anything())
  })
  it('records tokens, citations and mode from a streamed answer', async () => {
    const chat = useChatStore()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response('event: session\ndata: {"sessionId":9}\n\nevent: meta\ndata: {"mode":"LOCAL_DEMO"}\n\nevent: message\ndata:   indented\n\nevent: citation\ndata: {"chunkId":4}\n\nevent: done\ndata: {"data":9}\n\n')))
    await chat.send(1, 'question')
    expect(chat.messages[1]).toMatchObject({ content: '  indented', mode: 'LOCAL_DEMO', citations: [{ chunkId: 4 }] })
    expect(chat.sessionId).toBe(9)
    expect(chat.loading).toBe(false)
  })
  it('keeps saved citations when resuming a session', async () => {
    const chat = useChatStore()
    await chat.openSession({ id: 3, knowledgeBaseId: 2, title: 'saved' })
    expect(chat.messages[0]?.citations).toEqual([{ chunkId: 5 }])
    expect(chat.sessionId).toBe(3)
    expect(chat.knowledgeBaseId).toBe(2)
  })
  it('prevents an old response from updating a newly selected knowledge base', async () => {
    let resolve!: (value: unknown) => void
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(value => { resolve = value })))
    const chat = useChatStore()
    const pending = chat.send(1, 'old')
    chat.selectKnowledgeBase(2)
    resolve(response('event: session\ndata: {"sessionId":99}\n\nevent: done\ndata: {"data":99}\n\n'))
    await pending
    expect(chat.messages).toEqual([])
    expect(chat.sessionId).toBeUndefined()
  })
  it('marks a stream closed before done as interrupted', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response('event: message\ndata: partial\n\n')))
    const chat = useChatStore()
    await chat.send(1, 'question')
    expect(chat.messages[1]?.content).toBe('partial')
    expect(chat.messages[1]?.error).toBeTruthy()
  })
})
