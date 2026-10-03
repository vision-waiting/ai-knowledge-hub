<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { http } from '@/api/http'
import { errorMessage } from '@/utils/errors'
interface Card {id:number;front:string;back:string;studyId:number;knowledgeBaseId:number;intervalDays:number;repetitions:number;dueAt:string}
const cards=ref<Card[]>([]),revealed=ref<number[]>([]),all=ref(false),busy=ref(false),error=ref(''),loading=ref(false)
async function load(){loading.value=true;error.value='';try{cards.value=await http.get('/review',{params:{all:all.value}}) as unknown as Card[];revealed.value=[]}catch(cause){error.value=errorMessage(cause)}finally{loading.value=false}}
onMounted(load)
async function grade(card:Card,rating:string){if(busy.value)return;busy.value=true;error.value='';try{await http.post(`/review/${card.id}`,{rating});await load()}catch(cause){error.value=errorMessage(cause)}finally{busy.value=false}}
</script>
<template><AppLayout><div class="review-plan"><div class="page-heading"><div><h1>复习计划</h1><p>从学习助手把知识卡片加入计划，再按掌握程度安排下次复习。</p></div><router-link to="/study">进入学习助手 →</router-link></div>
 <label><input v-model="all" type="checkbox" :disabled="busy||loading" @change="load" /> 显示未来计划</label><button :disabled="busy||loading" @click="load">刷新</button>
 <p v-if="error" role="alert">{{error}}</p><p v-if="loading" role="status">正在加载…</p><p v-else-if="!cards.length&&!error">{{all?'还没有复习卡片。':'当前没有到期卡片，稍后再来。'}}</p>
 <article v-for="card in cards" :key="card.id"><small>连续记住 {{card.repetitions}} 次 · {{card.intervalDays}} 天间隔 · 下次复习 {{card.dueAt}}</small><h2>{{card.front}}</h2>
 <button v-if="!revealed.includes(card.id)" :data-test="`reveal-${card.id}`" @click="revealed.push(card.id)">查看答案</button><template v-else><p class="review-answer">{{card.back}}</p><div v-if="!all" class="review-actions"><button :disabled="busy" @click="grade(card,'AGAIN')">没记住 · 10 分钟后</button><button :data-test="`good-${card.id}`" :disabled="busy" @click="grade(card,'GOOD')">记住了</button><button :disabled="busy" @click="grade(card,'EASY')">很熟悉</button></div><p v-else>切回到期卡片列表后可提交复习结果。</p></template></article>
 </div></AppLayout></template>
<style scoped>.review-plan{max-width:1000px}.review-plan article{padding:24px;margin:18px 0;border:1px solid var(--el-border-color);border-radius:12px;background:var(--el-bg-color)}.review-plan h2{font-size:19px}.review-answer{white-space:pre-wrap;overflow-wrap:anywhere}.review-actions{display:flex;flex-wrap:wrap;gap:12px}.review-plan button{font:inherit;padding:9px 14px;border:1px solid var(--el-border-color);border-radius:8px;background:var(--el-bg-color);color:var(--el-text-color-primary);cursor:pointer}.review-plan button:disabled{opacity:.5}.review-plan [role=alert]{color:var(--el-color-danger)}</style>
