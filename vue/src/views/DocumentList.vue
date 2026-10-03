<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ChatDotRound, Download, MoreFilled, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import FileUpload from '@/components/FileUpload.vue'
import DocumentStatus from '@/components/DocumentStatus.vue'
import SourceDrawer from '@/components/SourceDrawer.vue'
import * as api from '@/api/documents'
import { errorMessage, formatDate } from '@/utils/errors'
import { getKnowledgeBase } from '@/api/knowledge'
import { useAuthStore } from '@/stores/auth'
import type { DocumentItem } from '@/types'

const auth = useAuthStore(); const ownerId = ref<number>(); const writable = ref<boolean>()
const baseName = ref('')
const canManage = computed(() => writable.value ?? (ownerId.value !== undefined && (auth.isAdmin || auth.user?.id === ownerId.value)))
const route = useRoute(); const id = computed(() => Number(route.params.id))
const docs = ref<DocumentItem[]>([]); const loading = ref(false); const error = ref(''); const keyword = ref(''); const status = ref('')
const busy = ref<number[]>([]); const selected = ref<number>(); let timer: ReturnType<typeof setTimeout> | undefined; let request = 0; let disposed = false
const processing = (doc: DocumentItem) => ['WAITING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING'].includes(doc.status)
const trashMode = ref(false)
const versions = ref<api.DocumentVersion[]>([])
const versionOpen = ref(false)
const versionLoading = ref(false)
const versionTarget = ref<DocumentItem>()
const versionInput = ref<HTMLInputElement>()
async function toggleTrash() { trashMode.value = !trashMode.value; status.value = ''; await load() }
async function restore(doc: DocumentItem) {
  busy.value.push(doc.id)
  try { await api.restoreDocument(doc.id); ElMessage.success('文档已恢复'); await load(true) }
  catch (cause) { ElMessage.error(errorMessage(cause)) }
  finally { busy.value = busy.value.filter(value => value !== doc.id) }
}
async function showVersions(doc: DocumentItem) {
  versionTarget.value = doc; versionOpen.value = true; versionLoading.value = true
  try { versions.value = await api.listVersions(doc.id) }
  catch (cause) { ElMessage.error(errorMessage(cause)); versions.value = [] }
  finally { versionLoading.value = false }
}
function chooseVersion(doc: DocumentItem) { versionTarget.value = doc; versionInput.value?.click() }
async function replaceFile(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; const target = versionTarget.value
  if (!file || !target) return
  busy.value.push(target.id)
  try { await api.uploadVersion(target.id, file); ElMessage.success('新版本已进入处理队列；成功后自动替换当前版本'); await load(true) }
  catch (cause) { ElMessage.error(errorMessage(cause)) }
  finally { input.value = ''; busy.value = busy.value.filter(value => value !== target.id) }
}
const filtered = computed(() => docs.value.filter(doc => doc.fileName.toLowerCase().includes(keyword.value.toLowerCase()) && (!status.value || (status.value === 'PROCESSING' ? processing(doc) : doc.status === status.value))))
async function load(silent = false) {
  clearTimeout(timer)
  const current = ++request
  if (!silent) loading.value = true
  try {
    const [result, base] = await Promise.all([trashMode.value ? api.listTrash(id.value) : api.listDocuments(id.value), getKnowledgeBase(id.value)])
    if (current !== request || disposed) return
    ownerId.value = base.ownerId; writable.value = base.canWrite; baseName.value = base.name; docs.value = result; error.value = ''
  } catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally {
    if (current === request && !disposed) {
      loading.value = false
      if (docs.value.some(processing)) timer = setTimeout(() => load(true), 2000)
    }
  }
}
async function remove(doc: DocumentItem) {
  try {
    await ElMessageBox.confirm(`将文档“${doc.fileName}”移入回收站？原文件和历史引用将保留。`, '移入回收站', { type: 'warning' })
    busy.value.push(doc.id); await api.deleteDocument(doc.id); ElMessage.success('已移入回收站'); await load(true)
  } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause)) }
  finally { busy.value = busy.value.filter(value => value !== doc.id) }
}
async function reindex(doc: DocumentItem) {
  busy.value.push(doc.id)
  try { const updated = await api.reindexDocument(doc.id); Object.assign(doc, updated); ElMessage.success('已进入处理队列'); await load(true) }
  catch (cause) { ElMessage.error(errorMessage(cause)) }
  finally { busy.value = busy.value.filter(value => value !== doc.id) }
}
async function download(doc: DocumentItem) {
  busy.value.push(doc.id)
  try { await api.saveDocument(doc.id, doc.fileName) } catch (cause) { ElMessage.error(errorMessage(cause)) }
  finally { busy.value = busy.value.filter(value => value !== doc.id) }
}
function rowCommand(command: string, doc: DocumentItem) {
  if (command === 'versions') void showVersions(doc)
  else if (command === 'newVersion') chooseVersion(doc)
  else if (command === 'reindex') void reindex(doc)
  else if (command === 'remove') void remove(doc)
}
watch(() => route.query.status, value => { status.value = ['SUCCESS', 'FAILED', 'PROCESSING'].includes(String(value)) ? String(value) : '' }, { immediate: true })
watch(id, () => { docs.value = []; baseName.value = ''; keyword.value = ''; trashMode.value = false; versionOpen.value = false; ownerId.value = undefined; writable.value = undefined; selected.value = undefined; load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; request++; clearTimeout(timer) })
</script>
<template><AppLayout :context-name="baseName">
  <div class="page-heading"><div><router-link class="back-link" :to="`/knowledge/${id}`"><el-icon><ArrowLeft /></el-icon>返回知识库</router-link><h1>{{ canManage ? '文档管理' : '文档列表' }}</h1></div><div class="heading-actions"><router-link :to="`/knowledge/${id}/chat`"><el-button :icon="ChatDotRound">开始问答</el-button></router-link><FileUpload v-if="canManage && !trashMode" compact :knowledge-base-id="id" @uploaded="load()" /></div></div>
  <input ref="versionInput" type="file" hidden accept=".pdf,.docx,.txt,.md,.pptx" @change="replaceFile" /><p v-if="canManage && !trashMode" class="muted">支持 PDF、DOCX、PPTX、TXT、Markdown，单个文件不超过 50MB。上传后将自动解析并建立索引。</p>
  <div class="toolbar"><el-input v-model="keyword" aria-label="搜索文档" placeholder="搜索文档" clearable /><el-select v-model="status" aria-label="文档状态" placeholder="全部状态" clearable><el-option label="处理中" value="PROCESSING" /><el-option label="处理完成" value="SUCCESS" /><el-option label="处理失败" value="FAILED" /></el-select><el-button :icon="Refresh" :loading="loading" title="刷新文档" aria-label="刷新文档" @click="load()" /><el-button v-if="canManage" @click="toggleTrash">{{ trashMode ? '返回文档' : '回收站' }}</el-button><span class="result-count">{{ loading && !docs.length ? '加载中…' : error && !docs.length ? '未能获取文档数量' : `${filtered.length} 份文档` }}</span></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
  <p v-if="error && docs.length" class="muted">刷新失败，以下为上次成功加载的文档。</p><p class="table-scroll-hint">左右滑动表格可查看文档操作。</p><div class="document-table-scroll" role="region" aria-label="文档列表，可横向滚动" tabindex="0"><el-table v-loading="loading" :data="filtered" :empty-text="loading ? '正在加载文档…' : error ? '文档未能加载，请重试' : keyword || status ? '没有匹配的文档，请调整筛选条件' : '暂无文档'"><el-table-column label="文档" min-width="240"><template #default="{ row }"><button class="document-name" @click="selected = row.id">{{ row.fileName }}</button><div class="muted">{{ row.fileType.toUpperCase() }} · {{ (row.fileSize / 1024 / 1024).toFixed(2) }} MB · {{ row.pageCount || '-' }} 页</div><details v-if="row.summary || row.errorMessage" class="document-summary-details"><summary>{{ row.status === 'FAILED' ? '查看失败原因' : '查看摘要' }}</summary><p>{{ row.errorMessage || row.summary }}</p><div class="tag-list"><el-tag v-for="tag in row.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag></div></details></template></el-table-column><el-table-column label="处理状态" width="150"><template #default="{ row }"><DocumentStatus :status="row.status" :error-message="row.errorMessage" /></template></el-table-column><el-table-column label="上传时间" width="170"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><div class="icon-actions"><el-button text :icon="View" title="查看原文" aria-label="查看原文" @click="selected = row.id" /><el-button text :icon="Download" title="下载" aria-label="下载" :disabled="busy.includes(row.id)" @click="download(row)" /><el-button v-if="canManage && trashMode" text type="primary" :disabled="busy.includes(row.id)" @click="restore(row)">恢复</el-button><el-dropdown v-else trigger="click" @command="(command: string) => rowCommand(command, row)"><el-button text :icon="MoreFilled" title="更多操作" :aria-label="`${row.fileName}的更多操作`" /><template #dropdown><el-dropdown-menu><el-dropdown-item command="versions">版本记录</el-dropdown-item><template v-if="canManage"><el-dropdown-item v-if="row.status === 'SUCCESS'" command="newVersion" :disabled="busy.includes(row.id)">上传新版本</el-dropdown-item><el-dropdown-item command="reindex" :disabled="processing(row) || busy.includes(row.id)">重新索引</el-dropdown-item><el-dropdown-item command="remove" divided class="danger-item" :disabled="processing(row) || busy.includes(row.id)">移入回收站</el-dropdown-item></template></el-dropdown-menu></template></el-dropdown></div></template></el-table-column></el-table></div>
  <el-dialog v-model="versionOpen" title="文档版本" width="720px">
    <p>新版本处理成功后生效。历史引用始终指向原版本；归档和回收站中的内容不参与新问答。</p>
    <el-table v-loading="versionLoading" :data="versions" empty-text="暂无版本">
      <el-table-column label="版本" width="80"><template #default="{row}">v{{ row.versionNumber }}</template></el-table-column>
      <el-table-column label="文件"><template #default="{row}">{{ row.document.fileName }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{row}">{{ row.document.status === 'ARCHIVED' ? '历史版本' : row.document.status === 'TRASHED' ? '回收站' : row.document.status === 'SUCCESS' ? '当前版本' : row.document.status }}</template></el-table-column>
      <el-table-column label="操作" width="160"><template #default="{row}"><el-button text @click="selected = row.document.id">查看</el-button><el-button text @click="download(row.document)">下载</el-button></template></el-table-column>
    </el-table>
  </el-dialog>
  <SourceDrawer :document-id="selected" @close="selected = undefined" />
</AppLayout></template>
