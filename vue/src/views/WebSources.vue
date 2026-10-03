<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { listKnowledgeBases } from '@/api/knowledge'
import { http } from '@/api/http'
import { errorMessage } from '@/utils/errors'
import type { KnowledgeBase } from '@/types'
interface Source { id: number; url: string; title: string; documentId: number; status: string; checkedAt: string }
const bases=ref<KnowledgeBase[]>([]),baseId=ref<number>(),url=ref(''),sources=ref<Source[]>([]),busy=ref(false),loading=ref(false),error=ref(''),notice=ref('')
let request=0;onBeforeUnmount(()=>request++)
async function init(){error.value='';try{bases.value=(await listKnowledgeBases()).filter(b=>b.canWrite);baseId.value=bases.value[0]?.id}catch(cause){error.value=errorMessage(cause)}}
onMounted(init);watch(baseId,()=>{notice.value='';void load()})
async function load(){const id=baseId.value,current=++request;sources.value=[];if(!id)return;loading.value=true;error.value='';try{const result=await http.get(`/knowledge-bases/${id}/web-sources`) as unknown as Source[];if(current===request)sources.value=result}catch(cause){if(current===request)error.value=errorMessage(cause)}finally{if(current===request)loading.value=false}}
async function submit(source?:Source){if(!baseId.value||busy.value)return;busy.value=true;error.value='';notice.value='';try{
 const result=await http.post(`/knowledge-bases/${baseId.value}/web-sources${source?`/${source.id}/check`:''}`,source?undefined:{url:url.value.trim()},{timeout:120000}) as unknown as Source
 notice.value=source&&result.documentId===source.documentId&&result.status==='SUCCESS'?'检查完成，正文没有变化。':'已提交文档处理，请在文档管理中查看解析进度。';if(!source)url.value='';await load()
}catch(cause){error.value=errorMessage(cause)}finally{busy.value=false}}
</script>
<template><AppLayout><div class="web-sources"><div class="page-heading"><div><h1>网页资料</h1><p>导入公开网页，手动检查正文更新。变化后保存为新版本，旧版本引用继续保留。</p></div></div>
 <label>知识库 <select v-model="baseId" :disabled="busy" aria-label="知识库"><option v-for="base in bases" :key="base.id" :value="base.id">{{base.name}}</option></select></label>
 <form v-if="baseId" @submit.prevent="submit()"><input v-model="url" aria-label="网页地址" type="url" placeholder="https://example.com/manual" maxlength="2000" required :disabled="busy" /><button :disabled="busy">{{busy?'正在读取…':'导入网页'}}</button></form>
 <p class="muted">支持公开 HTTPS 静态 HTML 页面，最大 2MB。需要登录、验证码或 JavaScript 渲染的页面暂不支持。只导入你有权使用的内容。</p>
 <p v-if="error" role="alert">{{error}} <button @click="bases.length?load():init()" :disabled="busy">重试</button></p><p v-if="notice" role="status">{{notice}}</p><p v-if="loading">正在加载…</p>
 <p v-if="!bases.length&&!error">暂无可编辑的知识库。</p>
 <article v-for="source in sources" :key="source.id"><h2>{{source.title}}</h2><p class="source-url">{{source.url}}</p><p>文档状态：{{source.status}} · 最近检查：{{source.checkedAt}}</p><button :disabled="busy" @click="submit(source)">检查更新</button> <router-link :to="`/knowledge/${baseId}/documents`">查看文档与版本</router-link></article><p v-if="baseId&&!loading&&!sources.length&&!error">尚未导入网页。</p>
 </div></AppLayout></template>
<style scoped>.web-sources{max-width:1000px}.web-sources form{display:flex;gap:12px;margin:20px 0}.web-sources input{flex:1;min-width:0}.web-sources input,.web-sources button,.web-sources select{font:inherit;border:1px solid var(--el-border-color);border-radius:8px;padding:10px;background:var(--el-bg-color);color:var(--el-text-color-primary)}.web-sources article{padding:20px;border:1px solid var(--el-border-color);border-radius:12px;margin:16px 0}.web-sources h2{font-size:18px}.source-url{overflow-wrap:anywhere}.web-sources button{cursor:pointer}.web-sources button:disabled{opacity:.5;cursor:wait}.web-sources [role=alert]{color:var(--el-color-danger)}</style>
