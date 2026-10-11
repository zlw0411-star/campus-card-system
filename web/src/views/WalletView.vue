<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { api } from '../api'
import type { Card, OperationRequest, TransactionRecord } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useOperationsStore } from '../stores/operations'
import { ArrowDownLeft, ArrowUpRight, Check, CircleAlert, CreditCard, RefreshCw, ShieldCheck } from '@lucide/vue'
import { errorText, isUncertain, money, moneyInputValid } from '../utils/format'
const auth = useAuthStore(), operations = useOperationsStore()
const card = ref<Card|null>(null), cardLoading = ref(true), cardError = ref('')
const kind = ref<'RECHARGE'|'CONSUMPTION'>('RECHARGE'), amount = ref(''), description = ref(''), busy = ref(false), message = ref(''), failure = ref(''), confirmed = ref(false)
const pending = computed(() => operations.pendingMoney)
const amountError = computed(() => amount.value && !moneyInputValid(amount.value) ? '请输入有效金额，最多两位小数。' : '')
const descriptionError = computed(() => kind.value === 'CONSUMPTION' && !description.value.trim() ? '请填写消费说明。' : description.value.length > 255 ? '说明最多 255 个字符。' : '')
async function loadCard(expectedGeneration=auth.generation) { if(expectedGeneration!==auth.generation)return; cardLoading.value = true; cardError.value=''; try { const result=await api.card(); if(expectedGeneration===auth.generation) card.value=result } catch(e) { if(expectedGeneration===auth.generation) cardError.value=errorText(e) } finally { if(expectedGeneration===auth.generation) cardLoading.value=false } }
function setKind(value:'RECHARGE'|'CONSUMPTION') { if (pending.value) return; kind.value=value; failure.value=''; message.value='' }
function start() {
  failure.value=''; message.value=''; confirmed.value=false
  if(!moneyInputValid(amount.value)) { failure.value='请输入正数金额，最多两位小数。'; return }
  if(descriptionError.value) { failure.value=descriptionError.value; return }
  const request:OperationRequest={amount:amount.value,description:description.value.trim(),operationId:crypto.randomUUID()}
  operations.pendingMoney={kind:kind.value,request}
  void writePending()
}
async function writePending() {
  const op=operations.pendingMoney; if(!op||busy.value)return
  const g=auth.generation, operationId=op.request.operationId
  const current=()=>g===auth.generation&&operations.pendingMoney?.request.operationId===operationId
  if(!current())return
  busy.value=true; failure.value=''; message.value='正在提交交易…'
  try { const record=op.kind==='RECHARGE'?await api.recharge(op.request):await api.consume(op.request); if(current())complete(record,g,operationId) }
  catch(e) {
    if(!current())return
    if(isUncertain(e)) { message.value='交易结果尚未确认。请查询状态后继续。'; failure.value=errorText(e) }
    else { operations.clear(); message.value=''; failure.value=errorText(e) }
  } finally { if(g===auth.generation&&(!operations.pendingMoney||operations.pendingMoney.request.operationId===operationId))busy.value=false }
}
async function checkAndRetry() {
  const op=operations.pendingMoney; if(!op||busy.value)return
  const g=auth.generation, operationId=op.request.operationId
  const current=()=>g===auth.generation&&operations.pendingMoney?.request.operationId===operationId
  if(!current())return
  busy.value=true; failure.value=''; message.value='正在查询交易状态…'
  try {
    const found=await api.findOperation(op.request.operationId)
    if(!current())return
    if(found) { complete(found,g,operationId); return }
    message.value='未找到该交易，正在使用原请求安全重试…'
    if(!current())return
    try { const record=op.kind==='RECHARGE'?await api.recharge(op.request):await api.consume(op.request); if(current())complete(record,g,operationId) }
    catch(e) { if(!current())return; if(isUncertain(e)) { message.value='交易结果仍待确认。请再次查询状态。'; failure.value=errorText(e) } else { operations.clear(); message.value=''; failure.value=errorText(e) } }
  } catch(e) { if(current()){message.value='无法确认交易状态。查询失败时不会重新提交。'; failure.value=errorText(e)} }
  finally { if(g===auth.generation&&(!operations.pendingMoney||operations.pendingMoney.request.operationId===operationId))busy.value=false }
}
function complete(record:TransactionRecord,g=auth.generation,operationId=operations.pendingMoney?.request.operationId) { if(g!==auth.generation||!operationId||operations.pendingMoney?.request.operationId!==operationId)return; operations.clear(); confirmed.value=true; message.value=`交易已完成，当前余额 ${money(record.balanceAfter)}。`; failure.value=''; amount.value=''; description.value=''; void loadCard(g) }
onMounted(()=>{if(pending.value){kind.value=pending.value.kind;amount.value=pending.value.request.amount;description.value=pending.value.request.description;message.value='交易结果尚未确认，请查询交易状态。'}void loadCard()})
</script>
<template>
  <AppShell><section class="page-heading"><div><span class="eyebrow">账户服务</span><h1>我的校园卡</h1><p>查看卡片余额，轻松完成校园卡充值与消费。</p></div><button class="button button-secondary" :disabled="cardLoading" @click="loadCard()"><RefreshCw :size="16" :class="cardLoading?'spin':''"/> 刷新余额</button></section>
    <div v-if="cardError" class="page-alert"><span>{{ cardError }}</span><button class="button button-small" @click="loadCard()">重试</button></div>
    <div class="wallet-layout"><section class="wallet-main"><article class="balance-card wallet-balance"><div class="balance-decor decor-one"></div><div class="balance-decor decor-two"></div><div class="balance-card-top"><span>可用余额</span><span class="card-chip"><CreditCard :size="16"/> CAMPUS</span></div><div class="balance-value">{{ cardLoading?'载入中…':money(card?.balance) }}</div><div class="balance-card-bottom"><div><small>持卡人</small><b>{{ auth.user?.fullName }}</b></div><div class="balance-card-no"><small>校园卡号</small><b>{{ card?.cardNo || '—' }}</b></div><span class="card-contactless"><i></i><i></i><i></i></span></div></article>
      <article class="surface-card wallet-form-card"><div class="section-heading"><div><h2>账户操作</h2><p>金额按输入精度提交，不会自动舍入。</p></div><span class="secure-note"><ShieldCheck :size="15"/> 安全交易</span></div>
        <div class="operation-tabs"><button :class="{selected:kind==='RECHARGE'}" :disabled="!!pending||busy" @click="setKind('RECHARGE')"><ArrowDownLeft :size="17"/> 充值</button><button :class="{selected:kind==='CONSUMPTION'}" :disabled="!!pending||busy" @click="setKind('CONSUMPTION')"><ArrowUpRight :size="17"/> 模拟消费</button></div>
        <form class="wallet-form" @submit.prevent="start"><label class="field-label" for="amount">{{ kind==='RECHARGE'?'充值':'消费' }}金额</label><div class="money-input-wrap"><span>¥</span><input id="amount" v-model="amount" inputmode="decimal" placeholder="0.00" :disabled="!!pending||busy" aria-describedby="amount-help"/><span class="money-suffix">CNY</span></div><small id="amount-help" class="field-help">请输入正数，最大金额 ¥9,999,999,999.99</small><span v-if="amountError" class="field-error">{{ amountError }}</span>
          <label class="field-label description-label" for="description">{{ kind==='CONSUMPTION'?'消费说明':'备注（选填）' }}</label><input id="description" v-model="description" maxlength="255" :disabled="!!pending||busy" :placeholder="kind==='CONSUMPTION'?'例如：食堂午餐':'填写充值用途（可选）'"/><small class="field-help">{{ description.length }}/255</small><span v-if="descriptionError" class="field-error">{{ descriptionError }}</span>
          <div v-if="message" class="operation-message" :class="confirmed?'success-message':''"><Check v-if="confirmed" :size="17"/><CircleAlert v-else-if="pending" :size="17"/><span>{{ message }}</span></div><div v-if="failure" class="form-alert"><span class="alert-dot"></span>{{ failure }}</div>
          <button v-if="!pending" class="button button-primary operation-submit" type="submit" :disabled="busy||cardLoading||!moneyInputValid(amount)||!!descriptionError"><ArrowDownLeft v-if="kind==='RECHARGE'" :size="18"/><ArrowUpRight v-else :size="18"/>{{ kind==='RECHARGE'?'确认充值':'确认消费' }}</button>
          <button v-else class="button button-primary operation-submit" type="button" :disabled="busy" @click="checkAndRetry"><span v-if="busy" class="spinner spinner-light"></span><RefreshCw v-else :size="17"/>{{ busy?'正在确认…':'查询状态并安全重试' }}</button>
          <p v-if="pending" class="pending-hint">本次交易请求已锁定；状态未确认前会沿用相同金额和请求编号。</p>
        </form>
      </article></section>
      <aside class="wallet-aside"><article class="surface-card wallet-info"><span class="info-badge"><ShieldCheck :size="19"/></span><h3>安心使用校园卡</h3><p>每笔充值与消费都会记录在交易明细中。遇到网络中断时，请先查询交易状态，避免重复操作。</p><RouterLink to="/transactions" class="text-link">查看交易记录 <ArrowDownLeft :size="15"/></RouterLink></article><article class="wallet-tip"><b>交易记录</b><p>完成校内消费后，可在交易记录中查看交易时间、金额与当前余额。</p></article></aside>
    </div>
  </AppShell>
</template>
