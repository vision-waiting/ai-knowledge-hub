<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { listSessions, deleteSession } from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledge'
import { errorMessage, formatDate } from '@/utils/errors'
import type { ChatSession, KnowledgeBase } from '@/types'

const router = useRouter(); const sessions = ref<ChatSession[]>([]); const knowledgeBases = ref<KnowledgeBase[]>([])
const keyword = ref(''); const selectedKb = ref<number>(); const loading = ref(false); const error = ref('')
const filtered = computed(() => sessions.value.filter(session => (!selectedKb.value || session.knowledgeBaseId === selectedKb.value) && session.title.toLowerCase().includes(keyword.value.toLowerCase())))
const names = computed(() => new Map(knowledgeBases.value.map(item => [item.id, item.name])))
async function load() {
  loading.value = true; error.value = ''
  try { [sessions.value, knowledgeBases.value] = await Promise.all([listSessions(), listKnowledgeBases()]) }
  catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
async function remove(session: ChatSession) {
  try { await ElMessageBox.confirm(`删除会话“${session.title}”？`, '删除会话', { type: 'warning' }); await deleteSession(session.id); await load() }
  catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause)) }
}
onMounted(load)
</script>
<template><AppLayout><div class="page-heading"><h1>聊天记录</h1><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div><div class="toolbar"><el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索会话" clearable /><el-select v-model="selectedKb" placeholder="全部知识库" clearable><el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" /></el-select><span class="result-count">{{ filtered.length }} 个会话</span></div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" /><el-table v-loading="loading" :data="filtered" empty-text="暂无会话"><el-table-column label="会话" min-width="220"><template #default="{ row }"><el-button link type="primary" class="wrap-link" @click="router.push(`/knowledge/${row.knowledgeBaseId}/chat?session=${row.id}`)">{{ row.title }}</el-button></template></el-table-column><el-table-column label="知识库" min-width="150"><template #default="{ row }">{{ names.get(row.knowledgeBaseId) || `知识库 #${row.knowledgeBaseId}` }}</template></el-table-column><el-table-column label="更新时间" width="180"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column><el-table-column label="操作" width="80"><template #default="{ row }"><el-button text type="danger" :icon="Delete" title="删除会话" aria-label="删除会话" @click="remove(row)" /></template></el-table-column></el-table></AppLayout></template>
