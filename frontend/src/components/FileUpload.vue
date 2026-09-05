<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '@/api/documents'
import type { DocumentItem } from '@/types'

const props = defineProps<{ knowledgeBaseId?: number; status?: string }>()
const emit = defineEmits<{ uploaded: [DocumentItem] }>()
const uploading = ref(false)
const input = ref<HTMLInputElement>()
const statusText: Record<string, string> = { WAITING: '等待处理', PARSING: '解析中', CHUNKING: '切块中', EMBEDDING: '向量化', INDEXING: '建立索引', SUCCESS: '处理完成', FAILED: '处理失败' }
function openPicker() { input.value?.click() }
async function handleChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]; if (!file || !props.knowledgeBaseId) return
  uploading.value = true
  try { emit('uploaded', await uploadDocument(props.knowledgeBaseId, file)); ElMessage.success('文件已进入处理队列') } catch (error: any) { ElMessage.error(error.response?.data?.message || '上传失败') } finally { uploading.value = false; if (input.value) input.value.value = '' }
}
</script>
<template>
  <div class="upload-box" @click="openPicker">
    <input ref="input" type="file" accept=".pdf,.docx,.pptx,.txt,.md" hidden @change="handleChange" />
    <div class="upload-icon">↑</div><strong>{{ uploading ? '正在上传…' : '上传知识文档' }}</strong>
    <span>支持 PDF、DOCX、PPTX、TXT、Markdown，单个文件不超过 50MB</span>
    <small v-if="props.status">状态：{{ statusText[props.status] || props.status }}</small>
  </div>
</template>

