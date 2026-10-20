<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ChatMessage as Message, Citation } from '@/types'
import CitationCard from './CitationCard.vue'
import SourceDrawer from './SourceDrawer.vue'
import { renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/errors'
import SaveNoteButton from '@/components/SaveNoteButton.vue'
import { feedbackMessage } from '@/api/chat'
const props = defineProps<{ message: Message; sourceSessionId?: number }>()
const selectedCitation = ref<Citation>()
const copyState = ref('')
async function copyAnswer() {
  copyState.value = ''
  const content = props.message.content
  try {
    await navigator.clipboard.writeText(content)
    if (props.message.content === content) copyState.value = '已复制'
  } catch { copyState.value = '复制失败，请选中正文手动复制。' }
}
watch(() => props.message.content, () => { copyState.value = '' })
watch(() => props.message, () => { selectedCitation.value = undefined })
const feedbackBusy = ref(false); const editing = ref(false); const reason = ref(''); const saved = ref<string>(); const error = ref('')
watch(() => props.message, message => { saved.value = message.feedback; reason.value = message.feedbackReason || ''; editing.value = false }, { immediate: true })
async function feedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
  if (!props.message.id || feedbackBusy.value) return
  const id = props.message.id
  feedbackBusy.value = true; error.value = ''
  try {
    await feedbackMessage(id, value, value === 'NOT_HELPFUL' ? reason.value.trim() : undefined)
    if (props.message.id === id) { saved.value = value; editing.value = false }
  } catch (cause) { if (props.message.id === id) error.value = errorMessage(cause, '反馈提交失败') }
  finally { feedbackBusy.value = false }
}
function openInlineCitation(event: MouseEvent) {
  const target = (event.target as HTMLElement).closest<HTMLElement>('[data-cite]')
  if (!target) return
  const citation = props.message.citations?.find(item => item.index === Number(target.dataset.cite))
  if (citation) selectedCitation.value = citation
}
function documentName(documentId: number) {
  return props.message.citations?.find(item => item.documentId === documentId)?.documentName || `文档 #${documentId}`
}
const modeLabel = (mode: string) => ({ LOCAL_DEMO: '原文检索', LOCAL_MODEL: '本地模型', CLOUD_API: '云端 API', OPENAI_COMPATIBLE: 'AI 回答', INSUFFICIENT_CONTEXT: '资料不足', SMALL_TALK: '日常对话' }[mode] || mode)
</script>
<template>
  <div :class="['message-row', props.message.role.toLowerCase()]">
    <div v-if="props.message.role === 'ASSISTANT'" class="avatar ai-avatar">AI</div>
    <div class="message-bubble">
      <div v-if="props.message.role === 'ASSISTANT'" class="message-label">AI 助手 <small v-if="props.message.mode">· {{ modeLabel(props.message.mode) }}<template v-if="props.message.model"> · {{ props.message.model }}</template></small></div>
      <span v-if="props.message.role === 'ASSISTANT' && !props.message.content && !props.message.error" class="typing-dots" aria-label="正在生成回答"><i /><i /><i /></span>
      <div class="markdown-body" v-html="renderMarkdown(props.message.content, props.message.citations?.length)" @click="openInlineCitation"></div>
      <p v-if="props.message.error" class="message-error" role="alert">{{ props.message.error }}</p>
      <section v-if="props.message.citations?.length" class="answer-sources" aria-label="回答依据"><h3>参考资料 · {{ props.message.citations.length }}</h3><CitationCard v-for="citation in props.message.citations" :key="`${citation.index}-${citation.chunkId}`" :citation="citation" @open="selectedCitation = $event" /></section>
      <details v-if="props.message.retrieval" class="retrieval-trace">
        <summary>检索过程 · {{ props.message.retrieval.chunks.length }} 个片段 · {{ props.message.retrieval.latencyMs }} ms<span :class="['trace-verdict', { weak: !props.message.retrieval.sufficient }]">{{ props.message.retrieval.sufficient ? '达到检索阈值' : '未达到检索阈值' }}</span></summary>
        <p class="trace-query"><span>检索语句</span>{{ props.message.retrieval.rewrittenQuery }}</p>
        <ol v-if="props.message.retrieval.chunks.length" class="trace-chunks">
          <li v-for="chunk in props.message.retrieval.chunks" :key="chunk.chunkId">
            <div class="trace-chunk-head"><strong>{{ documentName(chunk.documentId) }}</strong><small v-if="chunk.pageNumber">第 {{ chunk.pageNumber }} 页</small><b>{{ chunk.hybridScore.toFixed(3) }}</b></div>
            <div class="trace-bar" :style="{ '--score': `${Math.min(100, Math.max(0, chunk.hybridScore * 100))}%` }" aria-hidden="true" />
            <small class="trace-scores">向量 {{ chunk.vectorScore.toFixed(3) }} · 关键词 {{ chunk.keywordScore.toFixed(3) }} · 混合 {{ chunk.hybridScore.toFixed(3) }}</small>
          </li>
        </ol>
        <p v-else class="muted">没有片段通过相关性筛选，因此未调用模型生成回答。</p>
      </details>
      <div v-if="props.message.role === 'ASSISTANT' && props.message.content.trim()" class="answer-toolbar">
        <div class="answer-actions"><button class="ghost-button" @click="copyAnswer">复制回答</button><SaveNoteButton :content="props.message.content" :source-session-id="props.sourceSessionId" /><span v-if="copyState" role="status" class="muted">{{ copyState }}</span></div>
        <div v-if="props.message.id && !props.message.error" class="message-feedback">
          <span>回答有帮助吗？</span>
          <button :disabled="feedbackBusy" :aria-pressed="saved === 'HELPFUL'" @click="feedback('HELPFUL')">有帮助</button>
          <button data-test="negative-feedback" :disabled="feedbackBusy" :aria-pressed="saved === 'NOT_HELPFUL'" @click="editing = !editing">需改进</button>
          <span v-if="saved" role="status">已提交{{ saved === 'HELPFUL' ? '：有帮助' : '：需改进' }}</span>
        </div>
      </div>
      <div v-if="editing" class="feedback-form">
        <p>提交后，知识库维护者可查看这一条问题、回答和改进原因，用于补充资料。</p>
        <textarea v-model="reason" aria-label="改进原因" maxlength="200" rows="2" placeholder="例如：资料过时、引用不正确或缺少信息" />
        <div><button data-test="submit-feedback" :disabled="feedbackBusy" @click="feedback('NOT_HELPFUL')">提交反馈</button><button :disabled="feedbackBusy" @click="editing = false">取消</button></div>
      </div>
      <p v-if="error" class="message-error" role="alert">{{ error }}</p>
    </div>
    <div v-if="props.message.role === 'USER'" class="avatar user-avatar">我</div>
  </div>
  <SourceDrawer :document-id="selectedCitation?.documentId" :citation="selectedCitation" @close="selectedCitation = undefined" />
