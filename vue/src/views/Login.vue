<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { register } from '@/api/auth'
import { errorMessage } from '@/utils/errors'
import { Right, Collection, Connection, DocumentChecked } from '@element-plus/icons-vue'
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })
const loading = ref(false); const router = useRouter(); const route = useRoute(); const auth = useAuthStore()
const registering = computed(() => route.name === 'register')
const error = ref('')
const alternate = computed(() => ({ name: registering.value ? 'login' : 'register', query: route.query.redirect ? { redirect: route.query.redirect } : {} }))
watch(registering, () => { error.value = ''; form.password = ''; form.confirmPassword = '' })
async function submit() {
  if (loading.value) return
  error.value = ''
  if (!form.username.trim() || !form.password) { error.value = '请输入用户名和密码'; return }
  if (registering.value) {
    if (!/^[A-Za-z0-9_]{4,32}$/.test(form.username.trim())) { error.value = '用户名须为 4–32 位字母、数字或下划线'; return }
    if (form.password.length < 8 || new TextEncoder().encode(form.password).length > 72) { error.value = '密码至少 8 位，UTF-8 编码不能超过 72 字节'; return }
    if (form.password !== form.confirmPassword) { error.value = '两次输入的密码不一致'; return }
  }
  loading.value = true
  try {
    if (registering.value) {
      const result = await register({ username: form.username.trim(), nickname: form.nickname.trim(), password: form.password, confirmPassword: form.confirmPassword })
      auth.setSession(result.token, result.user)
      ElMessage.success('注册成功，欢迎加入')
    } else await auth.login(form.username.trim(), form.password)
    const redirect = String(route.query.redirect || '/dashboard')
    await router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/dashboard')
  } catch (cause) { error.value = errorMessage(cause, registering.value ? '注册失败，请稍后重试' : '登录失败，请稍后重试') }
  finally { loading.value = false }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="mobile-brand"><span>智</span>智能知识中心</div>
      <div class="login-story-content"><span class="section-kicker">让团队知识，有序连接</span><h1>让知识有序，<br />让答案有据。</h1><p>汇集散落的文档，在每一次提问中找到答案。<br />从知识沉淀到团队协作，一个工作空间就够了。</p>
        <div class="knowledge-flow"><div><el-icon><Collection /></el-icon><strong>汇集资料</strong><span>集中管理多种格式文档</span></div><div><el-icon><Connection /></el-icon><strong>理解与检索</strong><span>围绕知识库展开对话</span></div><div><el-icon><DocumentChecked /></el-icon><strong>追溯依据</strong><span>每条引用都能回到原文</span></div></div>
      </div><small class="login-story-footer">智能知识管理平台</small>
    </section>
    <section :class="['login-panel', { 'register-panel': registering }]"><div class="login-card"><span class="section-kicker">{{ registering ? '从这里开始' : '欢迎回来' }}</span><h2>{{ registering ? '创建你的账号' : '登录工作空间' }}</h2><p class="muted">{{ registering ? '注册后即可创建知识库，整理资料并开始问答。' : '使用你的账号，继续探索团队知识。' }}</p>
      <form @submit.prevent="submit" :aria-busy="loading">
        <label>用户名<input v-model="form.username" name="username" :placeholder="registering ? '4–32 位字母、数字或下划线' : '请输入用户名'" autocomplete="username" :maxlength="registering ? 32 : 50" :disabled="loading" required /></label>
        <label v-if="registering">昵称 <span class="field-optional">选填</span><input v-model="form.nickname" name="nickname" placeholder="如何称呼你" autocomplete="nickname" maxlength="50" :disabled="loading" /></label>
        <label>密码<input v-model="form.password" name="password" type="password" :placeholder="registering ? '至少 8 位，建议混合字母和数字' : '请输入密码'" :autocomplete="registering ? 'new-password' : 'current-password'" :maxlength="registering ? 72 : undefined" :disabled="loading" required /></label>
        <label v-if="registering">确认密码<input v-model="form.confirmPassword" name="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" maxlength="72" :disabled="loading" required /></label>
        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <button type="submit" class="primary-button" :disabled="loading">{{ loading ? (registering ? '正在创建…' : '正在登录…') : (registering ? '注册并进入工作台' : '登录工作空间') }}<el-icon><Right /></el-icon></button>
      </form>
      <p class="auth-switch">{{ registering ? '已有账号？' : '还没有账号？' }}<router-link :to="alternate" :aria-disabled="loading" @click="loading && $event.preventDefault()">{{ registering ? '立即登录' : '创建账号' }}</router-link></p>
      <p class="login-help">{{ registering ? '新账号可管理自己的知识库与会话。' : '忘记密码？请联系系统管理员。' }}</p><div class="login-trust"><el-icon><DocumentChecked /></el-icon>按权限访问 · 文档持久保存 · 引用可追溯</div>
    </div></section>
  </div>
</template>
