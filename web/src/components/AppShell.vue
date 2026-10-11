<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Activity, ArrowLeftRight, BarChart3, CreditCard, LayoutDashboard, LogOut, Menu, Settings, ShieldCheck, Users, X } from '@lucide/vue'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore(), route = useRoute(), router = useRouter(), drawer = ref(false)
const links = computed(() => [
  { to: '/dashboard', label: '总览', icon: LayoutDashboard },
  ...(auth.user?.role === 'USER' ? [{ to: '/wallet', label: '我的校园卡', icon: CreditCard }] : []),
  { to: '/transactions', label: '交易记录', icon: ArrowLeftRight },
  ...(auth.isAdmin ? [{ to: '/users', label: '用户管理', icon: Users }, { to: '/statistics', label: '运营统计', icon: BarChart3 }] : []),
])
const pageTitle = computed(() => String(route.meta.title || '校园卡服务'))
function expired() { auth.expire(); router.replace({ name: 'login', query: { expired: '1' } }) }
async function signOut() { await auth.logout(); drawer.value = false; router.replace('/login') }
onMounted(() => window.addEventListener('campus-session-expired', expired))
onUnmounted(() => window.removeEventListener('campus-session-expired', expired))
</script>
<template>
  <div class="app-frame">
    <div v-if="drawer" class="mobile-scrim" @click="drawer = false"></div>
    <aside class="sidebar" :class="{ 'sidebar-open': drawer }">
      <div class="brand-lockup"><span class="brand-mark"><ShieldCheck :size="21" /></span><span><b>青禾校园</b><small>CAMPUS CARD</small></span><button class="icon-button mobile-close" aria-label="关闭菜单" @click="drawer = false"><X :size="19"/></button></div>
      <div class="nav-caption">工作台</div>
      <nav class="main-nav"><RouterLink v-for="item in links" :key="item.to" :to="item.to" class="nav-link" :class="{ active: route.path === item.to }" @click="drawer = false"><component :is="item.icon" :size="19" :stroke-width="1.8"/><span>{{ item.label }}</span><span v-if="route.path === item.to" class="active-dot"></span></RouterLink></nav>
      <div class="sidebar-bottom"><div class="help-card"><div class="help-icon"><Activity :size="17"/></div><div><b>校园卡服务</b><p>账户与校园消费一站办理</p></div></div><RouterLink to="/settings" class="nav-link settings-link" @click="drawer = false"><Settings :size="19"/><span>账户设置</span></RouterLink><button class="nav-link signout" @click="signOut"><LogOut :size="19"/><span>退出登录</span></button></div>
      <button class="mobile-close floating-close" aria-label="关闭菜单" @click="drawer = false"><X :size="19"/></button>
    </aside>
    <main class="main-area">
      <header class="topbar"><button class="icon-button menu-trigger" aria-label="打开菜单" @click="drawer = true"><Menu :size="21"/></button><div class="crumb"><span>校园卡服务</span><span class="crumb-sep">/</span><strong>{{ pageTitle }}</strong></div><div class="topbar-user"><div class="user-avatar">{{ auth.user?.fullName?.slice(0,1) || '同' }}</div><div class="user-meta"><b>{{ auth.user?.fullName }}</b><small>{{ auth.isAdmin ? '系统管理员' : '学生账户' }}</small></div><span class="role-pill" :class="auth.isAdmin ? 'admin' : ''">{{ auth.isAdmin ? '管理端' : '学生端' }}</span></div></header>
      <div class="view-container"><slot /></div>
    </main>
  </div>
</template>
