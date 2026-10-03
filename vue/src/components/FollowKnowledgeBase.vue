<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Bell, Check } from '@element-plus/icons-vue'
import { getSubscription, followKnowledgeBase, unfollowKnowledgeBase } from '@/api/notifications'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/errors'

const props = defineProps<{ knowledgeBaseId: number }>()
const auth = useAuthStore()
const following = ref(false), loading = ref(true), loaded = ref(false), error = ref('')
let generation = 0
async function load() {
  const current = ++generation
  following.value = false; loaded.value = false; loading.value = true; error.value = ''
  if (!auth.token || !Number.isSafeInteger(props.knowledgeBaseId) || props.knowledgeBaseId <= 0) { loading.value = false; return }
  try {
    const result = await getSubscription(props.knowledgeBaseId)
    if (current === generation) { following.value = result.following; loaded.value = true }
  } catch (cause) { if (current === generation) error.value = errorMessage(cause) }
  finally { if (current === generation) loading.value = false }
}
async function toggle() {
  if (loading.value || !loaded.value) return
  const current = generation
  loading.value = true; error.value = ''
  try {
    const result = await (following.value ? unfollowKnowledgeBase(props.knowledgeBaseId) : followKnowledgeBase(props.knowledgeBaseId))
    if (current === generation) following.value = result.following
  } catch (cause) { if (current === generation) error.value = errorMessage(cause) }
  finally { if (current === generation) loading.value = false }
}
watch(() => [props.knowledgeBaseId, auth.token], load, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <div class="follow-control">
    <el-button :icon="following ? Check : Bell" :loading="loading" :disabled="!loaded || loading" :type="following ? 'primary' : 'default'" :plain="following" :aria-pressed="following" :title="following ? '取消关注后不再接收新提醒' : '关注后接收此知识库的文档更新提醒'" @click="toggle">{{ following ? '已关注更新' : '关注更新' }}</el-button>
    <span v-if="error" class="follow-error" role="alert">{{ error }} <button v-if="!loaded" type="button" @click="load">重试</button></span>
  </div>
</template>
<style scoped>
.follow-control{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.follow-error{font-size:12px;color:var(--el-color-danger)}.follow-error button{border:0;background:none;color:var(--el-color-primary);cursor:pointer}
</style>
