<script setup lang="ts">
import type { Citation } from '@/types'
import { TopRight } from '@element-plus/icons-vue'
const props = defineProps<{ citation: Citation }>()
const emit = defineEmits<{ open: [Citation] }>()
</script>
<template><button class="citation-card" @click="emit('open', props.citation)"><span>[{{ props.citation.index }}]</span><div><strong>{{ props.citation.documentName }}</strong><small>第 {{ props.citation.pageNumber || '-' }} 页 · {{ props.citation.excerpt }}</small></div><el-icon><TopRight /></el-icon></button></template>
