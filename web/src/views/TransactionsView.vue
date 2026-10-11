<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import TransactionTable from '../components/TransactionTable.vue'
import { api } from '../api'
import type { Page, TransactionFilter, TransactionRecord, TransactionType } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { errorText } from '../utils/format'
import { Download, Filter, RefreshCw, RotateCcw } from '@lucide/vue'
const auth=useAuthStore(), admin=computed(()=>auth.isAdmin)
const records=ref<TransactionRecord[]>([]), total=ref(0), page=ref(1), pageSize=20, pages=computed(()=>Math.max(1,Math.ceil(total.value/pageSize)))
const from=ref(''), to=ref(''), userId=ref(''), type=ref<TransactionType|''>(''), loading=ref(false), exporting=ref(false), error=ref(''), dateError=ref('')
function filter(includePage=true):TransactionFilter { return { ...(admin.value&&userId.value?{userId:Number(userId.value)}:{}), ...(type.value?{type:type.value}:{}), ...(from.value?{from:from.value}:{}), ...(to.value?{to:to.value}:{}), ...(includePage?{page:page.value,pageSize}:{}) } }
async function load(newPage=1) { if(from.value&&to.value&&from.value>to.value){dateError.value='结束日期不能早于开始日期';return} dateError.value=''; page.value=newPage; loading.value=true;error.value='';const g=auth.generation;try{const result:Page<TransactionRecord>=await api.records(filter());if(g===auth.generation){records.value=result.items;total.value=result.total;page.value=result.page}}catch(e){if(g===auth.generation)error.value=errorText(e)}finally{if(g===auth.generation)loading.value=false} }
function apply(){void load(1)}
function reset(){from.value='';to.value='';userId.value='';type.value='';void load(1)}
async function exportCsv(){dateError.value='';if(from.value&&to.value&&from.value>to.value){dateError.value='结束日期不能早于开始日期';return}exporting.value=true;error.value='';const g=auth.generation;try{const blob=await api.exportRecords(filter(false));if(g!==auth.generation)return;const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='校园卡交易记录.csv';document.body.appendChild(a);a.click();a.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(e){if(g===auth.generation)error.value=errorText(e)}finally{if(g===auth.generation)exporting.value=false}}
onMounted(()=>load(1))
</script>
<template><AppShell><section class="page-heading"><div><span class="eyebrow">资金明细</span><h1>交易记录</h1><p>查看充值与消费明细，支持按日期和交易类型筛选。</p></div><button class="button button-secondary" :disabled="exporting" @click="exportCsv"><Download :size="16"/> {{exporting?'正在导出…':'导出 CSV'}}</button></section>
  <section class="surface-card filter-card"><div class="filter-card-heading"><div><span class="filter-icon"><Filter :size="17"/></span><b>筛选条件</b></div><button class="text-button" @click="reset"><RotateCcw :size="14"/> 重置</button></div><div class="filter-grid"><label class="filter-field"><span>交易类型</span><select v-model="type"><option value="">全部类型</option><option value="RECHARGE">充值</option><option value="CONSUMPTION">消费</option></select></label><label class="filter-field"><span>开始日期</span><input v-model="from" type="date"/></label><label class="filter-field"><span>结束日期</span><input v-model="to" type="date"/></label><label v-if="admin" class="filter-field"><span>用户 ID <small>可选</small></span><input v-model="userId" type="number" min="1" placeholder="全部用户"/></label><button class="button button-primary filter-submit" :disabled="loading" @click="apply">{{loading?'查询中…':'查询记录'}}</button></div><p v-if="dateError" class="field-error filter-error">{{dateError}}</p></section>
  <div v-if="error" class="page-alert"><span>{{error}}</span><button class="button button-small" @click="load(page)">重试</button></div>
  <section class="surface-card records-card"><div class="section-heading"><div><h2>交易明细</h2><p>共 {{total}} 笔记录</p></div><button class="icon-button" aria-label="刷新记录" :disabled="loading" @click="load(page)"><RefreshCw :size="17" :class="loading?'spin':''"/></button></div><TransactionTable :rows="records" :admin="admin" :loading="loading"/><div class="pagination"><span>第 {{page}} 页，共 {{pages}} 页</span><div><button class="button button-small" :disabled="loading||page<=1" @click="load(page-1)">上一页</button><button class="button button-small" :disabled="loading||page>=pages" @click="load(page+1)">下一页</button></div></div></section>
</AppShell></template>
