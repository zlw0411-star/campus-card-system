<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CreditCard, Eye, EyeOff, Leaf, LockKeyhole, ShieldCheck, Sparkles } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
import { errorText } from '../utils/format'
const auth = useAuthStore(), route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), reveal = ref(false), busy = ref(false), error = ref('')
async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) { error.value = '请输入用户名和密码'; return }
  busy.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    const next = typeof route.query.next === 'string' && route.query.next.startsWith('/') && !route.query.next.startsWith('//') ? route.query.next : '/dashboard'
    await router.replace(next)
  } catch (e) { error.value = errorText(e) }
  finally { busy.value = false; password.value = '' }
}
function fill(user: string, pass: string) { username.value = user; password.value = pass; error.value = '' }
</script>
<template>
  <main class="login-screen">
    <section class="login-story"><div class="story-pattern"></div><div class="login-brand"><span class="brand-mark"><Leaf :size="22"/></span><span><b>青禾校园</b><small>CAMPUS CARD</small></span></div>
      <div class="story-copy"><span class="eyebrow"><Sparkles :size="15"/> 一张卡，连接校园生活</span><h1>校园生活<br/><em>轻松一点。</em></h1><p>让账户、消费和校园服务井然有序。安全便捷的校园卡服务，随时陪伴你的每一天。</p>
        <div class="story-benefits"><div><span><CreditCard :size="18"/></span><b>账户一目了然</b><small>实时查看余额与交易</small></div><div><span><ShieldCheck :size="18"/></span><b>安全校园服务</b><small>每一笔变动清晰可查</small></div></div>
      </div><div class="story-footer">为校园生活添一份从容 <span>·</span> 青禾校园卡</div>
    </section>
    <section class="login-form-side"><div class="login-card"><div class="mobile-login-mark"><span class="brand-mark"><Leaf :size="21"/></span><b>青禾校园</b></div><span class="eyebrow login-eyebrow">欢迎回来</span><h2>登录校园卡</h2><p class="login-intro">使用校园账户继续管理你的校园生活。</p>
        <div v-if="auth.logoutPending" class="session-note logout-note" role="alert"><LockKeyhole :size="15"/><span>上次退出未能确认，登录前请重试安全退出。</span><button class="button-small" type="button" :disabled="auth.logoutBusy" @click="auth.retryLogout()">{{ auth.logoutBusy ? '正在确认…' : '重试退出' }}</button></div><div v-else-if="auth.logoutError" class="session-note"><LockKeyhole :size="15"/>{{ auth.logoutError }}</div>
        <form @submit.prevent="submit"><label class="field-label" for="username">用户名</label><div class="input-wrap"><input id="username" v-model="username" autocomplete="username" placeholder="请输入用户名" :disabled="busy || auth.logoutPending"/><span class="field-icon"><CreditCard :size="18"/></span></div>
          <label class="field-label password-label" for="password">密码</label><div class="input-wrap"><input id="password" v-model="password" :type="reveal ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" :disabled="busy || auth.logoutPending"/><button type="button" class="reveal-button" :aria-label="reveal ? '隐藏密码' : '显示密码'" :disabled="auth.logoutPending" @click="reveal = !reveal"><component :is="reveal ? EyeOff : Eye" :size="18"/></button></div>
          <div v-if="error" class="form-alert"><span class="alert-dot"></span>{{ error }}</div><div v-if="route.query.expired" class="session-note"><LockKeyhole :size="15"/> 登录状态已过期，请重新登录。</div>
          <button class="button button-primary login-submit" type="submit" :disabled="busy || auth.logoutPending">{{ busy ? '正在验证…' : '安全登录' }}<ArrowRight v-if="!busy" :size="18"/><span v-else class="spinner spinner-light"></span></button>
        </form>
        <div class="demo-access"><div class="demo-heading"><span>演示账户</span><small>点击填入</small></div><button type="button" class="demo-item" :disabled="busy || auth.logoutPending" @click="fill('admin','admin123')"><span class="demo-avatar admin-avatar">A</span><span><b>管理员</b><small>admin · 管理控制台</small></span><span class="demo-use">填入</span></button><button type="button" class="demo-item" :disabled="busy || auth.logoutPending" @click="fill('student','student123')"><span class="demo-avatar">S</span><span><b>学生账户</b><small>student · 校园卡服务</small></span><span class="demo-use">填入</span></button><button type="button" class="demo-item" :disabled="busy || auth.logoutPending" @click="fill('demo2026','student123')"><span class="demo-avatar demo-avatar-soft">D</span><span><b>体验账户</b><small>demo2026 · 丰富交易记录</small></span><span class="demo-use">填入</span></button></div>
        <div class="login-legal">仅使用校园演示账户体验账户与交易管理功能。</div>
    </div></section>
  </main>
</template>
