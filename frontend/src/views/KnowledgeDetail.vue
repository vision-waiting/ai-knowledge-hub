<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import type { KnowledgeBase } from '@/types'
import { getKnowledgeBase } from '@/api/knowledge'
const route = useRoute(); const router = useRouter(); const item = ref<KnowledgeBase>()
onMounted(async () => { item.value = await getKnowledgeBase(Number(route.params.id)) })
</script>
<template><AppLayout><div class="detail-hero"><div><button class="back-link" @click="router.push('/knowledge')">← 返回知识库</button><p class="eyebrow">KNOWLEDGE BASE</p><h1>{{ item?.name || '知识库详情' }}</h1><p>{{ item?.description || '管理文档、查看处理状态并开始 AI 问答。' }}</p></div><div class="detail-actions"><button class="primary-button" @click="router.push(`/knowledge/${route.params.id}/chat`)">✦ 开始问答</button><button class="ghost-button" @click="router.push(`/knowledge/${route.params.id}/documents`)">管理文档</button></div></div><div class="detail-cards"><div><span>文档数量</span><strong>{{ item?.documentCount || 0 }}</strong></div><div><span>检索模式</span><strong>混合检索</strong></div><div><span>回答模式</span><strong>本地演示</strong></div></div><div class="panel detail-intro"><p class="eyebrow">NEXT STEP</p><h2>把资料变成可对话的知识</h2><p>上传 PDF、Word、PPT、TXT 或 Markdown，系统会自动解析、切块、建立索引，并为回答提供文档和页码引用。</p><button class="text-button" @click="router.push(`/knowledge/${route.params.id}/documents`)">上传第一份文档 →</button></div></AppLayout></template>
