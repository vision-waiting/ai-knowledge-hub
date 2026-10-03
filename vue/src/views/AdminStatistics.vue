<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import StatisticsView from '@/components/StatisticsView.vue'
import { statistics } from '@/api/admin'
import { errorMessage } from '@/utils/errors'
import type { DashboardOverview } from '@/types'
const overview = ref<DashboardOverview>(); const loading = ref(false); const error = ref('')
async function load() { loading.value = true; error.value = ''; try { overview.value = await statistics() } catch (cause) { error.value = errorMessage(cause) } finally { loading.value = false } }
onMounted(load)
</script>
<template><AppLayout><div class="page-heading"><h1>统计分析</h1><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon /><div v-loading="loading"><StatisticsView :overview="overview" admin /></div></AppLayout></template>