</template>
<style scoped>
.answer-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px 16px; border-top: 1px solid var(--line); margin-top: 16px; padding-top: 12px; }
.answer-toolbar .answer-actions { border: 0; margin: 0; padding: 0; }
.message-feedback { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 12px; color: var(--muted); }
.message-feedback button, .feedback-form button { cursor: pointer; border: 1px solid var(--line); background: #fff; color: var(--el-text-color-regular); border-radius: 6px; padding: 5px 10px; font-size: 12px; }
.message-feedback button[aria-pressed=true] { border-color: var(--green); color: var(--green); background: #edf6f2; }
.message-feedback button:disabled, .feedback-form button:disabled { opacity: .5; cursor: wait; }
.feedback-form { margin-top: 12px; padding: 12px; border-radius: 8px; background: #f6f9f8; font-size: 12px; color: var(--muted); }
.feedback-form p { margin: 0; }
.feedback-form textarea { display: block; box-sizing: border-box; width: 100%; margin: 8px 0; padding: 8px; border: 1px solid var(--line); border-radius: 6px; font: inherit; font-size: 13px; }
.feedback-form div { display: flex; gap: 8px; }
.typing-dots { display: inline-flex; gap: 5px; padding: 6px 0; }
.typing-dots i { width: 7px; height: 7px; border-radius: 50%; background: #91bdb4; animation: typing 1.2s infinite ease-in-out; }
.typing-dots i:nth-child(2) { animation-delay: .15s; } .typing-dots i:nth-child(3) { animation-delay: .3s; }
@keyframes typing { 0%, 80%, 100% { opacity: .35; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
.retrieval-trace { margin-top: 14px; font-size: 12px; color: var(--muted); }
.retrieval-trace summary { cursor: pointer; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--el-text-color-regular); }
.trace-verdict { padding: 1px 8px; border-radius: 10px; background: #edf6f2; color: var(--green); }
.trace-verdict.weak { background: #fdf3e6; color: #a5641c; }
.trace-query { margin: 10px 0; padding: 8px 10px; border-radius: 6px; background: #f6f9f8; color: var(--el-text-color-regular); line-height: 1.6; }
.trace-query span { margin-right: 8px; color: var(--muted); }
.trace-chunks { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 10px; }
.trace-chunk-head { display: flex; align-items: baseline; gap: 8px; }
.trace-chunk-head strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--el-text-color-regular); font-weight: 500; }
.trace-chunk-head b { margin-left: auto; color: var(--green); font-variant-numeric: tabular-nums; }
.trace-bar { height: 4px; margin: 5px 0; border-radius: 2px; background: linear-gradient(90deg, var(--green) var(--score), #e6eeea var(--score)); }
.trace-scores { font-variant-numeric: tabular-nums; }
</style>
