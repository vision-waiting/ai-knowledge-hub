<script setup lang="ts">
import type { ChatMessage as Message } from '@/types'
import { renderMarkdown } from '@/utils/markdown'
const props = defineProps<{ message: Message }>()
</script>
<template><div :class="['message-row', props.message.role.toLowerCase()]"><div v-if="props.message.role === 'ASSISTANT'" class="avatar ai-avatar">AI</div><div class="message-bubble"><div v-if="props.message.role === 'ASSISTANT'" class="message-label">AI 助手 <small v-if="props.message.mode">· {{ props.message.mode === 'LOCAL_DEMO' ? '本地演示' : props.message.mode }}</small></div><div class="markdown-body" v-html="renderMarkdown(props.message.content)"></div></div><div v-if="props.message.role === 'USER'" class="avatar user-avatar">我</div></div></template>
