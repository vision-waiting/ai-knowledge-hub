<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import * as api from '@/api/admin'
import { errorMessage, formatDate } from '@/utils/errors'
import type { User } from '@/types'

const router = useRouter(); const auth = useAuthStore(); const users = ref<User[]>([]); const keyword = ref(''); const loading = ref(false); const error = ref('')
const dialog = ref(false); const saving = ref(false); const busy = ref<number[]>([])
const editingId = ref<number>(); const detail = ref<User>(); const detailDialog = ref(false)
const form = reactive({ username: '', password: '', nickname: '', role: 'USER', status: 1 })
let userRequest = 0
onBeforeUnmount(() => { userRequest++ })
function openCreate() { userRequest++; editingId.value = undefined; Object.assign(form, { username: '', password: '', nickname: '', role: 'USER', status: 1 }); dialog.value = true }
async function openUser(user: User, edit: boolean) {
  const request = ++userRequest
  busy.value.push(user.id)
  try {
    const current = await api.getUser(user.id)
    if (request !== userRequest) return
    if (edit) { editingId.value = current.id; Object.assign(form, { username: current.username, nickname: current.nickname || '', password: '', role: current.role, status: current.status ?? 1 }); dialog.value = true }
    else { detail.value = current; detailDialog.value = true }
  } catch (cause) { if (request === userRequest) ElMessage.error(errorMessage(cause)) }
  finally { busy.value = busy.value.filter(id => id !== user.id) }
}
const filtered = computed(() => users.value.filter(user => `${user.username} ${user.nickname || ''}`.toLowerCase().includes(keyword.value.toLowerCase())))
async function load() { loading.value = true; error.value = ''; try { users.value = await api.users() } catch (cause) { error.value = errorMessage(cause) } finally { loading.value = false } }
async function save() {
  if (saving.value) return
  if (!/^[a-zA-Z0-9_.-]{3,50}$/.test(form.username)) return ElMessage.warning('用户名为 3 至 50 位字母、数字、下划线、点或短横线')
  if ((!editingId.value || form.password) && (form.password.length < 8 || new TextEncoder().encode(form.password).length > 72)) return ElMessage.warning('密码至少 8 位，UTF-8 编码不能超过 72 字节')
  saving.value = true
  try {
    if (editingId.value) {
      const updated = await api.updateUser(editingId.value, { username: form.username, nickname: form.nickname, role: form.role, status: form.status, ...(form.password ? { password: form.password } : {}) })
      if (updated.id === auth.user?.id && form.password) { auth.clearSession(); dialog.value = false; form.password = ''; ElMessage.success('密码已修改，请重新登录'); await router.push('/login'); return }
      if (updated.id === auth.user?.id) { auth.user = updated; localStorage.setItem('ai_kh_user', JSON.stringify(updated)) }
    } else { await api.createUser({ username: form.username, password: form.password, nickname: form.nickname, role: form.role }) }
    dialog.value = false; form.password = ''; ElMessage.success(editingId.value ? '用户信息已更新' : '用户已创建'); await load()
  } catch (cause) { ElMessage.error(errorMessage(cause)) } finally { saving.value = false }
}
async function status(user: User, value: boolean) {
  busy.value.push(user.id)
  try { await api.updateStatus(user.id, value ? 1 : 0); user.status = value ? 1 : 0 }
  catch (cause) { ElMessage.error(errorMessage(cause)) } finally { busy.value = busy.value.filter(id => id !== user.id) }
}
onMounted(load)
</script>
<template><AppLayout><div class="page-heading"><h1>用户管理</h1><el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button></div><div class="toolbar"><el-input v-model="keyword" placeholder="搜索用户名或昵称" :prefix-icon="Search" clearable /><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button><span class="result-count">{{ filtered.length }} 个用户</span></div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" /><el-table v-loading="loading" :data="filtered" empty-text="暂无用户"><el-table-column prop="id" label="ID" width="80" /><el-table-column prop="username" label="用户名" min-width="150" /><el-table-column prop="nickname" label="昵称" min-width="150" /><el-table-column label="角色" width="155"><template #default="{ row }"><el-tag :type="row.role === 'ADMIN' ? 'success' : 'info'">{{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}</el-tag></template></el-table-column><el-table-column label="启用状态" width="135"><template #default="{ row }"><el-switch :model-value="row.status === 1" :disabled="row.id === auth.user?.id" :loading="busy.includes(row.id)" active-text="启用" @change="status(row, Boolean($event))" /></template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" :data-test="`view-user-${row.id}`" :disabled="busy.includes(row.id)" @click="openUser(row, false)">查看</el-button><el-button link type="primary" :data-test="`edit-user-${row.id}`" :disabled="busy.includes(row.id)" @click="openUser(row, true)">编辑</el-button></template></el-table-column></el-table><el-dialog v-model="dialog" @close="userRequest++" :title="editingId ? '编辑用户' : '新增用户'" width="460px" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><el-form label-position="top" @submit.prevent="save"><el-form-item label="用户名" required><el-input v-model="form.username" maxlength="50" autocomplete="off" /></el-form-item><el-form-item label="昵称"><el-input v-model="form.nickname" aria-label="昵称" maxlength="100" /></el-form-item><el-form-item :label="editingId ? '新密码' : '密码'" :required="!editingId"><el-input v-model="form.password" :aria-label="editingId ? '新密码' : '密码'" :placeholder="editingId ? '留空表示不修改密码' : '请输入密码'" type="password" show-password autocomplete="new-password" maxlength="100" /></el-form-item><el-form-item label="角色"><el-tag v-if="editingId">{{ form.role === 'ADMIN' ? '管理员' : '普通用户' }}</el-tag><el-radio-group v-else v-model="form.role" :disabled="editingId === auth.user?.id"><el-radio-button value="USER">普通用户</el-radio-button><el-radio-button value="ADMIN">管理员</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="editingId" label="启用状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" :disabled="editingId === auth.user?.id" /></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" data-test="save-user" @click="save">{{ editingId ? '保存修改' : '创建用户' }}</el-button></template></el-dialog><el-dialog v-model="detailDialog" @close="userRequest++" title="用户详情" width="520px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="用户 ID">{{ detail.id }}</el-descriptions-item><el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item><el-descriptions-item label="昵称">{{ detail.nickname || '-' }}</el-descriptions-item><el-descriptions-item label="角色">{{ detail.role === 'ADMIN' ? '管理员' : '普通用户' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status === 1 ? '启用' : '禁用' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatDate(detail.updatedAt) }}</el-descriptions-item></el-descriptions><template #footer><el-button @click="detailDialog = false">关闭</el-button></template></el-dialog></AppLayout></template>
