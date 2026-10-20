<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
const emit = defineEmits<{ opened: []; opening: [] }>()
const scene = ref<HTMLElement>(), cover = ref<HTMLElement>(), volume = ref<HTMLElement>()
const opening = ref(false)
let disposed = false
const animations: Animation[] = []
async function open() {
  if (opening.value || disposed) return
  opening.value = true
  emit('opening')
  window.removeEventListener('keydown', key)
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  try {
    if (!reduced && cover.value?.animate) {
      const play = (element: HTMLElement | undefined, frames: Keyframe[], options: KeyframeAnimationOptions) => {
        const animation = element?.animate(frames, options)
        if (animation) { animations.push(animation); return animation.finished.catch(() => {}) }
      }
      await Promise.all([
        play(cover.value, [{ transform: 'rotateY(0deg)' }, { transform: 'rotateY(-165deg)' }], { duration: 1000, easing: 'cubic-bezier(.45,0,.2,1)', fill: 'forwards' }),
        play(volume.value, [{ transform: 'scale(1)' }, { transform: 'scale(2.2)' }], { duration: 1100, easing: 'cubic-bezier(.16,1,.3,1)', fill: 'forwards' }),
        play(scene.value, [{ opacity: 1 }, { opacity: 1, offset: .4 }, { opacity: 0 }], { duration: 1100, fill: 'forwards' }),
      ])
    }
  } finally {
    if (!disposed) emit('opened')
  }
}
function key(event: KeyboardEvent) {
  if (event.ctrlKey || event.metaKey || event.altKey || event.repeat || ['Shift', 'Control', 'Alt', 'Meta'].includes(event.key)) return
  event.preventDefault()
  void open()
}
onMounted(() => { window.addEventListener('keydown', key); cover.value?.focus({ preventScroll: true }) })
onBeforeUnmount(() => { disposed = true; window.removeEventListener('keydown', key); animations.forEach(a => a.cancel()) })
</script>
<template>
  <div ref="scene" class="book-cover-scene" @click="open">
    <div ref="volume" class="closed-volume">
      <div class="closed-paper" aria-hidden="true"></div>
      <button ref="cover" class="closed-cover" :disabled="opening" aria-label="打开书本，进入登录界面" @click.stop="open">
        <span class="cover-imprint">智能知识中心</span>
        <span class="cover-symbol" aria-hidden="true">知</span>
        <span class="cover-title">让知识有序，<br />让答案有据。</span>
        <span class="cover-divider" aria-hidden="true"></span>
        <span class="cover-subtitle">知识管理 · 智能问答</span>
        <span class="cover-bottom">你的知识，从这一页开始</span>
      </button>
    </div>
    <p class="cover-instruction">点击书本，或按任意键翻开 <span aria-hidden="true">↗</span></p>
  </div>
</template>
