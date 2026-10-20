<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Refresh, ChatDotRound, Reading, Notebook, Right, Search, Document } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import StatisticsView from '@/components/StatisticsView.vue'
import WorkspaceLibraries from '@/components/WorkspaceLibraries.vue'
import KnowledgeOrbit from '@/components/KnowledgeOrbit.vue'
import { overview as getOverview, recentActivity } from '@/api/dashboard'
import { errorMessage } from '@/utils/errors'
import type { DashboardOverview, RecentActivity } from '@/types'
const router = useRouter(), overview = ref<DashboardOverview>(), recent = ref<RecentActivity>(), loading = ref(false), error = ref(''), detailsOpen = ref(false)

async function load() {
  loading.value = true; error.value = ''
  try { [overview.value, recent.value] = await Promise.all([getOverview(), recentActivity()]) }
  catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
function formatTime(iso: string | undefined) {
  if (!iso) return ''
  const date = new Date(iso), now = new Date()
  const diff = now.getTime() - date.getTime(), day = 86400000
  if (diff < day && date.toDateString() === now.toDateString()) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
onMounted(load)
</script>
<template>
  <AppLayout>
    <div class="page-heading dashboard-heading"><div><h1>知识工作台</h1></div><div class="heading-actions"><el-button :icon="Refresh" :loading="loading" title="刷新统计" aria-label="刷新统计" @click="load" /><el-button type="primary" :icon="Plus" @click="router.push('/knowledge?create=1')">新建知识库</el-button></div></div>
    <section class="studio-hero" aria-labelledby="studio-title">
      <div class="studio-hero-copy">
        <h2 id="studio-title">从一份资料，<br />到<span>无限可能。</span></h2>
        <p>收集灵感，连接知识。<br />让每一次提问，都成为理解世界的新起点。</p>
        <router-link class="studio-cta" to="/knowledge">探索我的知识库 <el-icon><Right /></el-icon></router-link>
      </div>
      <KnowledgeOrbit />
    </section>
    <nav class="studio-shortcuts" aria-label="工作台快捷入口">
      <router-link to="/search"><span class="shortcut-icon"><el-icon><Search /></el-icon></span><div><strong>寻找一个答案</strong><small>全文搜索，让线索浮现</small></div><el-icon class="shortcut-arrow" aria-hidden="true"><Right /></el-icon></router-link>
      <router-link to="/analysis"><span class="shortcut-icon"><el-icon><Document /></el-icon></span><div><strong>读懂一份资料</strong><small>提炼重点，发现内容关联</small></div><el-icon class="shortcut-arrow" aria-hidden="true"><Right /></el-icon></router-link>
      <router-link to="/study"><span class="shortcut-icon"><el-icon><Reading /></el-icon></span><div><strong>掌握一个新知</strong><small>知识卡片，让理解更深一步</small></div><el-icon class="shortcut-arrow" aria-hidden="true"><Right /></el-icon></router-link>
    </nav>
    <div v-if="error" class="dashboard-error" role="alert"><el-alert :title="error" type="error" :closable="false" show-icon /><p v-if="overview" class="muted">刷新失败，以下统计为上次成功加载的数据。</p><p v-else class="muted">统计暂不可用，仍可进入知识库继续工作。</p><button class="text-button" @click="load">重新加载统计</button></div>
    <WorkspaceLibraries />
    <section class="dashboard-metrics" aria-label="账号统计" :aria-busy="loading"><el-skeleton v-if="loading && !overview" :rows="2" /><StatisticsView v-else :overview="overview" section="summary" /></section>
    <section class="continue-work"><div class="section-heading"><div><h2>最近活动</h2></div><span>继续上次的对话与文档</span></div>
      <div v-if="!recent || (!recent.sessions.length && !recent.documents.length && !recent.dueReviewCount)" class="scope-notice"><strong>暂无最近活动</strong><p>开始提问、上传文档或复习卡片后，这里会显示你的最近活动。</p></div>
      <template v-else>
        <div v-if="recent.dueReviewCount > 0" class="activity-banner"><el-icon><Reading /></el-icon><div><strong>{{ recent.dueReviewCount }} 张卡片待复习</strong><p>知识需要定期巩固，花几分钟回顾一下吧。</p></div><router-link class="primary-button" to="/review-plan">开始复习</router-link></div>
        <div class="activity-columns"><div v-if="recent.sessions.length" class="activity-section"><h3>最近会话</h3><div class="activity-list"><router-link v-for="session in recent.sessions" :key="session.id" :to="`/knowledge/${session.knowledgeBaseId}/chat?session=${session.id}`" class="activity-item"><div class="activity-icon session-icon"><el-icon><ChatDotRound /></el-icon></div><div class="activity-main"><strong>{{ session.title }}</strong><p>{{ session.knowledgeBaseName }} · {{ formatTime(session.updatedAt) }}</p></div></router-link></div></div>
        <div v-if="recent.documents.length" class="activity-section"><h3>最近文档</h3><div class="activity-list"><router-link v-for="doc in recent.documents" :key="doc.id" :to="doc.status === 'FAILED' ? `/knowledge/${doc.knowledgeBaseId}/documents?status=FAILED` : `/knowledge/${doc.knowledgeBaseId}/documents`" class="activity-item"><div :class="['activity-icon', 'doc-icon', { failed: doc.status === 'FAILED' }]"><el-icon><Notebook /></el-icon></div><div class="activity-main"><strong>{{ doc.fileName }}</strong><p>{{ doc.knowledgeBaseName }} · {{ formatTime(doc.createdAt) }}</p></div><span v-if="doc.status === 'FAILED'" class="activity-status">处理失败</span></router-link></div></div></div>
      </template>
    </section>
    <details v-if="overview" class="dashboard-details" @toggle="detailsOpen = ($event.target as HTMLDetailsElement).open"><summary>详细统计 <span>问答趋势、文档分布与知识库排行</span></summary><StatisticsView v-if="detailsOpen" :overview="overview" section="details" /></details>
  </AppLayout>
</template>
