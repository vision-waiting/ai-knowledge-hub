<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { logs } from '@/api/admin'
import { errorMessage, formatDate } from '@/utils/errors'
import type { OperationLog } from '@/types'
const items = ref<OperationLog[]>([]); const keyword = ref(''); const loading = ref(false); const error = ref('')
const filtered = computed(() => items.value.filter(item => `${item.operation || ''} ${item.requestUri || ''} ${item.userId || ''}`.toLowerCase().includes(keyword.value.toLowerCase())))
async function load() { loading.value = true; error.value = ''; try { items.value = await logs() } catch (cause) { error.value = errorMessage(cause) } finally { loading.value = false } }
onMounted(load)
</script>
<template><AppLayout><div class="page-heading"><h1>操作日志</h1><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div><div class="toolbar"><el-input v-model="keyword" placeholder="搜索操作、接口或用户 ID" :prefix-icon="Search" clearable /><span class="result-count">最近 {{ items.length }} 条</span></div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" /><el-table v-loading="loading" :data="filtered" empty-text="暂无操作日志"><el-table-column label="时间" width="185"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column><el-table-column prop="userId" label="用户 ID" width="90" /><el-table-column prop="operation" label="操作" min-width="160" /><el-table-column prop="method" label="方法" width="85" /><el-table-column prop="requestUri" label="接口" min-width="240" show-overflow-tooltip /><el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column><el-table-column prop="ip" label="IP" width="140" /></el-table></AppLayout></template>
