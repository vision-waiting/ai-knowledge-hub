<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
const form = reactive({ username: 'student', password: '123456' }); const loading = ref(false); const router = useRouter(); const route = useRoute(); const auth = useAuthStore()
async function submit() { if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码'); loading.value = true; try { await auth.login(form.username, form.password); router.push(String(route.query.redirect || '/dashboard')) } catch (error: any) { ElMessage.error(error.response?.data?.message || '登录失败，请检查账号密码') } finally { loading.value = false } }
</script>
<template><div class="login-page"><div class="login-art"><div class="art-grid"></div><div class="art-copy"><div class="logo-large">AI</div><h1>AI Knowledge Hub</h1><p>让每一份知识都能被准确找到、理解和引用。</p><div class="art-flow"><span>文档解析</span><i>→</i><span>智能检索</span><i>→</i><span>可信问答</span></div></div></div><div class="login-panel"><div class="login-card"><div class="mobile-brand"><span>AI</span> Knowledge Hub</div><p class="eyebrow">WELCOME BACK</p><h2>登录知识中心</h2><p class="muted">使用你的账号继续管理知识库</p><form @submit.prevent="submit"><label>用户名<input v-model="form.username" placeholder="请输入用户名" autocomplete="username" /></label><label>密码<input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" /></label><button class="primary-button" :disabled="loading">{{ loading ? '正在登录…' : '登录' }}<span>→</span></button></form><p class="demo-hint">演示账号：student / 123456</p></div></div></div></template>

