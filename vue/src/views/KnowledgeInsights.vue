<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Upload } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { getKnowledgeBase } from '@/api/knowledge'
import { knowledgeInsights } from '@/api/dashboard'
import { errorMessage, formatDate } from '@/utils/errors'
import type { KnowledgeBase, KnowledgeInsights } from '@/types'

const route = useRoute(), router = useRouter()
const base = ref<KnowledgeBase>(), insights = ref<KnowledgeInsights>(), loading = ref(false), error = ref('')
const days = ref(30)
const id = computed(() => Number(route.params.id))
const canManage = computed(() => base.value?.canManage)
let request = 0
async function load() {
  const current = ++request
  loading.value = true; error.value = ''
  try {
    const [detail, data] = await Promise.all([base.value || getKnowledgeBase(id.value), knowledgeInsights(id.value, days.value)])
    if (current === request) { base.value = detail; insights.value = data }
  } catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally { if (current === request) loading.value = false }
}
function toUpload() { router.push(`/knowledge/${id.value}/documents`) }
watch([id, days], load, { immediate: true })
onBeforeUnmount(() => { request++ })
</script>
<template>
  <AppLayout :context-name="base?.name">
    <div class="page-heading"><div><router-link :to="`/knowledge/${id}`" class="back-link"><el-icon><ArrowLeft /></el-icon>返回知识库</router-link><h1>知识缺口分析</h1><p v-if="base">最近 {{ insights?.days || days }} 天「资料不足」类回答的汇总，用于发现需要补充的问题。只统计至少两名用户提问过的内容。</p></div><div v-if="canManage" class="heading-actions"><el-button :icon="Upload" @click="toUpload">上传补充资料</el-button></div></div>
    <div v-if="error" role="alert"><el-alert :title="error" type="error" :closable="false" show-icon /><el-button @click="load">重试</el-button></div>
    <el-skeleton v-else-if="loading" :rows="8" />
    <template v-else-if="insights">
      <div class="toolbar"><el-select v-model="days" aria-label="统计周期"><el-option :value="7" label="最近 7 天" /><el-option :value="30" label="最近 30 天" /><el-option :value="90" label="最近 90 天" /></el-select><span class="result-count">{{ insights.gaps.length }} 个缺口{{ insights.hiddenGapCount > 0 ? `（隐藏 ${insights.hiddenGapCount} 个单次提问）` : '' }}</span></div>
      <div class="metrics-row insight-metrics"><div><span>问答总数</span><strong>{{ insights.answeredCount }}</strong></div><div><span>资料不足</span><strong :class="{ 'failed-count': insights.insufficientCount > 0 }">{{ insights.insufficientCount }}</strong></div><div><span>有帮助</span><strong>{{ insights.helpfulCount }}</strong></div><div><span>需改进</span><strong>{{ insights.notHelpfulCount }}</strong></div><div><span>待处理反馈</span><strong>{{ insights.openIssueCount }}</strong></div></div>
      <div v-if="!insights.gaps.length && !insights.insufficientCount" class="scope-notice"><strong>太好了！</strong><p>最近 {{ insights.days }} 天没有「资料不足」类回答，知识库覆盖良好。</p></div>
      <div v-else-if="!insights.gaps.length && insights.insufficientCount" class="scope-notice"><strong>暂无可统计缺口</strong><p>有 {{ insights.insufficientCount }} 次「资料不足」回答，但都只被单个用户提问过，不符合展示条件（至少两名用户）。</p></div>
      <ul v-else class="gap-list"><li v-for="(gap, i) in insights.gaps" :key="i" class="gap-item"><div class="gap-question">{{ gap.question }}</div><div class="gap-meta"><span>{{ gap.askCount }} 次提问</span><span>{{ gap.userCount }} 名用户</span><span>最后提问：{{ formatDate(gap.lastAskedAt) }}</span></div></li></ul>
    </template>
  </AppLayout>
</template>
<style scoped>
.insight-metrics { grid-template-columns: repeat(5, minmax(0, 1fr)); }
@media (max-width: 800px) { .insight-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.gap-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
.gap-item { border: 1px solid var(--line); border-radius: 10px; padding: 18px 20px; background: #fff; }
.gap-question { font-size: 15px; line-height: 1.7; margin-bottom: 10px; color: var(--el-text-color-regular); }
.gap-meta { display: flex; flex-wrap: wrap; gap: 10px 18px; font-size: 12px; color: var(--muted); }
.gap-meta span { display: flex; align-items: center; gap: 4px; }
</style>
