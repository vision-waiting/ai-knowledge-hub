<script setup lang="ts">
defineProps<{ status: string; errorMessage?: string }>()
const labels: Record<string, string> = { WAITING: '等待处理', PARSING: '解析中', CHUNKING: '切块中', EMBEDDING: '向量化', INDEXING: '索引中', SUCCESS: '处理完成', FAILED: '处理失败' }
</script>
<template><span :class="['status-pill', status.toLowerCase()]"><i></i>{{ labels[status] || status }}<em v-if="status === 'FAILED' && errorMessage">：{{ errorMessage }}</em></span></template>

