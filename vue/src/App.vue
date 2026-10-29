<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute } from 'vue-router'
import BookCover from '@/components/BookCover.vue'
import { readingPreferences, getPageTurnDuration } from '@/utils/readingPreferences'
const route = useRoute()
const opened = ref(false), opening = ref(false)
const covered = computed(() => !opened.value && (route.name === 'login' || route.name === 'register'))
const focused = computed(() => readingPreferences.focusMode && route.meta.requiresAuth)
const openingDuration = computed(() => `${getPageTurnDuration() ? getPageTurnDuration() + 100 : 0}ms`)
async function reveal() {
  opened.value = true
  await nextTick()
  document.querySelector<HTMLInputElement>('.login-card input')?.focus({ preventScroll: true })
}
</script>
<template>
  <div class="book-world" :class="{ 'is-reading-focused': focused }" :style="{ '--book-opening-duration': openingDuration }">
    <div class="book-landscape-backdrop" aria-hidden="true"></div>
    <div class="book-reading" :class="{ 'under-cover': covered && !opening, 'opening-book': opening && covered }" :inert="covered || undefined" :aria-hidden="covered || undefined"><router-view /></div>
    <BookCover v-if="covered" @opening="opening = true" @opened="reveal" />
  </div>
</template>

<style>
.book-transition-overlay { position: fixed; inset: 0; z-index: 10000; overflow: hidden; pointer-events: none; }
.book-transition-backdrop { position: absolute; inset: 0; }
.book-transition-overlay > .login-page.login-page { background: transparent; }
.book-transition-overlay .login-panel { z-index: 3; will-change: transform; }
.book-transition-overlay *, .book-transition-overlay *::before, .book-transition-overlay *::after { animation: none !important; transition: none !important; }
</style>
