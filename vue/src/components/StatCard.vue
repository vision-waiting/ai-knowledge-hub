<script setup lang="ts">
const props = defineProps<{ label: string; value: number | string; icon: string; tone?: string }>()
</script>
<template><div :class="['stat-card', props.tone || 'blue']"><span class="stat-icon">{{ props.icon }}</span><div><small>{{ props.label }}</small><strong>{{ props.value }}</strong></div></div></template>
