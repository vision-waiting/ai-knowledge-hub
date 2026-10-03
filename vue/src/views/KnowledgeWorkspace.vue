<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import SourceDrawer from '@/components/SourceDrawer.vue'
import { listKnowledgeBases } from '@/api/knowledge'
import { http } from '@/api/http'
import { renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/errors'
import type { KnowledgeBase } from '@/types'
interface Entry { id: number; kind: string; title: string; body: string; aliases?: string; sourceDocumentId?: number; sortOrder: number; updatedAt: string }
interface Issue { messageId: number; question: string; answer: string; reason: string; status: string }
interface Member { userId: number; username: string; nickname?: string; role: string }
const route = useRoute(), bases = ref<KnowledgeBase[]>([]), baseId = ref<number>(), tab = ref('content'), kind = ref('FAQ')
const entries = ref<Entry[]>([]), issues = ref<Issue[]>([]), members = ref<Member[]>([]), issueStatus = ref('OPEN'), offset = ref(0), hasMore = ref(false)
const loading = ref(false), saving = ref(false), error = ref(''), editing = ref(false), selectedSource = ref<number>(), username = ref(''), memberRole = ref('VIEWER')
const canManage = computed(() => bases.value.find(b => b.id === baseId.value)?.canManage === true)
const blank = () => ({ id: undefined as number | undefined, title: '', body: '', aliases: '', sourceDocumentId: undefined as number | undefined, sortOrder: 0 })
const draft = reactive(blank()); let request = 0
async function init() {
  error.value = ''; loading.value = true
  try { bases.value = await listKnowledgeBases(); const id = Number(route.query.knowledgeBaseId); baseId.value = bases.value.find(b => b.id === id)?.id ?? bases.value[0]?.id }
  catch (cause) { error.value = errorMessage(cause) } finally { loading.value = false }
}
onMounted(init); onBeforeUnmount(() => request++)
watch(baseId, () => { tab.value = 'content'; offset.value = 0; editing.value = false; selectedSource.value = undefined; void load() })
watch([tab, kind, issueStatus], () => { offset.value = 0; editing.value = false; void load() })
async function load() {
  const id = baseId.value, current = ++request
  entries.value = []; issues.value = []; members.value = []; hasMore.value = false
  if (!id) return
  loading.value = true; error.value = ''
  try {
    if (tab.value === 'content') {
      const result = await http.get(`/knowledge-bases/${id}/content`, { params: { kind: kind.value, offset: offset.value, limit: 30 } }) as unknown as { items: Entry[]; hasMore: boolean }
      if (current === request) { entries.value = result.items; hasMore.value = result.hasMore }
    } else if (tab.value === 'feedback') {
      const result = await http.get('/feedback/issues', { params: { knowledgeBaseId: id, status: issueStatus.value, offset: offset.value } }) as unknown as Issue[]
      if (current === request) { issues.value = result; hasMore.value = result.length === 50 }
    } else {
      const result = await http.get(`/knowledge-bases/${id}/members`) as unknown as Member[]
      if (current === request) members.value = result
    }
  } catch (cause) { if (current === request) error.value = errorMessage(cause) }
  finally { if (current === request) loading.value = false }
}
async function mutate(action: () => Promise<unknown>) {
  if (saving.value) return
  saving.value = true; error.value = ''
  try { await action(); editing.value = false; await load() } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}
function edit(entry?: Entry) { Object.assign(draft, blank(), entry || {}); editing.value = true }
function saveEntry() {
  if (!draft.title.trim() || !draft.body.trim()) { error.value = '标题和正文不能为空'; return }
  const payload = { kind: kind.value, title: draft.title.trim(), body: draft.body, aliases: draft.aliases, sourceDocumentId: draft.sourceDocumentId || null, sortOrder: draft.sortOrder }
  void mutate(() => draft.id ? http.put(`/knowledge-bases/${baseId.value}/content/${draft.id}`, payload) : http.post(`/knowledge-bases/${baseId.value}/content`, payload))
}
function removeEntry(entry: Entry) { if (window.confirm(`删除“${entry.title}”？`)) void mutate(() => http.delete(`/knowledge-bases/${baseId.value}/content/${entry.id}`)) }
function saveMember() { if (username.value.trim()) void mutate(() => http.put(`/knowledge-bases/${baseId.value}/members`, { username: username.value.trim(), role: memberRole.value })) }
function removeMember(member: Member) { if (window.confirm(`移除 ${member.username} 的成员权限？`)) void mutate(() => http.delete(`/knowledge-bases/${baseId.value}/members/${member.userId}`)) }
function page(direction: number) { offset.value = Math.max(0, offset.value + direction * (tab.value === 'feedback' ? 50 : 30)); void load() }
</script>
<template><AppLayout><div class="knowledge-workspace">
  <div class="page-heading"><div><h1>知识内容与维护</h1><p>整理标准问答、术语和阅读路线，持续完善资料。</p></div></div>
  <label>知识库 <select v-model="baseId" :disabled="saving" aria-label="知识库"><option v-for="b in bases" :key="b.id" :value="b.id">{{ b.name }}</option></select></label>
  <div class="workspace-tabs"><button @click="tab = 'content'" :disabled="saving" :aria-pressed="tab === 'content'">知识内容</button><template v-if="canManage"><button data-test="tab-feedback" :disabled="saving" @click="tab = 'feedback'" :aria-pressed="tab === 'feedback'">待补知识</button><button :disabled="saving" @click="tab = 'members'" :aria-pressed="tab === 'members'">成员权限</button></template></div>
  <p v-if="error" class="workspace-error" role="alert">{{ error }} <button @click="bases.length ? load() : init()">重试</button></p>
  <p v-if="loading" role="status">正在加载…</p><p v-if="!loading && !bases.length && !error">暂无可访问的知识库。</p>
  <template v-if="baseId && tab === 'content'">
    <div class="workspace-toolbar"><label>内容类型 <select v-model="kind" :disabled="saving"><option value="FAQ">标准问答 FAQ</option><option value="TERM">术语与别名</option><option value="READING">阅读路线</option></select></label><button v-if="canManage" data-test="new-entry" :disabled="saving" @click="edit()">新建条目</button></div>
    <p class="muted">这里发布的是人工维护内容；不会自动加入文档检索索引。</p>
    <form v-if="editing && canManage" class="workspace-editor" @submit.prevent="saveEntry">
      <label>标题<input v-model="draft.title" aria-label="标题" maxlength="200" required :disabled="saving" /></label>
      <label>正文（Markdown）<textarea v-model="draft.body" aria-label="正文" rows="7" maxlength="20000" required :disabled="saving" /></label>
      <label v-if="kind === 'TERM'">别名<input v-model="draft.aliases" maxlength="500" :disabled="saving" /></label>
      <label>来源文档 ID（可选）<input v-model.number="draft.sourceDocumentId" type="number" min="1" :disabled="saving" /></label>
      <label>显示顺序<input v-model.number="draft.sortOrder" type="number" min="0" max="10000" :disabled="saving" /></label>
      <button data-test="save-entry" type="submit" :disabled="saving">保存并发布</button><button type="button" :disabled="saving" @click="editing = false">取消</button>
    </form>
    <article v-for="entry in entries" :key="entry.id"><h2>{{ entry.title }}</h2><p v-if="entry.aliases">别名：{{ entry.aliases }}</p><div class="markdown-body" v-html="renderMarkdown(entry.body)" /><small>更新于 {{ entry.updatedAt }}</small><div class="workspace-toolbar"><button v-if="entry.sourceDocumentId" @click="selectedSource = entry.sourceDocumentId">查看来源</button><template v-if="canManage"><button :disabled="saving" @click="edit(entry)">编辑</button><button :disabled="saving" @click="removeEntry(entry)">删除</button></template></div></article>
    <p v-if="!loading && !entries.length && !error">该类型暂无条目。</p>
  </template>
  <template v-if="tab === 'feedback' && canManage"><label>处理状态 <select v-model="issueStatus" :disabled="saving"><option value="OPEN">待处理</option><option value="RESOLVED">已处理</option></select></label><p class="muted">仅展示用户主动提交“需改进”的这一条问答；不会展示其他私人会话。多人反复遇到「资料不足」的问题见 <router-link class="inline-link" :to="`/knowledge/${baseId}/insights`">知识缺口分析 →</router-link></p>
    <article v-for="issue in issues" :key="issue.messageId"><h2>{{ issue.question || '未记录问题' }}</h2><p class="answer-text">{{ issue.answer }}</p><p>原因：{{ issue.reason || '未填写' }}</p><button :data-test="`resolve-${issue.messageId}`" :disabled="saving" @click="mutate(() => http.put(`/feedback/issues/${issue.messageId}`, { status: issueStatus === 'OPEN' ? 'RESOLVED' : 'OPEN' }))">{{ issueStatus === 'OPEN' ? '标记已处理' : '重新打开' }}</button></article><p v-if="!loading && !issues.length && !error">暂无此状态的问题。</p>
  </template>
  <template v-if="tab === 'members' && canManage"><p>查看者可阅读和问答；编辑者可维护文档；管理者可维护知识库设置、内容与成员。共享库仍对所有登录用户可读。</p>
    <form class="workspace-toolbar" @submit.prevent="saveMember"><input v-model="username" aria-label="成员用户名" placeholder="已注册的用户名" maxlength="50" required :disabled="saving" /><select v-model="memberRole" aria-label="成员角色" :disabled="saving"><option value="VIEWER">查看者</option><option value="EDITOR">编辑者</option><option value="MANAGER">管理者</option></select><button :disabled="saving">添加 / 更新成员</button></form>
    <article v-for="member in members" :key="member.userId"><strong>{{ member.nickname || member.username }}</strong> · {{ member.username }} · {{ {VIEWER:'查看者',EDITOR:'编辑者',MANAGER:'管理者'}[member.role as 'VIEWER'] || member.role }} <button :disabled="saving" @click="username=member.username; memberRole=member.role">修改角色</button><button :disabled="saving" @click="removeMember(member)">移除</button></article><p v-if="!loading && !members.length && !error">尚未添加成员。所有者和系统管理员保留原有权限。</p>
  </template>
  <div v-if="tab !== 'members' && (offset || hasMore)" class="workspace-toolbar"><button :disabled="!offset || loading || saving" @click="page(-1)">上一页</button><span>第 {{ Math.floor(offset / (tab === 'feedback' ? 50 : 30)) + 1 }} 页</span><button :disabled="!hasMore || loading || saving" @click="page(1)">下一页</button></div>
  <SourceDrawer :document-id="selectedSource" @close="selectedSource = undefined" />
</div></AppLayout></template>
<style scoped>
.knowledge-workspace { max-width: 1100px; }
.knowledge-workspace > label, .workspace-toolbar label { display: inline-flex; align-items: center; gap: 10px; font-size: 13px; color: var(--muted); }
.workspace-tabs { display: inline-flex; gap: 4px; padding: 4px; margin: 20px 0 8px; border-radius: 10px; background: #e9efec; }
.workspace-tabs button { border: 0 !important; background: transparent !important; padding: 8px 16px !important; color: var(--muted) !important; }
.workspace-tabs button[aria-pressed=true] { background: #fff !important; color: var(--green) !important; font-weight: 600; box-shadow: 0 1px 3px #16302314; }
.workspace-toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 16px 0; }
.knowledge-workspace button, .knowledge-workspace input, .knowledge-workspace select, .knowledge-workspace textarea { font: inherit; font-size: 13px; border: 1px solid #d5dfdb; border-radius: 8px; padding: 8px 12px; background: #fff; color: var(--el-text-color-regular); outline: 0; }
.knowledge-workspace input:focus, .knowledge-workspace select:focus, .knowledge-workspace textarea:focus { border-color: var(--green); box-shadow: 0 0 0 3px #247b6920; }
.knowledge-workspace button { cursor: pointer; }
.knowledge-workspace button:hover:not(:disabled) { border-color: #91bdb4; color: var(--green); }
.knowledge-workspace button[data-test="new-entry"], .knowledge-workspace button[data-test="save-entry"] { background: var(--green); border-color: var(--green); color: #fff; }
.knowledge-workspace button:disabled { opacity: .5; cursor: default; }
.knowledge-workspace article, .workspace-editor { padding: 20px 22px; margin: 14px 0; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.knowledge-workspace article:hover { border-color: #c4ddd4; }
.knowledge-workspace article small { display: block; margin-top: 10px; color: var(--muted); font-size: 12px; }
.knowledge-workspace h2 { font-size: 16px; margin-bottom: 10px; }
.workspace-editor label { display: block; margin: 12px 0; font-size: 13px; color: var(--muted); }
.workspace-editor input, .workspace-editor textarea { display: block; width: 100%; box-sizing: border-box; margin-top: 6px; }
.workspace-editor button { margin-right: 8px; }
.workspace-error { padding: 12px 14px; border-radius: 8px; background: #fff3ee; color: #a7352b; font-size: 13px; }
.answer-text { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--el-text-color-regular); font-size: 14px; line-height: 1.8; padding: 10px 12px; border-radius: 8px; background: #f6f9f8; }
.inline-link { color: var(--green); }
</style>
