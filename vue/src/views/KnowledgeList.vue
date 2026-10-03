<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'
import KnowledgeCard from '@/components/KnowledgeCard.vue'
import * as api from '@/api/knowledge'
import { errorMessage } from '@/utils/errors'
import type { KnowledgeBase } from '@/types'
const auth = useAuthStore(); const category = ref('personal')
const filteredItems = computed(() => items.value.filter(item => category.value === 'shared' ? item.shared : !item.shared))
const sharedLocked = computed(() => editing.value ? !!editing.value.sharingLocked : auth.isAdmin)
const route = useRoute(); const router = useRouter(); const items = ref<KnowledgeBase[]>([]); const keyword = ref(''); const dialog = ref(false)
const editing = ref<KnowledgeBase | null>(null); const form = ref({ name: '', description: '', shared: auth.isAdmin }); const loading = ref(false); const saving = ref(false); const error = ref('')
let request = 0
async function load() {
  const current = ++request; loading.value = true; error.value = ''
  try { const result = await api.listKnowledgeBases(keyword.value); if (current === request) items.value = result }
  catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally { if (current === request) loading.value = false }
}
function openCreate() { editing.value = null; form.value = { name: '', description: '', shared: auth.isAdmin }; dialog.value = true }
function openEdit(item: KnowledgeBase) { editing.value = item; form.value = { name: item.name, description: item.description || '', shared: !!item.shared }; dialog.value = true }
async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入知识库名称')
  saving.value = true
  try { const payload = { name: form.value.name.trim(), description: form.value.description.trim(), shared: sharedLocked.value || form.value.shared }; const saved = editing.value ? await api.updateKnowledgeBase(editing.value.id, payload) : await api.createKnowledgeBase(payload); category.value = saved.shared ? 'shared' : 'personal'; dialog.value = false; ElMessage.success(editing.value ? '知识库已更新' : '知识库已创建'); await load() }
  catch (cause) { ElMessage.error(errorMessage(cause)) } finally { saving.value = false }
}
async function remove(item: KnowledgeBase) {
  try { await ElMessageBox.confirm(`删除知识库“${item.name}”及其文档和会话？`, '删除知识库', { type: 'warning' }); await api.deleteKnowledgeBase(item.id); ElMessage.success('知识库已删除'); await load() }
  catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause)) }
}
onMounted(() => { load(); if (route.query.create) { openCreate(); router.replace('/knowledge') } })
</script>
<template><AppLayout><div class="page-heading"><div><span class="section-kicker">知识库</span><h1>知识库</h1><p>按主题组织文档，在同一个空间里管理资料与问答。</p></div><el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button></div><el-tabs v-model="category"><el-tab-pane :label="`个人知识库（${items.filter(item => !item.shared).length}）`" name="personal" /><el-tab-pane :label="`共享知识库（${items.filter(item => item.shared).length}）`" name="shared" /></el-tabs><form class="toolbar" @submit.prevent="load"><el-input v-model="keyword" placeholder="搜索知识库" :prefix-icon="Search" clearable @clear="load" /><el-button :icon="Search" native-type="submit">搜索</el-button><el-button :icon="Refresh" :loading="loading" title="刷新" aria-label="刷新" @click="load" /><span class="result-count">共 {{ filteredItems.length }} 个知识库</span></form><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><div v-loading="loading"><div v-if="filteredItems.length" class="knowledge-grid"><KnowledgeCard v-for="item in filteredItems" :key="item.id" :item="item" @open="id => router.push(`/knowledge/${id}`)" @edit="openEdit" @remove="remove" /></div><el-empty v-else-if="!loading && !error" :description="keyword ? '未找到匹配的知识库' : (category === 'shared' ? '暂无共享知识库' : '暂无个人知识库')"><el-button v-if="!keyword" type="primary" :icon="Plus" @click="openCreate">创建知识库</el-button></el-empty></div><el-dialog v-model="dialog" :title="editing ? '编辑知识库' : '新建知识库'" width="460px"><el-form label-position="top" @submit.prevent="save"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item><el-form-item label="共享设置"><el-switch v-model="form.shared" :disabled="sharedLocked" active-text="共享知识库" inactive-text="个人知识库" /><p class="sharing-help">{{ sharedLocked ? '管理员发布的知识库始终共享。' : '开启后所有登录用户可查看和问答；只有发布者和管理员可以编辑、删除。' }}</p></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog></AppLayout></template>
