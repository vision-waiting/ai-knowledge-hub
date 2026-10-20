<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { isNavigationFailure, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { register } from '@/api/auth'
import { errorMessage } from '@/utils/errors'
import { enterWorkspace } from '@/utils/enterWorkspace'
import { Right, Collection, Connection, DocumentChecked } from '@element-plus/icons-vue'
import KnowledgeOrbit from '@/components/KnowledgeOrbit.vue'
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })
const loading = ref(false); const router = useRouter(); const route = useRoute(); const auth = useAuthStore()
const registering = computed(() => route.name === 'register')
const error = ref('')
const entering = ref(false)
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
    entering.value = true
    await enterWorkspace(async () => {
      const failure = await router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/dashboard')
      if (isNavigationFailure(failure)) throw new Error('暂时无法进入工作空间，请重试')
    })
  } catch (cause) { error.value = errorMessage(cause, registering.value ? '注册失败，请稍后重试' : '登录失败，请稍后重试') }
  finally { loading.value = false; entering.value = false }
}
</script>
<template>
  <div class="login-page">
    <div class="login-book">
    <section class="login-story">
      <div class="mobile-brand"><span>智</span>智能知识中心</div>
      <div class="login-story-content"><h1>让知识有序，<br />让答案有据。</h1><p>汇集散落的文档，在每一次提问中找到答案。<br />从知识沉淀到团队协作，一个工作空间就够了。</p>
        <KnowledgeOrbit />
        <div class="knowledge-flow"><div><el-icon><Collection /></el-icon><strong>汇集资料</strong><span>集中管理多种格式文档</span></div><div><el-icon><Connection /></el-icon><strong>理解与检索</strong><span>围绕知识库展开对话</span></div><div><el-icon><DocumentChecked /></el-icon><strong>追溯依据</strong><span>每条引用都能回到原文</span></div></div>
      </div><small class="login-story-footer">智能知识管理平台</small>
    </section>
    <section :class="['login-panel', { 'register-panel': registering }]" aria-labelledby="auth-title"><div class="login-card"><h2 id="auth-title">{{ registering ? '创建你的账号' : '登录工作空间' }}</h2><p class="muted">{{ registering ? '注册后即可创建知识库，整理资料并开始问答。' : '使用你的账号，继续探索团队知识。' }}</p>
      <form @submit.prevent="submit" :aria-busy="loading">
        <label>用户名<input v-model="form.username" name="username" :placeholder="registering ? '4–32 位字母、数字或下划线' : '请输入用户名'" autocomplete="username" :maxlength="registering ? 32 : 50" :disabled="loading" required /></label>
        <label v-if="registering">昵称 <span class="field-optional">选填</span><input v-model="form.nickname" name="nickname" placeholder="如何称呼你" autocomplete="nickname" maxlength="50" :disabled="loading" /></label>
        <label>密码<input v-model="form.password" name="password" type="password" :placeholder="registering ? '至少 8 位，建议混合字母和数字' : '请输入密码'" :autocomplete="registering ? 'new-password' : 'current-password'" :maxlength="registering ? 72 : undefined" :disabled="loading" required /></label>
        <label v-if="registering">确认密码<input v-model="form.confirmPassword" name="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" maxlength="72" :disabled="loading" required /></label>
        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <button type="submit" class="primary-button" :disabled="loading"><span>{{ entering ? '正在进入工作空间…' : loading ? (registering ? '正在创建…' : '正在登录…') : (registering ? '注册并进入工作台' : '登录工作空间') }}</span><span v-if="loading && !entering" class="auth-spinner" aria-hidden="true" /><el-icon v-else aria-hidden="true"><Right /></el-icon></button>
        <span class="auth-announcement" role="status">{{ entering ? '验证成功，正在进入工作空间' : loading ? '正在验证账号，请稍候' : '' }}</span>
      </form>
      <p class="auth-switch">{{ registering ? '已有账号？' : '还没有账号？' }}<router-link :to="alternate" :aria-disabled="loading" @click="loading && $event.preventDefault()">{{ registering ? '立即登录' : '创建账号' }}</router-link></p>
      <p class="login-help">{{ registering ? '新账号可管理自己的知识库与会话。' : '忘记密码？请联系系统管理员。' }}</p><div class="login-trust"><el-icon><DocumentChecked /></el-icon>按权限访问 · 文档持久保存 · 引用可追溯</div>
    </div></section>
    </div>
  </div>
</template>

