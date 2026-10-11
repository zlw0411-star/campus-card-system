<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowDownLeft, ArrowRight, ArrowUpRight, CreditCard, RefreshCw, Sparkles, Users } from '@lucide/vue'
import AppShell from '../components/AppShell.vue'
import TrendChart from '../components/TrendChart.vue'
import TransactionTable from '../components/TransactionTable.vue'
import { api } from '../api'
import type { Card, DashboardOverview, Page, Statistics, TransactionRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { errorText, money, shortName, todayIso } from '../utils/format'
const auth = useAuthStore(), admin = computed(() => auth.isAdmin)
const card = ref<Card|null>(null), trend = ref<DashboardOverview|null>(null), stats = ref<Statistics|null>(null), recent = ref<TransactionRecord[]>([])
const loading = ref(true), error = ref('')
async function load() {
  loading.value = true; error.value = ''; const g = auth.generation
  try {
    const tasks: Promise<unknown>[] = [api.dashboard(7), api.records({page:1,pageSize:5})]
    if (admin.value) tasks.push(api.statistics())
    else tasks.push(api.card())
    const [d, r, extra] = await Promise.all(tasks)
    if (g !== auth.generation) return
    trend.value = d as DashboardOverview; recent.value = (r as Page<TransactionRecord>).items
    if (admin.value) stats.value = extra as Statistics; else card.value = extra as Card
  } catch (e) { if (g === auth.generation) error.value = errorText(e) }
  finally { if (g === auth.generation) loading.value = false }
}
const last7 = computed(() => trend.value)
const today = computed(() => todayIso())
onMounted(load)
</script>
<template>
  <AppShell><section class="page-heading dashboard-heading"><div><span class="eyebrow"><Sparkles :size="14"/> {{ today }}</span><h1>{{ admin ? '校园运营总览' : `欢迎回来，${shortName(auth.user?.fullName)}同学` }}</h1><p>{{ admin ? '查看校园卡运行情况与近期交易动态。' : '校园卡状态与最近活动都在这里。' }}</p></div><button class="button button-secondary" :disabled="loading" @click="load"><RefreshCw :size="16" :class="loading ? 'spin' : ''"/> 刷新数据</button></section>
    <div v-if="error" class="page-alert"><span>{{ error }}</span><button class="button button-small" @click="load">重试</button></div>
    <div v-if="loading && !trend" class="loading-panel"><span class="spinner"></span><span>正在加载校园卡数据…</span></div>
    <template v-else>
      <section class="overview-grid" :class="admin ? 'admin-overview' : 'student-overview'">
        <article v-if="!admin" class="balance-card"><div class="balance-decor decor-one"></div><div class="balance-decor decor-two"></div><div class="balance-card-top"><span>校园卡可用余额</span><span class="card-chip"><CreditCard :size="16"/> CAMPUS</span></div><div class="balance-value">{{ money(card?.balance) }}</div><div class="balance-card-bottom"><div><small>持卡人</small><b>{{ auth.user?.fullName }}</b></div><div class="balance-card-no"><small>校园卡号</small><b>{{ card?.cardNo || '—' }}</b></div><RouterLink to="/wallet" class="balance-arrow" aria-label="打开校园卡"><ArrowRight :size="19"/></RouterLink></div></article>
        <article v-else class="admin-card"><div class="admin-card-icon"><Users :size="20"/></div><span>校园学生用户</span><strong>{{ loading ? '—' : stats?.users ?? '—' }}</strong><small>已启用 {{ stats?.enabledUsers ?? '—' }} 个账户</small></article>
        <article class="metric-card"><span class="metric-icon mint-icon"><ArrowDownLeft :size="18"/></span><span class="metric-label">近 7 日充值</span><strong>{{ money(last7?.rechargeAmount) }}</strong><small>所选周期累计</small></article>
        <article class="metric-card"><span class="metric-icon amber-icon"><ArrowUpRight :size="18"/></span><span class="metric-label">近 7 日消费</span><strong>{{ money(last7?.consumptionAmount) }}</strong><small>所选周期累计</small></article>
        <article v-if="admin" class="metric-card"><span class="metric-icon blue-icon"><CreditCard :size="18"/></span><span class="metric-label">校园卡总余额</span><strong>{{ money(stats?.totalBalance) }}</strong><small>包含停用账户</small></article>
        <article class="metric-card"><span class="metric-icon violet-icon"><Sparkles :size="18"/></span><span class="metric-label">近 7 日交易笔数</span><strong>{{ last7?.transactionCount ?? '—' }}</strong><small>充值与消费</small></article>
      </section>
      <section class="dashboard-content-grid"><article class="surface-card trend-card"><div class="section-heading"><div><h2>资金趋势</h2><p>近 7 日充值与消费明细</p></div><span class="period-chip">近 7 日</span></div><TrendChart :points="trend?.trend || []"/></article><article class="surface-card shortcut-card"><div class="section-heading"><div><h2>{{ admin ? '管理快捷入口' : '快捷服务' }}</h2><p>{{ admin ? '常用管理功能' : '常用校园卡操作' }}</p></div></div><RouterLink v-if="!admin" to="/wallet" class="shortcut-row"><span class="shortcut-icon mint-icon"><CreditCard :size="19"/></span><span><b>校园卡账户</b><small>充值、消费与余额查询</small></span><ArrowRight :size="17"/></RouterLink><RouterLink to="/transactions" class="shortcut-row"><span class="shortcut-icon blue-icon"><ArrowDownLeft :size="19"/></span><span><b>交易记录</b><small>按日期和类型查找</small></span><ArrowRight :size="17"/></RouterLink><RouterLink v-if="admin" to="/users" class="shortcut-row"><span class="shortcut-icon violet-icon"><Users :size="19"/></span><span><b>用户管理</b><small>维护校园卡账户</small></span><ArrowRight :size="17"/></RouterLink><RouterLink v-if="admin" to="/statistics" class="shortcut-row"><span class="shortcut-icon amber-icon"><Sparkles :size="19"/></span><span><b>运营统计</b><small>查看日期范围汇总</small></span><ArrowRight :size="17"/></RouterLink></article></section>
      <section class="surface-card recent-card"><div class="section-heading"><div><h2>最近交易</h2><p>按时间显示最新活动</p></div><RouterLink class="text-link" to="/transactions">全部记录 <ArrowRight :size="15"/></RouterLink></div><TransactionTable :rows="recent" :admin="admin" :loading="loading"/></section>
    </template>
  </AppShell>
</template>
