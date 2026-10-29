<script setup lang="ts">
import { readingPreferences, preferencesSaved, updateReadingPreferences, type TurnSpeed } from '@/utils/readingPreferences'
function changeSpeed(event: Event) {
  updateReadingPreferences({ turnSpeed: (event.target as HTMLSelectElement).value as TurnSpeed })
}
</script>

<template>
  <div class="reading-controls" role="group" aria-label="阅读设置">
    <label>翻页<select :value="readingPreferences.turnSpeed" aria-label="翻页速度" @change="changeSpeed">
      <option value="relaxed">舒缓 · 1.5 秒</option>
      <option value="quick">快速 · 0.6 秒</option>
      <option value="off">关闭动画</option>
    </select></label>
    <button type="button" :aria-pressed="readingPreferences.focusMode" @click="updateReadingPreferences({ focusMode: !readingPreferences.focusMode })">{{ readingPreferences.focusMode ? '退出专注' : '专注阅读' }}</button>
    <small v-if="!preferencesSaved" role="status">浏览器无法保存设置，本次仍有效。</small>
  </div>
</template>
