<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getUnreadCount } from '@/api/notifications'

const auth = useAuthStore()
const count = ref(0), unavailable = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let generation = 0, pending = false
function stop() { if (timer) clearTimeout(timer); timer = undefined }
async function refresh() {
  if (!auth.token || pending) return
  stop()
  const current = generation
  pending = true
  try {
    const result = await getUnreadCount()
    if (current !== generation) return
    count.value = result.count; unavailable.value = false
    timer = setTimeout(refresh, 60000)
  } catch {
    if (current === generation) { count.value = 0; unavailable.value = true; stop() }
  } finally { if (current === generation) pending = false }
}
watch(() => [auth.token, auth.user?.id], () => {
  generation++; stop(); count.value = 0; pending = false; unavailable.value = false
  void refresh()
}, { immediate: true })
onMounted(() => window.addEventListener('knowledge-notifications-changed', refresh))
onBeforeUnmount(() => { generation++; stop(); window.removeEventListener('knowledge-notifications-changed', refresh) })
</script>

<template>
  <router-link class="notification-bell" to="/notifications" :aria-label="unavailable ? '更新提醒，暂时无法获取未读数量' : `更新提醒，${count} 条未读`" :title="unavailable ? '未读数量加载失败，进入提醒页重试' : '知识库更新提醒'">
    <el-icon :size="20"><Bell /></el-icon>
    <span v-if="count > 0" class="notification-count">{{ count > 99 ? '99+' : count }}</span>
    <span v-if="unavailable" class="notification-status">!</span>
  </router-link>
</template>
<style scoped>
.notification-bell{position:relative;display:inline-flex;align-items:center;justify-content:center;width:38px;height:38px;flex-shrink:0;border:1px solid var(--el-border-color-light);border-radius:12px;color:var(--el-text-color-regular);background:var(--el-bg-color);text-decoration:none}.notification-bell:hover{color:var(--el-color-primary);border-color:var(--el-color-primary-light-5)}.notification-count{position:absolute;top:-5px;right:-7px;min-width:18px;padding:0 4px;height:18px;line-height:18px;border-radius:10px;background:#ef6464;color:white;font-size:10px;text-align:center}.notification-status{position:absolute;top:-3px;right:0;color:var(--el-color-warning);font-weight:700}
</style>
