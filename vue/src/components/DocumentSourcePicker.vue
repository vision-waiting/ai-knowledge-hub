<script setup lang="ts">
import { useId } from 'vue'
import type { DocumentItem, KnowledgeBase } from '@/types'
const props = withDefaults(defineProps<{ knowledgeBases: KnowledgeBase[]; knowledgeBaseId?: number; documents: DocumentItem[]; modelValue: number[]; limit?: number; disabled?: boolean; loading?: boolean }>(), { limit: 5 })
const emit = defineEmits<{ 'update:knowledgeBaseId': [number | undefined]; 'update:modelValue': [number[]] }>()
const selectId = useId()
function toggle(doc: DocumentItem, checked: boolean) {
  if (props.disabled || doc.status !== 'SUCCESS') return
  if (checked && !props.modelValue.includes(doc.id) && props.modelValue.length < props.limit) emit('update:modelValue', [...props.modelValue, doc.id])
  else if (!checked) emit('update:modelValue', props.modelValue.filter(id => id !== doc.id))
}
</script>
<template>
  <div class="source-picker">
    <label :for="selectId" class="tool-field-label">知识库</label>
    <select :id="selectId" class="tool-select" :value="knowledgeBaseId || ''" :disabled="disabled || loading" @change="emit('update:knowledgeBaseId', Number(($event.target as HTMLSelectElement).value) || undefined)">
      <option value="" disabled>选择知识库</option><option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
    </select>
    <div class="source-picker-heading"><strong>选择资料</strong><span>{{ modelValue.length }} / {{ limit }}</span></div>
    <p v-if="loading" class="muted" role="status">正在加载资料…</p>
    <div v-else-if="documents.length" class="source-options">
      <label v-for="doc in documents" :key="doc.id" :class="['source-option', { selected: modelValue.includes(doc.id), unavailable: doc.status !== 'SUCCESS' }]">
        <input type="checkbox" :checked="modelValue.includes(doc.id)" :disabled="disabled || doc.status !== 'SUCCESS' || (modelValue.length >= limit && !modelValue.includes(doc.id))" @change="toggle(doc, ($event.target as HTMLInputElement).checked)" />
        <span><strong>{{ doc.fileName }}</strong><small>{{ doc.status === 'SUCCESS' ? '可使用' : doc.status === 'FAILED' ? '处理失败' : '正在处理' }} · {{ doc.fileType.toUpperCase() }}</small></span>
      </label>
    </div>
    <p v-else class="tool-empty-small">{{ knowledgeBases.length ? '还没有资料，请先上传文档。' : '还没有知识库，请先创建知识库并上传文档。' }}</p>
    <router-link v-if="knowledgeBaseId" :to="`/knowledge/${knowledgeBaseId}/documents`" class="text-link">管理 / 上传文档 →</router-link>
  </div>
</template>
