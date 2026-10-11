<script setup lang="ts">
import type { TransactionRecord } from '../api/types'
import { ArrowDownLeft, ArrowUpRight } from '@lucide/vue'
import { dateTime, money } from '../utils/format'
defineProps<{ rows: TransactionRecord[]; loading?: boolean; admin?: boolean }>()
</script>
<template>
  <div class="table-scroll"><table class="data-table"><thead><tr><th>交易项目</th><th v-if="admin">持卡人</th><th>交易时间</th><th>金额</th><th>交易后余额</th><th>状态</th></tr></thead>
    <tbody v-if="rows.length"><tr v-for="r in rows" :key="r.id"><td><div class="transaction-name"><span class="transaction-icon" :class="r.type === 'RECHARGE' ? 'recharge' : 'consumption'"><component :is="r.type === 'RECHARGE' ? ArrowDownLeft : ArrowUpRight" :size="16"/></span><span><b>{{ r.description || (r.type === 'RECHARGE' ? '校园卡充值' : '校园消费') }}</b><small>{{ r.type === 'RECHARGE' ? '账户充值' : '校园消费' }}</small></span></div></td><td v-if="admin"><span class="person-cell">{{ r.fullName }}<small>{{ r.username }}</small></span></td><td class="muted-cell">{{ dateTime(r.createdAt) }}</td><td class="amount-cell" :class="r.type === 'RECHARGE' ? 'positive' : ''">{{ r.type === 'RECHARGE' ? '+' : '−' }}{{ money(r.amount) }}</td><td class="muted-cell">{{ money(r.balanceAfter) }}</td><td><span class="status-chip" :class="r.type === 'RECHARGE' ? 'success' : 'neutral'">{{ r.type === 'RECHARGE' ? '充值成功' : '已完成' }}</span></td></tr></tbody>
    <tbody v-else><tr><td :colspan="admin ? 6 : 5"><div class="table-empty"><span v-if="loading" class="spinner"></span><span v-else>暂无符合条件的交易记录</span></div></td></tr></tbody>
  </table></div>
</template>