<style scoped>
.login-page { display: grid; grid-template-columns: 1fr; padding: 36px; background: #e1e5d9; }
.login-book { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); width: min(1280px, 100%); min-height: min(780px, calc(100dvh - 72px)); perspective: 2200px; border-radius: 5px 14px 14px 5px; box-shadow: 0 3px 0 #d3d4c4, 0 6px 0 #f6f3e5, 0 9px 0 #395642, 0 28px 64px #293d3226; }
.login-story { position: relative; padding: 32px clamp(28px, 4vw, 60px); border: 0; border-radius: 5px 0 0 5px; background: linear-gradient(90deg, #eff0df 0%, #eff0df 92%, #d9dcca 99%, #bdc4ad 100%); overflow: hidden; }
.login-story::after { display: none; }
.login-story .mobile-brand { margin-bottom: 24px; font-size: 17px; }
.login-story .mobile-brand > span { width: 36px; height: 36px; border-radius: 12px 12px 12px 3px; font-family: 'STSong', 'SimSun', serif; font-size: 19px; }
.login-story-content { padding: 0; }
.login-story h1 { margin: 16px 0; font-size: clamp(32px, 3.2vw, 48px); line-height: 1.4; letter-spacing: -.025em; text-wrap: balance; }
.login-story-content > p { color: #526349; font-size: 13px; line-height: 1.9; margin: 0; }
.login-story :deep(.knowledge-orbit) { margin: 12px auto; }
.knowledge-flow { margin-top: 20px; padding-top: 20px; }
.knowledge-flow span { color: #526349; font-size: 11px; }
.login-story-footer { margin-top: 24px; color: #526349; letter-spacing: .5px; }
.login-panel { position: relative; display: grid; place-items: center; align-self: stretch; max-width: none; padding: 44px clamp(32px, 5vw, 76px); background: transparent; transform-origin: left center; transform-style: preserve-3d; }
.login-panel::before, .login-panel::after { content: ''; position: absolute; inset: 0; border-radius: 0 14px 14px 0; backface-visibility: hidden; pointer-events: none; }
.login-panel::before { background: linear-gradient(90deg, #d8d9c9 0%, #f5f3e8 5%, #fffdf3 16%, #fffdf3 97%, #f0eddf 100%); box-shadow: inset 1px 0 #a4ae9680; }
.login-panel::after { background: linear-gradient(90deg, #edeadb, #fffdf3 12%, #f5f2e5 92%, #d7d8c7); transform: rotateY(180deg); box-shadow: -16px 12px 36px #273b3226; }
.login-card { position: relative; max-width: 380px; transform: translateZ(1px); backface-visibility: hidden; }
.login-card h2 { margin: 0 0 12px; font-size: clamp(26px, 2.4vw, 32px); letter-spacing: -.025em; line-height: 1.4; }
.login-card .muted { font-size: 14px; line-height: 1.8; margin-bottom: 36px; }
.login-card label { font-size: 14px; font-weight: 500; margin-bottom: 22px; }
.login-card input { min-height: 52px; padding: 14px 16px; font-size: 16px; font-weight: 400; caret-color: var(--green); transition: border-color 160ms ease, background-color 160ms ease; }
.login-card input::placeholder { color: #71766b; font-size: 14px; }
.login-card input:hover:not(:disabled) { border-color: #81957b; }
.login-card input:disabled { color: #526349; background: #eef0e5; cursor: wait; }
.login-card .primary-button { display: flex; align-items: center; justify-content: center; gap: 12px; min-height: 52px; font-size: 14px; transition: background-color 160ms ease; }
.login-card .primary-button:hover:not(:disabled) { background: #1b4938; }
.login-card .primary-button:active:not(:disabled) { background: #153c2d; }
.login-card .primary-button:disabled { opacity: 1; background: #526d58; cursor: wait; }
.login-card .primary-button:focus-visible, .auth-switch a:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 4px; }
.auth-switch { font-size: 14px; margin-top: 24px; }
.auth-switch a { display: inline-flex; align-items: center; min-height: 44px; color: var(--green); text-underline-offset: 4px; }
.auth-switch a:hover { text-decoration: underline; }
.login-help { margin-top: 12px; }
.login-trust { margin-top: 32px; padding-top: 22px; font-size: 11px; line-height: 1.8; text-align: center; }
.auth-spinner { width: 16px; height: 16px; border: 2px solid #ffffff55; border-top-color: #fff; border-radius: 50%; animation: auth-spin 800ms linear infinite; }
.auth-announcement { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@keyframes auth-spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) and (min-width: 801px) {
  .login-page { padding: 24px; }
  .login-story { padding-inline: 28px; }
  .login-story :deep(.knowledge-orbit) { transform: scale(.82); transform-origin: center; width: 340px; margin-left: -12px; }
  .knowledge-flow { flex-direction: column; gap: 14px; }
}
@media (max-width: 800px) {
  .login-page { align-content: start; padding: 18px 14px 28px; }
  .login-book { grid-template-columns: minmax(0, 1fr); min-height: 0; perspective: 1400px; }
  .login-story { padding: 24px; border-radius: 5px 12px 0 0; background: linear-gradient(180deg, #eff0df 95%, #d9dcca); }
  .login-story .mobile-brand { margin-bottom: 20px; font-size: 16px; }
  .login-story-content { padding: 0; }
  .login-story h1 { margin: 0; font-size: 28px; line-height: 1.45; }
  .login-story :deep(.knowledge-orbit), .knowledge-flow, .login-story-footer { display: none; }
  .login-panel { padding: 32px 20px 24px; }
  .login-panel::before, .login-panel::after { border-radius: 0 0 12px 5px; }
  .login-card .muted { margin-bottom: 26px; }
  .login-trust { margin-top: 24px; }
}
@media (prefers-reduced-motion: reduce) {
  .auth-spinner { animation: none; }
  .login-card input, .login-card .primary-button { transition: none; }
}
</style>
