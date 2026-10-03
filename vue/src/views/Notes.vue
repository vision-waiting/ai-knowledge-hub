<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Delete, EditPen, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { createNote, deleteNote, getNote, listNoteCategories, listNotes, updateNote, type NoteInput, type PersonalNote } from '@/api/notes'
import { listSessions } from '@/api/chat'
import { errorMessage, formatDate } from '@/utils/errors'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute(), router = useRouter()
const notes = ref<PersonalNote[]>([]), categories = ref<string[]>([])
const keyword = ref(''), category = ref(''), loading = ref(false), error = ref('')
const editing = ref(false), saving = ref(false), deleting = ref<number>(), sourceLoading = ref(false), editorError = ref('')
const editingId = ref<number>(), draft = reactive<NoteInput>({ title: '', content: '', category: '' })
const preview = computed(() => renderMarkdown(draft.content))
let original = '', generation = 0, detailGeneration = 0
const dirty = computed(() => editing.value && JSON.stringify(draft) !== original)

async function load() {
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    const [items, names] = await Promise.all([listNotes({ keyword: keyword.value.trim() || undefined, category: category.value || undefined }), listNoteCategories()])
    if (current === generation) { notes.value = items; categories.value = names }
  } catch (cause) { if (current === generation) error.value = errorMessage(cause, '笔记加载失败，请重试') }
  finally { if (current === generation) loading.value = false }
}
function edit(note?: PersonalNote) {
  // A manually opened editor supersedes any pending deep-link request.
  detailGeneration++
  editingId.value = note?.id
  draft.title = note?.title || ''; draft.content = note?.content || ''; draft.category = note?.category || ''
  draft.sourceSessionId = note?.sourceSessionId ?? null
  original = JSON.stringify(draft); editorError.value = ''; editing.value = true
}
async function mayDiscard() {
  if (saving.value) return false
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('尚有未保存的修改，确定放弃吗？', '放弃修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true }
  catch { return false }
}
async function closeEditor(done?: () => void) {
  if (!await mayDiscard()) return
  editing.value = false; done?.()
  if (route.query.note) await router.replace({ query: { ...route.query, note: undefined } })
}
async function save() {
  if (saving.value) return
  if (!draft.title.trim() || !draft.content.trim()) { editorError.value = '标题和内容不能为空'; return }
  saving.value = true; editorError.value = ''
  try {
    const payload = { ...draft, title: draft.title.trim(), category: draft.category?.trim() || '未分类' }
    if (editingId.value) await updateNote(editingId.value, payload)
    else await createNote(payload)
    original = JSON.stringify(draft); editing.value = false
    if (route.query.note) await router.replace({ query: { ...route.query, note: undefined } })
    await load()
  } catch (cause) { editorError.value = errorMessage(cause, '笔记保存失败，请重试') }
  finally { saving.value = false }
}
async function remove(note: PersonalNote) {
  if (deleting.value) return
  try { await ElMessageBox.confirm(`确定删除笔记“${note.title}”？删除后无法恢复。`, '删除笔记', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) }
  catch { return }
  deleting.value = note.id; error.value = ''
  try { await deleteNote(note.id); await load() }
  catch (cause) { error.value = errorMessage(cause, '删除失败，请重试') }
  finally { deleting.value = undefined }
}
async function openSource() {
  if (!draft.sourceSessionId || sourceLoading.value) return
  sourceLoading.value = true; editorError.value = ''
  try {
    const session = (await listSessions()).find(item => item.id === draft.sourceSessionId)
    if (!session) throw new Error('来源会话已删除或不可访问，笔记内容仍会保留。')
    await router.push({ path: `/knowledge/${session.knowledgeBaseId}/chat`, query: { session: session.id } })
  } catch (cause) { editorError.value = errorMessage(cause, '来源会话暂时无法打开') }
  finally { sourceLoading.value = false }
}
void load()
watch(() => route.query.note, async value => {
  const current = ++detailGeneration
  if (!value) return
  const id = Number(value)
  if (!Number.isSafeInteger(id) || id <= 0) { error.value = '无效的笔记链接'; return }
  try {
    const note = await getNote(id)
    if (current !== detailGeneration || !await mayDiscard()) return
    if (current === detailGeneration) edit(note)
  }
  catch (cause) { if (current === detailGeneration) error.value = errorMessage(cause, '笔记不存在或无法访问') }
}, { immediate: true })
onBeforeRouteLeave(mayDiscard)
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { generation++; detailGeneration++; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <AppLayout>
    <div class="page-heading"><div><span class="section-kicker">我的笔记</span><h1>我的笔记</h1><p>把有用的回答和学习收获，整理成自己的知识。</p></div><div class="heading-actions"><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button><el-button data-test="create-note" type="primary" :icon="Plus" @click="edit()">新建笔记</el-button></div></div>
    <div class="notes-toolbar">
      <el-input v-model="keyword" aria-label="搜索笔记" :prefix-icon="Search" placeholder="搜索标题或正文" maxlength="200" clearable @keyup.enter="load" @clear="load" />
      <el-select v-model="category" aria-label="筛选笔记分类" placeholder="全部分类" clearable @change="load"><el-option v-for="name in categories" :key="name" :label="name" :value="name" /></el-select>
      <el-button data-test="search-notes" :loading="loading" @click="load">搜索</el-button><span class="notes-count">{{ notes.length }} 篇笔记 · 仅自己可见</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button size="small" @click="load">重试</el-button></el-alert>
    <div v-loading="loading" class="notes-list" aria-live="polite">
      <el-empty v-if="!loading && !error && !notes.length" :description="keyword || category ? '没有匹配的笔记' : '还没有笔记'"><p class="empty-help">可以新建笔记，也可以在 AI 回答、文档解读和学习助手中点击「保存为笔记」。</p><el-button type="primary" @click="edit()">写下第一篇笔记</el-button></el-empty>
      <article v-for="note in notes" :key="note.id" class="note-card">
        <div class="note-card-meta"><el-tag size="small" effect="plain">{{ note.category }}</el-tag><time :datetime="note.updatedAt">{{ formatDate(note.updatedAt) }}</time></div>
        <button class="note-title" @click="edit(note)">{{ note.title }}</button><p class="note-excerpt">{{ note.content.slice(0, 180) }}</p>
        <div class="note-card-footer"><span>{{ note.sourceSessionId ? '来自 AI 对话' : '私人笔记' }}</span><div><el-button :data-test="`edit-note-${note.id}`" text :icon="EditPen" @click="edit(note)">编辑</el-button><el-button :data-test="`delete-note-${note.id}`" text type="danger" :icon="Delete" :loading="deleting === note.id" :disabled="!!deleting" @click="remove(note)">删除</el-button></div></div>
      </article>
    </div>
    <el-dialog v-model="editing" :title="editingId ? '编辑笔记' : '新建笔记'" width="min(1080px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeEditor" append-to-body>
      <el-form label-position="top" @submit.prevent="save"><div class="note-fields"><el-form-item label="标题"><el-input v-model="draft.title" aria-label="笔记标题" maxlength="200" show-word-limit :disabled="saving" /></el-form-item><el-form-item label="分类"><el-input v-model="draft.category" aria-label="笔记分类" maxlength="80" placeholder="未分类" :disabled="saving" /></el-form-item></div>
        <div class="note-editor-grid"><el-form-item label="内容 · 支持 Markdown"><el-input v-model="draft.content" type="textarea" aria-label="笔记内容" :rows="16" maxlength="200000" show-word-limit :disabled="saving" placeholder="记录想法、代码片段和学习要点…" /></el-form-item><section class="note-preview-panel"><h3>实时预览</h3><div v-if="draft.content" class="note-preview markdown-body" v-html="preview"></div><p v-else class="note-preview-placeholder">开始输入，预览会显示在这里。</p></section></div>
      </el-form>
      <el-alert v-if="editorError" :title="editorError" type="error" :closable="false" show-icon />
      <template #footer><div class="note-dialog-footer"><el-button v-if="draft.sourceSessionId" data-test="note-source" text :loading="sourceLoading" :disabled="saving" @click="openSource">查看来源会话 →</el-button><span v-else>笔记仅自己可见</span><div><el-button :disabled="saving" @click="closeEditor()">取消</el-button><el-button data-test="save-note" type="primary" :loading="saving" :disabled="!draft.title.trim() || !draft.content.trim()" @click="save">保存笔记</el-button></div></div></template>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
.notes-toolbar{display:flex;align-items:center;gap:12px;margin:24px 0 20px;flex-wrap:wrap}.notes-toolbar>.el-input{width:320px;max-width:100%}.notes-toolbar>.el-select{width:170px}.notes-count{margin-left:auto;color:var(--el-text-color-secondary);font-size:13px}.notes-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(300px,100%),1fr));gap:18px;min-height:220px}.notes-list>.el-empty{grid-column:1/-1}.note-card{background:var(--el-bg-color);border:1px solid var(--el-border-color-light);border-radius:16px;padding:22px;display:flex;flex-direction:column;min-width:0}.note-card-meta{display:flex;justify-content:space-between;align-items:center;gap:12px;font-size:11px;color:var(--el-text-color-secondary);flex-wrap:wrap}.note-card-meta .el-tag{max-width:100%;height:auto;white-space:normal;overflow-wrap:anywhere}.note-title{border:0;background:none;font:inherit;font-size:18px;font-weight:600;text-align:left;color:var(--el-text-color-primary);cursor:pointer;padding:0;margin:18px 0 10px;overflow-wrap:anywhere;line-height:1.5}.note-title:hover{color:var(--el-color-primary)}.note-excerpt{color:var(--el-text-color-secondary);font-size:13px;line-height:1.8;margin:0 0 20px;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.note-card-footer{display:flex;align-items:center;justify-content:space-between;margin-top:auto;border-top:1px solid var(--el-border-color-extra-light);padding-top:10px;font-size:12px;color:var(--el-text-color-secondary);gap:8px}.note-card-footer .el-button+.el-button{margin-left:0}.empty-help{font-size:13px;line-height:1.8;max-width:480px;padding:0 18px;color:var(--el-text-color-secondary)}.note-fields{display:grid;grid-template-columns:2fr 1fr;gap:18px}.note-editor-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.note-editor-grid>.el-form-item{display:block;min-width:0}.note-preview-panel{min-width:0}.note-preview-panel h3{font-size:14px;font-weight:400;line-height:22px;margin:0 0 8px}.note-preview{border:1px solid var(--el-border-color);border-radius:5px;padding:12px 16px;min-height:348px;max-height:540px;overflow:auto;overflow-wrap:anywhere}.note-preview-placeholder{padding:20px;color:var(--el-text-color-placeholder)}.note-dialog-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:12px;color:var(--el-text-color-secondary)}.note-dialog-footer>div{display:flex;flex-shrink:0}.notes-list+.el-dialog{margin-top:20px}@media(max-width:700px){.note-editor-grid,.note-fields{grid-template-columns:1fr;gap:0}.note-preview{min-height:150px;max-height:300px}.notes-count{width:100%;margin-left:0}.note-dialog-footer{flex-wrap:wrap}.notes-toolbar>.el-input{width:100%}.note-fields .el-form-item{margin-bottom:16px}.note-preview-panel{margin-bottom:18px}}
</style>
