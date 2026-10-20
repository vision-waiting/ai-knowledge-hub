<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus, Promotion, VideoPause, ChatDotRound, DocumentChecked } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import { useChatStore } from '@/stores/chat'
import { getKnowledgeBase } from '@/api/knowledge'
import { deleteSession, listSessions } from '@/api/chat'
import { http } from '@/api/http'
import { chatModels, type ChatModelOption } from '@/api/system'
import { errorMessage } from '@/utils/errors'
import type { KnowledgeBase, ChatSession } from '@/types'

const route = useRoute(); const router = useRouter(); const chat = useChatStore()
const item = ref<KnowledgeBase>(); const sessions = ref<ChatSession[]>([]); const question = ref('')
const messagesElement = ref<HTMLElement>(); const pageError = ref(''); const sessionsLoading = ref(false)
const awayFromBottom = ref(false)
const modelOptions = ref<ChatModelOption[]>([])
const provider = ref('local')
const modelsError = ref('')
const modelsLoading = ref(true)
const selectedModel = computed(() => modelOptions.value.find(option => option.id === provider.value))
async function loadModels() {
  modelsLoading.value = true; modelsError.value = ''
  try {
    const result = await chatModels()
    modelOptions.value = result.options
    provider.value = result.defaultProvider
  } catch (cause) { modelsError.value = errorMessage(cause, '模型配置加载失败，请重试') }
  finally { modelsLoading.value = false }
}
onMounted(loadModels)
const defaultExamples = ['这份资料的核心内容是什么？', '请整理资料中的使用步骤', '有哪些需要注意的事项？']
const faqExamples = ref<string[]>([])
const examples = computed(() => faqExamples.value.length ? faqExamples.value : defaultExamples)
async function loadExamples(id: number) {
  faqExamples.value = []
  try {
    const page = await http.get(`/knowledge-bases/${id}/content`, { params: { kind: 'FAQ', limit: 4 } }) as unknown as { items: { title: string }[] }
    if (kbId.value === id) faqExamples.value = page.items.map(item => item.title.trim()).filter(Boolean).slice(0, 4)
  } catch { /* FAQ entries are optional; generic prompts still work. */ }
}
function trackScroll() {
  const el = messagesElement.value
  awayFromBottom.value = !!el && el.scrollHeight - el.scrollTop - el.clientHeight >= 160
}
async function jumpToLatest() {
  await nextTick()
  if (messagesElement.value) messagesElement.value.scrollTop = messagesElement.value.scrollHeight
  awayFromBottom.value = false
}
function sessionTime(value?: string) {
  if (!value) return ''
  const date = new Date(value), now = new Date()
  if (Number.isNaN(date.getTime())) return ''
  return date.toDateString() === now.toDateString()
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}
const kbId = computed(() => Number(route.params.id))
const filteredSessions = computed(() => sessions.value.filter(session => session.knowledgeBaseId === kbId.value))
let routeRequest = 0
let sendRequest = 0
async function loadSessions() {
  sessionsLoading.value = true
  try { sessions.value = await listSessions() } catch (cause) { ElMessage.error(errorMessage(cause, '会话列表加载失败')) }
  finally { sessionsLoading.value = false }
}
async function initialize() {
  const current = ++routeRequest
  chat.selectKnowledgeBase(kbId.value)
  pageError.value = ''; item.value = undefined
  try {
    const [knowledgeBase, allSessions] = await Promise.all([getKnowledgeBase(kbId.value), listSessions()])
    if (current !== routeRequest) return
    item.value = knowledgeBase; sessions.value = allSessions
    const requested = Number(route.query.session)
    if (requested && chat.sessionId !== requested) {
      const session = filteredSessions.value.find(value => value.id === requested)
      if (!session) throw new Error('会话不存在或不属于当前知识库')
      await chat.openSession(session)
    } else if (!requested && chat.sessionId) chat.clear()
  } catch (cause) { if (current === routeRequest) pageError.value = errorMessage(cause) }
}
async function send() {
  const value = question.value.trim()
  if (!value || chat.loading || chat.historyLoading || !item.value || !selectedModel.value?.configured) return
  const current = ++sendRequest
  const selectedKnowledgeBase = kbId.value
  question.value = ''
  awayFromBottom.value = false
  void jumpToLatest()
  await chat.send(selectedKnowledgeBase, value, provider.value)
  if (current !== sendRequest || chat.knowledgeBaseId !== selectedKnowledgeBase) return
  await loadSessions()
  if (current === sendRequest && chat.sessionId) await router.replace({ query: { session: chat.sessionId } })
}
async function newSession() { sendRequest++; chat.clear(); question.value = ''; awayFromBottom.value = false; await router.replace({ query: {} }) }
async function openSession(session: ChatSession) {
  sendRequest++
  chat.stop()
  await router.replace({ query: { session: session.id } })
}
function selectSession(value?: number) {
  const session = filteredSessions.value.find(item => item.id === value)
  if (session) openSession(session)
  else newSession()
}
async function remove(session: ChatSession) {
  try {
    await ElMessageBox.confirm(`删除会话“${session.title}”？`, '删除会话', { type: 'warning' })
    await deleteSession(session.id)
    if (chat.sessionId === session.id) await newSession()
    await loadSessions()
  } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause)) }
}
watch(() => [route.params.id, route.query.session], initialize, { immediate: true })
watch(kbId, id => { if (id) void loadExamples(id) }, { immediate: true })
watch(() => [chat.loading, ...chat.messages.map(message => `${message.content}|${message.citations?.length || 0}|${message.error || ''}`)], async () => {
  const element = messagesElement.value
  const nearBottom = !element || element.scrollHeight - element.scrollTop - element.clientHeight < 160
  await nextTick()
  if (nearBottom && messagesElement.value) messagesElement.value.scrollTop = messagesElement.value.scrollHeight
  trackScroll()
})
onBeforeUnmount(() => { routeRequest++; sendRequest++; chat.stop() })
</script>
<template>
  <AppLayout :context-name="item?.name">
    <div class="chat-page">
      <aside class="chat-sidebar">
        <router-link class="back-link" :to="`/knowledge/${kbId}`"><el-icon><ArrowLeft /></el-icon>{{ item?.name || '知识库' }}</router-link>
        <el-button :icon="Plus" @click="newSession">新建会话</el-button>
        <div class="chat-side-title">最近会话</div>
        <div v-loading="sessionsLoading" class="session-list">
          <div v-for="session in filteredSessions" :key="session.id" :class="['session-item', { active: chat.sessionId === session.id }]"><button :title="session.title" @click="openSession(session)">{{ session.title }}<small v-if="session.updatedAt || session.createdAt">{{ sessionTime(session.updatedAt || session.createdAt) }}</small></button><el-button text :icon="Delete" title="删除会话" aria-label="删除会话" :disabled="chat.loading && session.id === chat.sessionId" @click="remove(session)" /></div>
          <p v-if="!filteredSessions.length" class="muted">暂无会话</p>
        </div>
      </aside>
      <section class="chat-main">
        <header class="chat-header"><div><h1>{{ item?.name || '知识库问答' }}</h1><p v-if="item" class="chat-scope">{{ item.documentCount }} 份文档 · 回答附带原文引用，点击编号可查看出处</p></div><el-button class="chat-header-new" :icon="Plus" @click="newSession">新建会话</el-button></header>
        <div class="mobile-session-select"><el-select :model-value="chat.sessionId" placeholder="选择历史会话" clearable @change="selectSession"><el-option v-for="session in filteredSessions" :key="session.id" :value="session.id" :label="session.title" /></el-select></div>
        <el-alert v-if="pageError" :title="pageError" type="error" :closable="false" show-icon />
        <div ref="messagesElement" v-loading="chat.historyLoading" class="messages" @scroll="trackScroll">
          <div v-if="!chat.messages.length && !chat.historyLoading" class="chat-welcome"><span class="chat-welcome-icon"><el-icon><ChatDotRound /></el-icon></span><span class="section-kicker">基于你的知识库</span><h2>有什么想了解的？</h2><p>围绕「{{ item?.name || '当前知识库' }}」提问，<br />也可以先打个招呼。</p><div class="chat-welcome-note"><el-icon><DocumentChecked /></el-icon>知识库资料不足时，我会明确告诉你。</div></div>
          <div v-if="!chat.messages.length && !chat.historyLoading" class="question-examples" aria-label="提问建议"><button v-for="example in examples" :key="example" @click="question = example">{{ example }}</button><small>{{ faqExamples.length ? '来自本知识库的常见问题，' : '' }}点击填入后可编辑，答案取决于资料内容。</small></div>
          <div v-for="(message, index) in chat.messages" :key="message.id || index"><ChatMessage :message="message" :source-session-id="chat.sessionId" /></div>
          <div v-if="chat.loading" class="sr-only" role="status">正在生成回答</div>
        </div>
        <button v-if="awayFromBottom" class="latest-message" @click="jumpToLatest">↓ 回到最新</button>
        <form class="composer" @submit.prevent="send">
          <div class="model-controls">
            <label for="chat-model-provider">回答模型</label>
            <select id="chat-model-provider" v-model="provider" :disabled="chat.loading || modelsLoading || !!modelsError" aria-describedby="chat-model-description">
              <option v-for="option in modelOptions" :key="option.id" :value="option.id" :disabled="!option.configured">{{ option.label }}{{ option.configured ? '' : '（未配置）' }}</option>
            </select>
            <span class="model-name">{{ modelsLoading ? '加载中…' : selectedModel?.model }}</span>
          </div>
          <p v-if="modelsError" class="model-error" role="alert">{{ modelsError }} <button type="button" class="ghost-button" @click="loadModels">重试</button></p>
          <p v-else id="chat-model-description" class="model-description">{{ provider === 'cloud' ? '将问题、会话上下文和相关资料片段发送至云端 API，按服务商规则计费。' : provider === 'local' ? '默认在本机生成回答；繁忙时排队，不会自动切换到云端。' : '使用管理员配置的默认服务，可能连接远端模型；具体处理位置请向管理员确认。' }}</p>
          <textarea v-model="question" rows="2" maxlength="4000" placeholder="针对当前知识库提问，Shift + Enter 换行" aria-label="问题" :disabled="chat.historyLoading || !item" @keydown.enter.exact="event => { if (!event.isComposing) { event.preventDefault(); send() } }" />
          <p v-if="chat.loading && chat.status" class="model-description" role="status">{{ chat.status }}</p>
          <div class="composer-footer"><span>{{ question.length }} / 4000</span><div><el-button v-if="chat.loading" :icon="VideoPause" @click="chat.stop">停止</el-button><el-button type="primary" :icon="Promotion" :disabled="chat.loading || chat.historyLoading || !question.trim() || !item || !selectedModel?.configured" native-type="submit">发送</el-button></div></div>
        </form>
      </section>
    </div>
  </AppLayout>
</template>
<style scoped>
.model-controls { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; font-size: 13px; }
.model-controls label { font-weight: 600; }
.model-controls select { font: inherit; color: inherit; background: var(--el-fill-color-blank); border: 1px solid var(--el-border-color); border-radius: 6px; padding: 7px 10px; max-width: 100%; }
.model-controls select:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.model-controls select:disabled { cursor: not-allowed; opacity: .65; }
.model-name { overflow-wrap: anywhere; color: var(--el-text-color-regular); }
.model-description { margin: 8px 0 10px; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.6; }
.model-error { color: var(--el-color-danger); font-size: 13px; }
@media (max-width: 600px) { .model-name { flex-basis: 100%; } }
</style>
