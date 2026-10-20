<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ChatDotRound, Document, Right, Collection } from '@element-plus/icons-vue'
import { listKnowledgeBases } from '@/api/knowledge'
import type { KnowledgeBase } from '@/types'
const items = ref<KnowledgeBase[]>([]); const loading = ref(true); const error = ref(false)
let disposed = false
async function load() {
  loading.value = true; error.value = false
  try {
    const result = await listKnowledgeBases()
    if (!disposed) items.value = [...result].sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || '')).slice(0, 3)
  } catch { if (!disposed) error.value = true }
  finally { if (!disposed) loading.value = false }
}
onMounted(load); onBeforeUnmount(() => { disposed = true })
</script>
<template>
  <section class="workspace-libraries">
    <div class="section-heading"><div><h2>继续探索知识<span class="section-subtitle">我的知识库</span></h2></div><router-link class="text-link" to="/knowledge">全部知识库 <el-icon><Right /></el-icon></router-link></div>
    <p v-if="loading" role="status" class="muted">正在加载知识库…</p>
    <p v-else-if="error" role="alert" class="inline-error">知识库加载失败。<button class="text-button" @click="load">重试</button></p>
    <div v-else-if="items.length" class="library-grid"><article v-for="(item, index) in items" :key="item.id" :class="['library-tile', `library-tone-${index}`]"><div class="library-tile-top"><span class="library-icon"><el-icon><Collection /></el-icon></span><span class="library-count">{{ item.documentCount }} 份文档</span></div><router-link :to="`/knowledge/${item.id}`"><h3>{{ item.name }}</h3></router-link><p>{{ item.description || '集中整理资料，让团队知识随时可用。' }}</p><footer><router-link :to="`/knowledge/${item.id}/documents`"><el-icon><Document /></el-icon>查看文档</router-link><router-link class="library-chat" :to="`/knowledge/${item.id}/chat`"><el-icon><ChatDotRound /></el-icon>开始问答</router-link></footer></article></div>
    <div v-else class="library-empty"><el-icon><Collection /></el-icon><div><h3>创建你的第一个知识库</h3><p>上传文档后，即可围绕资料提问并追溯原文。</p></div><router-link class="primary-button" to="/knowledge?create=1">创建知识库</router-link></div>
  </section>
</template>
