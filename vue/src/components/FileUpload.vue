<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '@/api/documents'
import type { DocumentItem } from '@/types'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps<{ knowledgeBaseId?: number; status?: string; compact?: boolean }>()
const emit = defineEmits<{ uploaded: [DocumentItem] }>()
const uploading = ref(false)
const input = ref<HTMLInputElement>()
const statusText: Record<string, string> = { WAITING: '等待处理', PARSING: '解析中', CHUNKING: '切块中', EMBEDDING: '向量化', INDEXING: '建立索引', SUCCESS: '处理完成', FAILED: '处理失败' }
function openPicker() { if (!uploading.value) input.value?.click() }
async function upload(file?: File) {
  if (!file || !props.knowledgeBaseId || uploading.value) return
  if (!/\.(pdf|docx|pptx|txt|md)$/i.test(file.name)) return ElMessage.warning('请选择 PDF、DOCX、PPTX、TXT 或 Markdown 文件')
  if (file.size > 50 * 1024 * 1024) return ElMessage.warning('文件不能超过 50MB')
  if (!file.size) return ElMessage.warning('不能上传空文件')
  uploading.value = true
  try { emit('uploaded', await uploadDocument(props.knowledgeBaseId, file)); ElMessage.success('文件已进入处理队列') } catch (error: any) { ElMessage.error(error.response?.data?.message || '上传失败') } finally { uploading.value = false; if (input.value) input.value.value = '' }
}
async function handleChange(event: Event) {
  await upload((event.target as HTMLInputElement).files?.[0])
}
</script>
<template>
  <div :class="['upload-box', { compact }]" role="button" tabindex="0" :aria-disabled="uploading" @click="openPicker" @keydown.enter.prevent="openPicker" @keydown.space.prevent="openPicker" @dragover.prevent @drop.prevent="upload($event.dataTransfer?.files[0])">
    <input ref="input" type="file" accept=".pdf,.docx,.pptx,.txt,.md" :disabled="uploading" hidden @change="handleChange" />
    <el-icon class="upload-icon"><UploadFilled /></el-icon><strong>{{ uploading ? '正在上传…' : '上传知识文档' }}</strong>
    <span v-if="!compact">支持 PDF、DOCX、PPTX、TXT、Markdown，单个文件不超过 50MB</span>
    <small v-if="props.status">状态：{{ statusText[props.status] || props.status }}</small>
  </div>
</template>
