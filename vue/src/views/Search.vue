<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import SourceDrawer from '@/components/SourceDrawer.vue'
import { searchKnowledge } from '@/api/search'
import { listKnowledgeBases } from '@/api/knowledge'
import { errorMessage } from '@/utils/errors'
import type { Citation, SearchResult } from '@/types'

const route = useRoute()
const knowledgeBaseId = ref<number>()
const scopeError = ref('')
function applyScope() {
  const requested = Number(route.query.knowledgeBaseId)
  const match = bases.value.find(base => base.id === requested)
  knowledgeBaseId.value = route.query.knowledgeBaseId ? match?.id : bases.value[0]?.id
  scopeError.value = route.query.knowledgeBaseId && !match ? '当前知识库不可访问，请选择其他知识库。' : ''
}
const bases = ref<{ id: number; name: string }[]>([])
const query = ref(''), fileName = ref(''), fileType = ref(''), tag = ref('')
const results = ref<SearchResult[]>([])
const loading = ref(false), basesLoading = ref(false), searched = ref(false)
const error = ref(''), basesError = ref(''), searchedQuery = ref('')
const offset = ref(0), hasNext = ref(false), citation = ref<Citation>()
const pageSize = 20
let request = 0, basesRequest = 0
const terms = computed(() => [...new Set(searchedQuery.value.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean))].sort((a,b) => b.length-a.length))
function highlight(text: string) {
  const parts: {text:string; hit:boolean}[] = []
  const lower = text.toLocaleLowerCase()
  let start = 0, index = 0
  while (index < text.length) {
    const term = terms.value.find(term => lower.startsWith(term, index))
    if (!term) { index++; continue }
    if (index > start) parts.push({text:text.slice(start,index),hit:false})
    parts.push({text:text.slice(index,index+term.length),hit:true}); index += term.length; start=index
  }
  if (start < text.length) parts.push({text:text.slice(start),hit:false})
  return parts
}
async function loadBases() {
  const current = ++basesRequest
  basesLoading.value = true; basesError.value = ''
  try { const value = await listKnowledgeBases(); if (current !== basesRequest) return; bases.value = value; applyScope() }
  catch (cause) { if (current === basesRequest) basesError.value = errorMessage(cause) }
  finally { if (current === basesRequest) basesLoading.value = false }
}
function reset() { request++; loading.value=false; results.value=[]; searched.value=false; error.value=''; offset.value=0; hasNext.value=false; citation.value=undefined }
watch([knowledgeBaseId, query, fileName, fileType, tag], reset)
watch(knowledgeBaseId, value => { if (value) scopeError.value = '' })
watch(() => route.query.knowledgeBaseId, () => { if (!basesLoading.value) applyScope() })
onBeforeUnmount(() => {request++; basesRequest++})
void loadBases()
async function run(nextOffset = 0) {
  if (!knowledgeBaseId.value || !query.value.trim()) return
  const current = ++request
  loading.value=true; error.value=''; results.value=[]; citation.value=undefined
  offset.value=nextOffset; searchedQuery.value=query.value.trim()
  try {
    const value = await searchKnowledge(knowledgeBaseId.value, query.value.trim(), {fileName:fileName.value.trim() || undefined, fileType:fileType.value || undefined, tag:tag.value.trim() || undefined, offset:nextOffset, limit:pageSize+1})
    if (current !== request) return
    hasNext.value=value.length>pageSize; results.value=value.slice(0,pageSize); searched.value=true
  } catch (cause) { if(current===request) error.value=errorMessage(cause) }
  finally { if(current===request) loading.value=false }
}
function openSource(item: SearchResult) { citation.value = {index:1,documentId:item.documentId,documentName:item.documentName,chunkId:item.chunkId,pageNumber:item.pageNumber,excerpt:item.excerpt} }
</script>
<template>
  <AppLayout>
    <div class="page-heading"><div><h1>全文搜索</h1><p>搜索知识库内已完成文档的正文；多个关键词按匹配数量排序。</p></div></div>
    <p v-if="scopeError" role="alert" class="inline-error">{{ scopeError }}</p>
    <p v-if="knowledgeBaseId" class="scope-notice">搜索范围：<strong>{{ bases.find(base => base.id === knowledgeBaseId)?.name }}</strong> · 仅搜索已完成处理的文档</p>
    <div class="toolbar search-toolbar">
      <el-select v-model="knowledgeBaseId" aria-label="搜索知识库" placeholder="选择知识库" :loading="basesLoading"><el-option v-for="base in bases" :key="base.id" :label="base.name" :value="base.id" /></el-select>
      <el-input v-model="query" aria-label="正文关键词" placeholder="输入关键词" :maxlength="200" clearable @keyup.enter="run()" />
      <el-button type="primary" :loading="loading" :disabled="!knowledgeBaseId || !query.trim()" @click="run()">搜索</el-button>
    </div>
    <details class="filter-disclosure" open><summary>高级筛选 · 文件与标签</summary><div class="toolbar search-filters">
      <el-input v-model="fileName" aria-label="文件名筛选" placeholder="文件名包含" :maxlength="255" clearable @keyup.enter="run()" />
      <el-select v-model="fileType" aria-label="文件类型筛选" placeholder="全部文件类型" clearable><el-option v-for="type in ['pdf','docx','txt','md']" :key="type" :label="type.toUpperCase()" :value="type" /></el-select>
      <el-input v-model="tag" aria-label="标签筛选" placeholder="标签（精确匹配）" :maxlength="100" clearable @keyup.enter="run()" />
    </div></details>
    <div v-if="basesError" role="alert"><el-alert :title="basesError" type="error" :closable="false" /><el-button data-test="retry-bases" @click="loadBases">重试加载知识库</el-button></div>
    <div v-else-if="error" role="alert"><el-alert :title="error" type="error" :closable="false" /><el-button @click="run(offset)">重试搜索</el-button></div>
    <p v-else-if="loading" role="status">正在搜索正文…</p>
    <el-empty v-else-if="!basesLoading && !bases.length" description="暂无可访问的知识库" />
    <el-empty v-else-if="!results.length && !basesLoading" :description="searched ? '未找到匹配内容，请调整关键词或筛选条件' : '选择知识库并输入关键词开始搜索'" />
    <el-card v-for="item in results" :key="item.chunkId" class="search-result">
      <button :data-test="`source-${item.chunkId}`" class="search-source" @click="openSource(item)"><strong>{{ item.documentName }}</strong><span>片段 {{ (item.chunkIndex ?? 0) + 1 }} · 查看原文</span></button>
      <p class="search-excerpt"><template v-for="(part,index) in highlight(item.excerpt)" :key="index"><mark v-if="part.hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></p>
    </el-card>
    <div v-if="searched && !loading && !error" class="search-pagination"><el-button :disabled="offset === 0" @click="run(Math.max(0,offset-pageSize))">上一页</el-button><span>第 {{ offset/pageSize+1 }} 页</span><el-button :disabled="!hasNext" @click="run(offset+pageSize)">下一页</el-button></div>
    <SourceDrawer :document-id="citation?.documentId" :citation="citation" @close="citation=undefined" />
  </AppLayout>
</template>
<style scoped>
.search-toolbar .el-select { min-width: 200px; }
.search-filters { margin: 12px 0 20px; flex-wrap: wrap; }
.search-filters > * { flex: 1; min-width: 160px; }
.search-result { margin: 14px 0; }
.search-source { display: flex; flex-wrap: wrap; gap: 10px; width: 100%; border: 0; background: transparent; padding: 0; text-align: left; cursor: pointer; color: var(--el-color-primary); }
.search-source span { color: var(--el-text-color-secondary); font-size: 12px; }
.search-excerpt { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
mark { background: #fef08a; color: #292524; border-radius: 2px; }
.search-pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin: 24px 0; }
</style>
