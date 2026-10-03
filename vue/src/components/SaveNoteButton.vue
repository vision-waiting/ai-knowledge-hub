<script setup lang="ts">
import { ref } from 'vue'
import { createNote } from '@/api/notes'
import { errorMessage } from '@/utils/errors'
const props = defineProps<{ content: string; title?: string; sourceSessionId?: number; disabled?: boolean }>()
const open = ref(false), saving = ref(false), title = ref(''), category = ref(''), error = ref('')
const savedId = ref<number>()
let snapshot = ''
let source: number | undefined
function show() {
  title.value = (props.title || props.content.replace(/[#*`>\n]/g, ' ').trim().slice(0, 60) || '新笔记').slice(0, 200)
  category.value = ''; error.value = ''; snapshot = props.content; source = props.sourceSessionId
  open.value = true
}
async function save() {
  if (saving.value) return
  if (!title.value.trim() || !snapshot.trim()) { error.value = '请填写标题，并确保笔记内容不为空'; return }
  saving.value = true; error.value = ''
  try {
    const result = await createNote({ title: title.value.trim(), content: snapshot, category: category.value.trim() || '未分类', sourceSessionId: source })
    savedId.value = result.id; open.value = false
  } catch (cause) { error.value = errorMessage(cause, '笔记保存失败，请重试') }
  finally { saving.value = false }
}
</script>
<template>
  <span class="save-note-control">
    <el-button data-test="open-save-note" size="small" text :disabled="disabled || !content.trim()" @click="show">保存为笔记</el-button>
    <router-link v-if="savedId" :to="{ path: '/notes', query: { note: savedId } }">已保存 · 查看笔记</router-link>
    <el-dialog v-model="open" title="保存为笔记" width="min(480px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" append-to-body>
      <p class="save-note-hint">内容将存入你的私人笔记，可在笔记中心继续编辑。</p>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="标题"><el-input v-model="title" aria-label="笔记标题" maxlength="200" show-word-limit :disabled="saving" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="category" aria-label="笔记分类" maxlength="80" placeholder="未分类" :disabled="saving" /></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <template #footer><el-button :disabled="saving" @click="open = false">取消</el-button><el-button data-test="confirm-save-note" type="primary" :loading="saving" :disabled="!title.trim()" @click="save">保存笔记</el-button></template>
    </el-dialog>
  </span>
</template>
<style scoped>
.save-note-control { display: inline-flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12px; }
.save-note-control a { color: var(--el-color-primary); text-decoration: none; }
.save-note-hint { color: var(--el-text-color-secondary); margin: 0 0 20px; line-height: 1.6; }
</style>
