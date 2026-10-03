<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeBase } from '@/types'
import { Delete, Edit, Collection, Right } from '@element-plus/icons-vue'
defineProps<{ item: KnowledgeBase }>()
const auth = useAuthStore()
defineEmits<{ open: [number]; edit: [KnowledgeBase]; remove: [KnowledgeBase] }>()
</script>
<template>
  <article class="knowledge-card" tabindex="0" @click="$emit('open', item.id)" @keydown.enter.self="$emit('open', item.id)">
    <div class="card-mark"><el-icon><Collection /></el-icon></div><div class="card-main"><h3>{{ item.name }}</h3><span class="card-visibility">{{ item.shared ? '共享知识库' : '个人知识库' }}</span><p>{{ item.description || '暂无描述' }}</p><div v-if="item.shared || auth.isAdmin" class="card-creator">{{ item.shared ? '发布者' : '创建者' }}：{{ item.ownerNickname ? `${item.ownerNickname}（${item.ownerUsername || item.ownerId}）` : (item.ownerUsername || `用户 #${item.ownerId}`) }}</div><div class="card-meta"><span>{{ item.documentCount }} 份文档</span><span>{{ item.updatedAt ? new Date(item.updatedAt).toLocaleDateString('zh-CN') : '-' }}</span></div></div>
    <footer class="card-footer" @click.stop @keydown.stop><router-link :to="`/knowledge/${item.id}/chat`">开始问答 →</router-link><details v-if="item.canManage ?? (auth.isAdmin || auth.user?.id === item.ownerId)" class="card-menu"><summary :aria-label="`${item.name}的更多操作`">更多</summary><div class="card-menu-actions"><el-button text :icon="Edit" title="编辑知识库" aria-label="编辑知识库" @click.stop="$emit('edit', item)" /><el-button text type="danger" :icon="Delete" title="删除知识库" aria-label="删除知识库" @click.stop="$emit('remove', item)" /></div></details><span v-else class="muted">只读访问</span></footer>
  </article>
</template>


<style scoped>
.card-visibility { display: inline-block; color: var(--muted); font-size: 12px; margin-bottom: 8px; }
.card-creator { color: var(--muted); font-size: 12px; margin-bottom: 10px; overflow-wrap: anywhere; }
</style>
