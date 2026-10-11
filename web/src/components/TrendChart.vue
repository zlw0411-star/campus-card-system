<script setup lang="ts">
import { computed } from 'vue'
import type { TrendPoint } from '../api/types'
import { dateOnly, money } from '../utils/format'
const props = defineProps<{ points: TrendPoint[]; compact?: boolean }>()
const width = 680, height = 210, pad = { l: 12, r: 12, t: 20, b: 32 }
const maxValue = computed(() => Math.max(0, ...props.points.flatMap(p => [Number(p.rechargeAmount), Number(p.consumptionAmount)])))
function y(v: string) { const plot = height - pad.t - pad.b; return pad.t + plot - (maxValue.value > 0 ? Number(v) / maxValue.value * plot : 0) }
function x(i: number) { const n = Math.max(1, props.points.length - 1); return pad.l + i * (width - pad.l - pad.r) / n }
const recharge = computed(() => props.points.map((p, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(p.rechargeAmount)}`).join(' '))
const consumption = computed(() => props.points.map((p, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(p.consumptionAmount)}`).join(' '))
const grid = computed(() => [0, 1, 2, 3].map(i => pad.t + i * (height - pad.t - pad.b) / 3))
</script>
<template>
  <div v-if="!points.length" class="chart-empty">所选日期范围内暂无趋势数据</div>
  <div v-else class="chart-wrap" :class="{ compact }">
    <div class="chart-legend"><span><i class="legend-dot mint"></i>充值</span><span><i class="legend-dot amber"></i>消费</span><small>纵轴单位：元</small></div>
    <svg class="trend-svg" viewBox="0 0 680 210" role="img" aria-label="每日充值与消费趋势">
      <line v-for="yy in grid" :key="yy" :x1="12" :x2="668" :y1="yy" :y2="yy" class="chart-grid" />
      <path :d="recharge" class="chart-line chart-recharge"/><path :d="consumption" class="chart-line chart-consumption"/>
      <g v-for="(point, i) in points" :key="point.date"><circle :cx="x(i)" :cy="y(point.rechargeAmount)" r="3.5" class="chart-point recharge-point"><title>{{ point.date }} · 充值 {{ money(point.rechargeAmount) }}</title></circle><circle :cx="x(i)" :cy="y(point.consumptionAmount)" r="3.5" class="chart-point consumption-point"><title>{{ point.date }} · 消费 {{ money(point.consumptionAmount) }}</title></circle><text v-if="i === 0 || i === points.length - 1 || (points.length > 5 && i % Math.ceil(points.length / 5) === 0)" :x="x(i)" y="201" text-anchor="middle" class="chart-label">{{ dateOnly(point.date) }}</text></g>
    </svg>
  </div>
</template>
