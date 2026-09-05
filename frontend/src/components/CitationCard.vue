<script setup lang="ts">
import type { Citation } from '@/types'
const props = defineProps<{ citation: Citation }>()
const emit = defineEmits<{ open: [Citation] }>()
</script>
<template><button class="citation-card" @click="emit('open', props.citation)"><span>[{{ props.citation.index }}]</span><div><strong>{{ props.citation.documentName }}</strong><small>第 {{ props.citation.pageNumber || '-' }} 页 · {{ props.citation.excerpt }}</small></div><b>↗</b></button></template>
