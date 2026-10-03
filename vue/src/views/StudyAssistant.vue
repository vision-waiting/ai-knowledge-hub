<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Reading, Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import DocumentSourcePicker from '@/components/DocumentSourcePicker.vue'
import SaveNoteButton from '@/components/SaveNoteButton.vue'
import StudyQuiz from '@/components/StudyQuiz.vue'
import { useKnowledgeSources } from '@/composables/useKnowledgeSources'
import * as api from '@/api/intelligence'
import { http } from '@/api/http'
import { errorMessage, formatDate } from '@/utils/errors'

const route = useRoute()
const { knowledgeBases, knowledgeBaseId, documents, documentIds, loading, error: sourceError, reload } = useKnowledgeSources(Number(route.params.id) || undefined)
const count = ref(5); const pane = ref<'cards' | 'quiz' | 'mistakes'>('cards')
const studies = ref<api.StudySession[]>([]); const study = ref<api.StudySession>(); const grade = ref<api.StudyGrade>()
const quizAnswers = ref<number[]>([])
const mistakes = ref<api.StudyMistake[]>([]); const includeResolved = ref(false); const mistakeBusy = ref<number[]>([])
const generating = ref(false); const submitting = ref(false); const opening = ref(false); const historyLoading = ref(false); const mistakesLoading = ref(false); const error = ref('')
let generation = 0; let openRequest = 0; let historyRequest = 0; let mistakeRequest = 0; let disposed = false
let restoringKnowledgeBase = false
const busy = computed(() => generating.value || submitting.value || opening.value)
const canGenerate = computed(() => knowledgeBaseId.value && documentIds.value.length > 0 && !loading.value && !busy.value && count.value >= 3 && count.value <= 10)
const visibleStudies = computed(() => studies.value.filter(item => item.knowledgeBaseId === knowledgeBaseId.value))
const notesContent = computed(() => study.value?.cards.map(card => `## ${card.front}\n\n${card.back}`).join('\n\n') || '')
const addingPlan = ref(false); const planNotice = ref('')
async function addReviewPlan() {
  if (!study.value || addingPlan.value) return
  addingPlan.value = true; planNotice.value = ''
  try { await http.post(`/study/${study.value.id}/review-plan`); planNotice.value = '已加入复习计划，重复添加不会产生重复卡片。' }
  catch (cause) { error.value = errorMessage(cause) } finally { addingPlan.value = false }
}
async function loadHistory() {
  const current = ++historyRequest; historyLoading.value = true
  try { const data = await api.listStudies(); if (current === historyRequest && !disposed) studies.value = data }
  catch (cause) { if (!disposed && current === historyRequest) error.value = errorMessage(cause, '学习记录加载失败') }
  finally { if (current === historyRequest) historyLoading.value = false }
}
async function generate() {
  if (!canGenerate.value || !knowledgeBaseId.value) return
  const current = ++generation; generating.value = true; error.value = ''; grade.value = undefined
  historyRequest++; historyLoading.value = false
  try {
    const data = await api.createStudy(knowledgeBaseId.value, { documentIds: [...documentIds.value], questionCount: count.value })
    if (current !== generation || disposed) return
    study.value = data; quizAnswers.value = []; studies.value = [data, ...studies.value.filter(item => item.id !== data.id)]; pane.value = 'cards'
  } catch (cause) { if (current === generation && !disposed) error.value = errorMessage(cause, '学习内容生成失败，请重试') }
  finally { if (current === generation) generating.value = false }
}
async function openStudy(id: number) {
  const current = ++openRequest; opening.value = true; error.value = ''
  try {
    const [data, attempts] = await Promise.all([api.getStudy(id), api.listAttempts(id)])
    if (current !== openRequest || disposed) return
    restoringKnowledgeBase = true
    knowledgeBaseId.value = data.knowledgeBaseId
    restoringKnowledgeBase = false
    study.value = data; quizAnswers.value = []; grade.value = attempts[0]; pane.value = 'cards'
  } catch (cause) { if (!disposed && current === openRequest) error.value = errorMessage(cause, '学习记录加载失败') }
  finally { if (current === openRequest) opening.value = false }
}
async function submit(answers: number[]) {
  if (!study.value || busy.value) return
  const studyId = study.value.id; submitting.value = true; error.value = ''
  try {
    const data = await api.submitStudy(studyId, answers)
    if (!disposed && study.value?.id === studyId) { grade.value = data; void loadMistakes() }
  } catch (cause) { if (!disposed) error.value = errorMessage(cause, '提交失败，答案已保留，请重试') }
  finally { if (!disposed) submitting.value = false }
}
async function loadMistakes() {
  const current = ++mistakeRequest; mistakesLoading.value = true
  try { const data = await api.listMistakes(includeResolved.value); if (!disposed && current === mistakeRequest) mistakes.value = data }
  catch (cause) { if (!disposed && current === mistakeRequest) error.value = errorMessage(cause, '错题加载失败') }
  finally { if (current === mistakeRequest) mistakesLoading.value = false }
}
async function resolve(item: api.StudyMistake) {
  mistakeBusy.value.push(item.id)
  try { await api.resolveMistake(item.id, !item.resolved); item.resolved = !item.resolved; if (!includeResolved.value) mistakes.value = mistakes.value.filter(value => !value.resolved) }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, '更新错题失败') }
  finally { mistakeBusy.value = mistakeBusy.value.filter(id => id !== item.id) }
}
watch(knowledgeBaseId, () => {
  generation++; generating.value = false; study.value = undefined; grade.value = undefined; quizAnswers.value = []
  if (!restoringKnowledgeBase) { openRequest++; opening.value = false }
}, { flush: 'sync' })
watch(() => route.params.id, value => { if (value) knowledgeBaseId.value = Number(value) })
watch(includeResolved, loadMistakes)
watch(pane, value => { if (value === 'mistakes') void loadMistakes() })
onBeforeUnmount(() => { disposed = true; generation++; openRequest++; historyRequest++; mistakeRequest++ })
void loadHistory()
</script>
<template><AppLayout :context-id="knowledgeBaseId" :context-name="knowledgeBases.find(base => base.id === knowledgeBaseId)?.name">
  <div class="page-heading"><div><span class="section-kicker">从你的知识中学习</span><h1>学习助手</h1><p>把资料变成知识卡片和小测验，在练习中发现需要复习的内容。</p></div><el-button :disabled="busy" @click="pane = 'mistakes'">我的错题本</el-button></div>
  <div class="tools-layout">
    <aside class="tool-panel tool-settings">
      <h2>创建学习内容</h2><p class="muted tool-help">选择资料，生成一组卡片和测验。</p>
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" /><el-button v-if="sourceError" text @click="reload">重新加载资料</el-button>
      <DocumentSourcePicker v-model:knowledge-base-id="knowledgeBaseId" v-model="documentIds" :knowledge-bases="knowledgeBases" :documents="documents" :disabled="busy" :loading="loading" />
      <label class="tool-field-label" for="question-count">测验题数</label><select id="question-count" v-model.number="count" class="tool-select" :disabled="busy"><option v-for="amount in [3,4,5,6,7,8,9,10]" :key="amount" :value="amount">{{ amount }} 题</option></select>
      <el-button class="tool-primary" type="primary" :loading="generating" :disabled="!canGenerate" @click="generate">{{ generating ? '正在整理知识点…' : '生成卡片与测验' }}</el-button>
      <div class="source-heading"><strong>学习记录</strong><el-button text :icon="Refresh" :loading="historyLoading" :disabled="busy" @click="loadHistory" title="刷新学习记录" aria-label="刷新学习记录" /></div>
      <p v-if="!visibleStudies.length" class="muted">本知识库还没有学习记录。</p><div class="tool-history"><button v-for="item in visibleStudies" :key="item.id" :class="{active: study?.id === item.id}" :disabled="busy" @click="openStudy(item.id)"><span><strong>{{ item.title }}</strong><small>{{ formatDate(item.createdAt) }}</small></span></button></div>
    </aside>
    <section class="tool-results">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <div class="tool-tabs" role="group" aria-label="学习方式"><button :class="{active: pane === 'cards'}" :disabled="busy" @click="pane = 'cards'">知识卡片</button><button :class="{active: pane === 'quiz'}" :disabled="busy" @click="pane = 'quiz'">练习测验</button><button :class="{active: pane === 'mistakes'}" :disabled="busy" @click="pane = 'mistakes'">错题本</button></div>
      <div v-if="generating || opening" class="tool-panel tool-empty" role="status"><el-icon class="is-loading"><Refresh /></el-icon><h2>{{ opening ? '正在打开学习记录' : '正在生成学习内容' }}</h2><p>请稍候，完成后就可以开始学习。</p></div>
      <section v-else-if="pane === 'mistakes'" class="tool-panel">
        <div class="section-heading"><h2>我的错题本</h2><label class="tool-inline-check"><input v-model="includeResolved" type="checkbox" />包含已掌握</label></div>
        <p v-if="mistakesLoading" role="status" class="muted">正在加载错题…</p><div v-else-if="!mistakes.length" class="tool-empty"><h3>{{ includeResolved ? '还没有错题记录' : '暂无待复习错题' }}</h3><p>完成测验后，答错的题目会自动保存在这里。</p></div>
        <article v-for="item in mistakes" :key="item.id" class="quiz-question"><span class="section-kicker">{{ item.title }} · {{ item.resolved ? '已掌握' : '待复习' }}</span><h3>{{ item.prompt }}</h3><p>你的答案：{{ item.options[item.selectedIndex] }}</p><details><summary>查看答案与解析</summary><p><strong>{{ item.options[item.correctIndex] }}</strong></p><p>{{ item.explanation }}</p></details><div class="tool-actions"><el-button size="small" :loading="mistakeBusy.includes(item.id)" @click="resolve(item)">{{ item.resolved ? '重新复习' : '标记已掌握' }}</el-button><el-button size="small" text :disabled="busy" @click="openStudy(item.studyId)">回到学习记录</el-button></div></article>
      </section>
      <article v-else-if="study" class="tool-panel">
        <div class="tool-actions"><el-button :loading="addingPlan" @click="addReviewPlan">加入复习计划</el-button><router-link to="/review-plan">查看复习计划 →</router-link></div><p v-if="planNotice" role="status">{{ planNotice }}</p>
        <header class="tool-result-heading"><div><h2>{{ study.title }}</h2><p class="muted">{{ formatDate(study.createdAt) }} · {{ study.mode === 'OPENAI_COMPATIBLE' ? 'AI 生成' : '原文学习' }}</p></div><SaveNoteButton :title="study.title" :content="notesContent" /></header>
        <p v-if="study.sourceNotice" class="source-notice">{{ study.sourceNotice }}</p>
        <template v-if="pane === 'cards'"><p class="muted">先回忆，再展开查看内容。</p><div class="study-cards"><details v-for="(card, index) in study.cards" :key="`${study.id}-${index}`" class="study-card"><summary><span class="section-kicker">知识点 {{ index + 1 }}</span><strong>{{ card.front }}</strong><span class="card-reveal">点击展开 ↓</span></summary><p>{{ card.back }}</p></details></div><el-button type="primary" @click="pane = 'quiz'">开始测验</el-button></template>
        <StudyQuiz v-else :key="study.id" v-model="quizAnswers" :questions="study.questions" :grade="grade" :busy="submitting" @submit="submit" @retry="grade = undefined" />
      </article>
      <div v-else class="tool-panel tool-empty"><el-icon><Reading /></el-icon><h2>把阅读变成掌握</h2><p>选择左侧资料生成学习内容，或打开一条已保存的学习记录。</p></div>
    </section>
  </div>
</AppLayout></template>
