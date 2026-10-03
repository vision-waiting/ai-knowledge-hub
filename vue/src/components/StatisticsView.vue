<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { ChatDotRound, Collection, Document, Warning } from '@element-plus/icons-vue'
const DataChart = defineAsyncComponent(() => import('./DataChart.vue'))
import type { DashboardOverview } from '@/types'

const props = withDefaults(defineProps<{ overview?: DashboardOverview; admin?: boolean; section?: 'all' | 'summary' | 'details' }>(), { section: 'all' })
const metrics = computed(() => [
  { label: '知识库', value: props.overview?.knowledgeBaseCount, icon: Collection, tone: 'blue', to: '/knowledge' },
  { label: '文档', value: props.overview?.documentCount, icon: Document, tone: 'green', to: '/knowledge' },
  { label: '会话', value: props.overview?.chatCount, icon: ChatDotRound, tone: 'blue', to: '/chat/history' },
  { label: '处理失败', value: props.overview?.failedDocumentCount, icon: Warning, tone: 'red', to: '' },
])
const trends = computed(() => ({
  color: ['#23846b', '#3873d4'], tooltip: { trigger: 'axis' }, legend: { bottom: 0 }, grid: { top: 24, right: 18, bottom: 55, left: 40, containLabel: true },
  xAxis: { type: 'category', data: props.overview?.weeklyQuestions?.map(item => item.date.slice(5)) || [], axisLine: { lineStyle: { color: '#ced4da' } }, axisLabel: { color: '#667078' } },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf0f1' } } },
  series: [{ name: '问答', type: 'line', data: props.overview?.weeklyQuestions?.map(item => item.count) || [], symbolSize: 7 }, { name: '上传', type: 'bar', data: props.overview?.weeklyUploads?.map(item => item.count) || [], barMaxWidth: 22 }],
}))
const types = computed(() => ({ color: ['#23846b', '#3873d4', '#cf5c61', '#779c42', '#818992'], tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['40%', '65%'], center: ['50%', '43%'], label: { formatter: '{b}: {c}' }, data: props.overview?.documentTypes?.map(item => ({ name: item.name.toUpperCase(), value: item.count })) || [] }] }))
const ranking = computed(() => ({ color: ['#23846b'], tooltip: { trigger: 'axis' }, grid: { top: 8, right: 30, bottom: 28, left: 10, containLabel: true }, xAxis: { type: 'value', minInterval: 1 }, yAxis: { type: 'category', inverse: true, data: props.overview?.knowledgeBaseRanking?.map(item => item.name) || [], axisLabel: { width: 120, overflow: 'truncate' } }, series: [{ type: 'bar', barMaxWidth: 22, data: props.overview?.knowledgeBaseRanking?.map(item => item.count) || [] }] }))
</script>
<template>
  <div v-if="section !== 'details'" class="stats-grid"><component :is="metric.to && !admin ? 'router-link' : 'div'" v-for="metric in metrics" :key="metric.label" :to="metric.to && !admin ? metric.to : undefined" :class="['stat-card', metric.tone, { 'stat-link': metric.to && !admin }]"><el-icon class="stat-icon"><component :is="metric.icon" /></el-icon><div><small>{{ metric.label }}</small><strong>{{ metric.value ?? '—' }}</strong></div></component></div>
  <template v-if="section !== 'summary'">
  <div class="metrics-row secondary-metrics"><div><span>处理中</span><strong>{{ overview?.processingCount ?? '-' }}</strong></div><div><span>今日问答</span><strong>{{ overview?.todayQuestionCount ?? '-' }}</strong></div><div><span>文档片段</span><strong>{{ overview?.chunkCount ?? '-' }}</strong></div><div><span>{{ admin ? '用户' : 'AI 调用' }}</span><strong>{{ admin ? overview?.userCount ?? '-' : overview?.aiCallCount ?? '-' }}</strong></div></div>
  <div class="analytics-grid"><section class="analytics-section"><div class="section-heading"><h2>最近 7 天</h2><span>问答与上传</span></div><DataChart v-if="overview?.weeklyQuestions?.length" :option="trends" label="最近七天问答和文档上传数量" /><el-empty v-else description="暂无趋势数据" /></section><section class="analytics-section"><div class="section-heading"><h2>文档类型</h2></div><DataChart v-if="overview?.documentTypes?.length" :option="types" label="按格式统计的文档数量" /><el-empty v-else description="暂无文档数据" /></section></div>
  <section class="analytics-section"><div class="section-heading"><h2>知识库文档数量</h2></div><DataChart v-if="overview?.knowledgeBaseRanking?.length" :option="ranking" label="知识库文档数量排行" /><el-empty v-else description="暂无知识库数据" /></section>
  </template>
</template>
