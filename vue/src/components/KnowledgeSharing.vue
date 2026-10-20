<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Star, Share } from '@element-plus/icons-vue'
import { http } from '@/api/http'
import { errorMessage } from '@/utils/errors'
import type { KnowledgeBase } from '@/types'
const props = defineProps<{ item: KnowledgeBase }>()
const starred = ref(false), count = ref(0), busy = ref(false), error = ref(''), message = ref('')
const link = computed(() => `${window.location.origin}/knowledge/${props.item.id}`)
watch(() => props.item, item => { starred.value = !!item.starred; count.value = item.starCount || 0; error.value = ''; message.value = '' }, { immediate: true })
async function toggle() {
  if (busy.value) return
  busy.value = true; error.value = ''
  const id = props.item.id
  try {
    if (starred.value) { await http.delete(`/knowledge-bases/${id}/star`); if (id === props.item.id) { starred.value = false; count.value = Math.max(0, count.value - 1) } }
    else { const result = await http.put(`/knowledge-bases/${id}/star`) as unknown as { starred: boolean; starCount: number }; if (id === props.item.id) { starred.value = result.starred; count.value = result.starCount } }
  } catch (cause) { if (id === props.item.id) error.value = errorMessage(cause) }
  finally { busy.value = false }
}
async function copy() {
  message.value = ''; error.value = ''
  try { await navigator.clipboard.writeText(link.value); message.value = '链接已复制' }
  catch { error.value = '未能自动复制，请选中下方链接手动复制。' }
}
</script>
<template>
  <section class="knowledge-sharing" aria-label="收藏与分享">
    <div class="sharing-controls">
      <button data-test="star" class="ghost-button" :aria-pressed="starred" :disabled="busy" @click="toggle"><Star width="16" aria-hidden="true" />{{ starred ? '已收藏' : '收藏' }} · {{ count }}</button>
      <button class="ghost-button" @click="copy"><Share width="16" aria-hidden="true" />复制分享链接</button>
      <span>{{ item.shared ? '公开 · 所有登录用户可读' : '私有 · 仅所有者、受邀成员及系统管理员可访问' }}</span>
    </div>
    <label class="share-link">分享链接<input :value="link" readonly aria-label="分享链接" @focus="($event.target as HTMLInputElement).select()" /></label>
    <p class="muted">{{ item.shared ? '链接包含本库的文档和知识条目。阅读者可以提出修改建议，审核通过后才会发布。' : '分享链接不会授予访问权限；可在知识库列表编辑可见范围，或在成员权限中添加成员。' }}</p>
    <p v-if="message" role="status">{{ message }}</p><p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>
<style scoped>
.knowledge-sharing { margin: 20px 0 28px; padding: 18px 0; border-block: 1px solid var(--line); }
.sharing-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.sharing-controls span, .share-link, p { font-size: 13px; }
.share-link { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.share-link input { min-width: 0; flex: 1; max-width: 560px; border: 1px solid var(--line); border-radius: 8px; padding: 9px 12px; color: var(--text); background: var(--surface, white); font: inherit; }
button[aria-pressed=true] { color: var(--green); border-color: var(--green); }
button:disabled { opacity: .6; cursor: wait; }
button:focus-visible, input:focus-visible { outline: 2px solid var(--green); outline-offset: 3px; }
p { line-height: 1.7; margin: 10px 0 0; }
[role=alert] { color: #a7352b; }
@media (max-width: 600px) { .share-link { align-items: stretch; flex-direction: column; gap: 6px; } }
</style>
