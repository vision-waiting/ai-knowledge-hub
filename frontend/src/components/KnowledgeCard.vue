<script setup lang="ts">
import type { KnowledgeBase } from '@/types'
defineProps<{ item: KnowledgeBase }>()
defineEmits<{ open: [number]; edit: [KnowledgeBase]; remove: [KnowledgeBase] }>()
</script>
<template>
  <article class="knowledge-card" @click="$emit('open', item.id)">
    <div class="card-mark">KB</div><div class="card-main"><h3>{{ item.name }}</h3><p>{{ item.description || '暂无描述，上传资料开始构建知识库。' }}</p><div class="card-meta"><span>{{ item.documentCount }} 份文档</span><span>{{ item.updatedAt ? new Date(item.updatedAt).toLocaleDateString('zh-CN') : '刚刚更新' }}</span></div></div>
    <div class="card-actions"><button title="编辑" @click.stop="$emit('edit', item)">编辑</button><button class="danger" title="删除" @click.stop="$emit('remove', item)">删除</button></div>
  </article>
</template>

