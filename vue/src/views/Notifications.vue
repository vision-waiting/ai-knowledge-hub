<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Bell, Check, Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { listNotifications, markNotificationRead, markAllNotificationsRead, notifyReadStateChanged, type KnowledgeNotification } from '@/api/notifications'
import { errorMessage } from '@/utils/errors'

const auth = useAuthStore()
const items = ref<KnowledgeNotification[]>([]), total = ref(0), unreadCount = ref(0), page = ref(1)
const unreadOnly = ref(false), loading = ref(false), busy = ref(false), error = ref('')
let generation = 0
async function load() {
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    const result = await listNotifications({ unreadOnly: unreadOnly.value, page: page.value, pageSize: 20 })
    if (current !== generation) return
    if (page.value > 1 && result.items.length === 0) { page.value = Math.max(1, Math.ceil(result.total / 20)); await load(); return }
    items.value = result.items; total.value = result.total; unreadCount.value = result.unreadCount
    notifyReadStateChanged()
  } catch (cause) { if (current === generation) { items.value = []; total.value = 0; error.value = errorMessage(cause) } }
  finally { if (current === generation) loading.value = false }
}
async function markRead(item?: KnowledgeNotification) {
  if (busy.value) return
  const current = generation
  busy.value = true; error.value = ''
  try {
    if (item) await markNotificationRead(item.id)
    else await markAllNotificationsRead()
    if (current === generation) { notifyReadStateChanged(); await load() }
  } catch (cause) { if (current === generation) error.value = errorMessage(cause) }
  finally { busy.value = false }
}
function filterChanged() { page.value = 1; void load() }
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
watch(() => auth.token, () => {
  generation++; items.value = []; total.value = 0; unreadCount.value = 0; page.value = 1
  if (auth.token) void load()
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <AppLayout>
    <div class="page-heading"><div><span class="section-kicker">知识库更新</span><h1>更新提醒</h1><p>关注的知识库有文档更新时，会在这里通知你。</p></div><div class="heading-actions"><el-button :icon="Refresh" :loading="loading" :disabled="busy" @click="load">刷新</el-button><el-button :icon="Check" :disabled="!unreadCount || loading || busy" @click="markRead()">全部已读</el-button></div></div>
    <div class="notification-toolbar"><span><strong>{{ unreadCount }}</strong> 条未读</span><el-switch v-model="unreadOnly" active-text="仅看未读" :disabled="loading || busy" @change="filterChanged" /></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-loading="loading" class="notification-list" aria-live="polite">
      <el-empty v-if="!loading && !error && !items.length" :description="unreadOnly ? '没有未读提醒' : '还没有更新提醒'">
        <p class="empty-help">进入知识库点击「关注更新」，即可接收之后的上传、处理和删除提醒。</p><router-link to="/knowledge">去关注知识库 →</router-link>
      </el-empty>
      <article v-for="item in items" :key="item.id" class="notification-item" :class="{ unread: !item.read }">
        <div class="notification-symbol" :class="{ failed: item.type === 'FAILED' }"><el-icon :size="21"><Bell /></el-icon></div>
        <div class="notification-content"><div class="notification-meta"><router-link :to="`/knowledge/${item.knowledgeBaseId}`">{{ item.knowledgeBaseName }}</router-link><time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time><span v-if="!item.read" class="unread-label">未读</span></div><h3>{{ item.message }}</h3><router-link :to="`/knowledge/${item.knowledgeBaseId}/documents`">查看知识库文档 →</router-link></div>
        <el-button v-if="!item.read" size="small" :disabled="busy || loading" @click="markRead(item)">标为已读</el-button>
      </article>
    </div>
    <el-pagination v-if="total > 20" v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" :disabled="loading || busy" @current-change="load" />
  </AppLayout>
</template>
<style scoped>
.notification-toolbar{display:flex;align-items:center;justify-content:space-between;margin:24px 0 16px;color:var(--el-text-color-secondary)}.notification-toolbar strong{color:var(--el-color-primary);font-size:20px;margin-right:4px}.notification-list{min-height:220px;border:1px solid var(--el-border-color-light);border-radius:16px;overflow:hidden;background:var(--el-bg-color)}.notification-item{display:flex;gap:16px;align-items:center;padding:23px 24px;border-bottom:1px solid var(--el-border-color-extra-light)}.notification-item:last-child{border-bottom:0}.notification-item.unread{background:var(--el-color-primary-light-9)}.notification-symbol{width:42px;height:42px;border-radius:12px;display:flex;align-items:center;justify-content:center;color:var(--el-color-primary);background:var(--el-fill-color-light);flex-shrink:0}.notification-symbol.failed{color:var(--el-color-danger)}.notification-content{flex:1;min-width:0}.notification-meta{display:flex;align-items:center;gap:12px;flex-wrap:wrap;font-size:12px;color:var(--el-text-color-secondary)}.notification-meta a{font-weight:600}.notification-content h3{font-size:15px;margin:8px 0;line-height:1.6;overflow-wrap:anywhere}.notification-content a,.empty-help+a{color:var(--el-color-primary);text-decoration:none;font-size:13px}.unread-label{font-size:11px;border-radius:8px;background:var(--el-color-primary);color:white;padding:2px 7px}.empty-help{max-width:420px;font-size:13px;color:var(--el-text-color-secondary);line-height:1.8;padding:0 16px}.el-pagination{justify-content:center;margin-top:20px}@media(max-width:600px){.notification-item{padding:18px 14px;gap:10px;flex-wrap:wrap}.notification-meta{gap:8px}.notification-item>.el-button{margin-left:52px}}
</style>
