<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ChatDotRound, FolderOpened } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import DocumentStatus from '@/components/DocumentStatus.vue'
import KnowledgeSharing from '@/components/KnowledgeSharing.vue'
import FollowKnowledgeBase from '@/components/FollowKnowledgeBase.vue'
import { useAuthStore } from '@/stores/auth'
import { getKnowledgeBase } from '@/api/knowledge'
import { listDocuments } from '@/api/documents'
import { errorMessage, formatDate } from '@/utils/errors'
import type { KnowledgeBase, DocumentItem } from '@/types'

const auth = useAuthStore(), route = useRoute()
const item = ref<KnowledgeBase>(), docs = ref<DocumentItem[]>([]), loading = ref(false), error = ref('')
const ready = computed(() => docs.value.filter(doc => doc.status === 'SUCCESS').length)
const failed = computed(() => docs.value.filter(doc => doc.status === 'FAILED').length)
const canWrite = computed(() => item.value?.canWrite ?? (auth.isAdmin || auth.user?.id === item.value?.ownerId))
let request = 0
async function load() {
  const current = ++request
  loading.value = true; error.value = ''; item.value = undefined; docs.value = []
  try {
    const [base, documents] = await Promise.all([getKnowledgeBase(Number(route.params.id)), listDocuments(Number(route.params.id))])
    if (current === request) { item.value = base; docs.value = documents }
  } catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally { if (current === request) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { request++ })
</script>
<template>
  <AppLayout :context-name="item?.name">
    <div class="page-heading"><div><router-link to="/knowledge" class="back-link"><el-icon><ArrowLeft /></el-icon>返回知识库</router-link><h1>{{ item?.name || '知识库详情' }}</h1><p v-if="item?.shared">共享知识库 · 发布者：{{ item.ownerNickname || item.ownerUsername || `用户 #${item.ownerId}` }}</p><p v-if="item?.description">{{ item.description }}</p></div><div v-if="item" class="heading-actions"><FollowKnowledgeBase :knowledge-base-id="item.id" /><router-link class="ghost-button" :to="`/knowledge/${item.id}/documents`"><el-icon><FolderOpened /></el-icon>{{ canWrite ? '管理文档' : '查看文档' }}</router-link><router-link class="primary-button" :to="`/knowledge/${item.id}/chat`"><el-icon><ChatDotRound /></el-icon>开始问答</router-link></div></div>
    <div v-if="error" role="alert"><el-alert :title="error" type="error" :closable="false" show-icon /><el-button @click="load">重试加载</el-button></div>
    <el-skeleton v-else-if="loading" :rows="6" />
    <template v-else-if="item">
      <KnowledgeSharing :key="item.id" :item="item" />
      <div class="metrics-row knowledge-metrics"><router-link :to="`/knowledge/${item.id}/documents`"><span>全部文档</span><strong>{{ docs.length }}</strong></router-link><router-link :to="`/knowledge/${item.id}/documents?status=SUCCESS`"><span>可检索文档</span><strong>{{ ready }}</strong></router-link><router-link :to="`/knowledge/${item.id}/documents?status=PROCESSING`"><span>处理中</span><strong>{{ docs.length - ready - failed }}</strong></router-link><router-link :to="`/knowledge/${item.id}/documents?status=FAILED`"><span>处理失败 →</span><strong :class="{ 'failed-count': failed > 0 }">{{ failed }}</strong></router-link></div>
      <div v-if="!ready" class="scope-notice empty-library-notice"><strong>暂无可检索资料</strong><p>{{ canWrite ? '上传资料或等待文档处理完成后，即可基于内容提问。' : '请等待维护者补充资料或文档处理完成。' }}</p><router-link v-if="canWrite" class="text-link" :to="`/knowledge/${item.id}/documents`">上传资料 / 查看处理状态 →</router-link></div>
      <div class="knowledge-tool-links"><router-link :to="`/search?knowledgeBaseId=${item.id}`">搜索库内资料 →</router-link><template v-if="item.canManage"><router-link :to="`/knowledge/${item.id}/insights`">知识缺口分析 →</router-link><router-link :to="`/knowledge/${item.id}/retrieval`">检索实验室 →</router-link></template><router-link :to="`/knowledge/${item.id}/analysis`">文档总结与对比 →</router-link><router-link :to="`/knowledge/${item.id}/study`">生成卡片与测验 →</router-link><router-link :to="`/workspace?knowledgeBaseId=${item.id}`">知识内容与维护 →</router-link></div>
      <div class="section-heading"><h2>最近文档</h2><span class="muted">创建于 {{ formatDate(item.createdAt) }}</span></div>
      <p class="table-scroll-hint">左右滑动查看完整信息。</p><div class="document-table-scroll" role="region" aria-label="最近文档" tabindex="0"><el-table :data="docs.slice(0, 8)" empty-text="暂无文档"><el-table-column prop="fileName" label="文档" min-width="220" /><el-table-column label="状态" width="150"><template #default="{ row }"><DocumentStatus :status="row.status" :error-message="row.errorMessage" /></template></el-table-column><el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip /></el-table></div>
    </template>
  </AppLayout>
</template>
