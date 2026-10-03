<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Check, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { updateProfile, changePassword } from '@/api/auth'
import { errorMessage } from '@/utils/errors'

const auth = useAuthStore(); const router = useRouter(); const nickname = ref(auth.user?.nickname || '')
const password = reactive({ currentPassword: '', newPassword: '', confirmation: '' }); const saving = ref(false); const changing = ref(false)
async function save() {
  if (!nickname.value.trim()) return ElMessage.warning('请输入昵称')
  saving.value = true
  try { await updateProfile({ nickname: nickname.value.trim() }); await auth.loadMe(); ElMessage.success('个人资料已更新') }
  catch (cause) { ElMessage.error(errorMessage(cause)) } finally { saving.value = false }
}
async function change() {
  if (!password.currentPassword || password.newPassword.length < 8 || password.newPassword.length > 72) return ElMessage.warning('请填写当前密码，新密码长度须为 8 至 72 位')
  if (password.newPassword !== password.confirmation) return ElMessage.warning('两次新密码不一致')
  changing.value = true
  try { await changePassword({ currentPassword: password.currentPassword, newPassword: password.newPassword }); auth.clearSession(); ElMessage.success('密码已更新，请重新登录'); await router.push('/login') }
  catch (cause) { ElMessage.error(errorMessage(cause)) } finally { changing.value = false }
}
</script>
<template><AppLayout><div class="page-heading"><h1>个人中心</h1></div><div class="profile-grid"><section class="form-section"><h2>账号资料</h2><el-form label-position="top" @submit.prevent="save"><el-form-item label="用户名"><el-input :model-value="auth.user?.username" disabled /></el-form-item><el-form-item label="角色"><el-tag>{{ auth.isAdmin ? '管理员' : '普通用户' }}</el-tag></el-form-item><el-form-item label="昵称" required><el-input v-model="nickname" maxlength="50" show-word-limit /></el-form-item><el-button type="primary" :icon="Check" :loading="saving" native-type="submit">保存资料</el-button></el-form></section><section class="form-section"><h2>修改密码</h2><el-form label-position="top" @submit.prevent="change"><el-form-item label="当前密码" required><el-input v-model="password.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="新密码" required><el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" maxlength="100" /></el-form-item><el-form-item label="确认新密码" required><el-input v-model="password.confirmation" type="password" show-password autocomplete="new-password" maxlength="100" /></el-form-item><el-button :icon="Lock" :loading="changing" native-type="submit">更新密码</el-button></el-form></section></div></AppLayout></template>
