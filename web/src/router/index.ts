import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guest: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '总览' } },
    { path: '/wallet', name: 'wallet', component: () => import('../views/WalletView.vue'), meta: { title: '我的校园卡', userOnly: true } },
    { path: '/transactions', name: 'transactions', component: () => import('../views/TransactionsView.vue'), meta: { title: '交易记录' } },
    { path: '/users', name: 'users', component: () => import('../views/UsersView.vue'), meta: { title: '用户管理', adminOnly: true } },
    { path: '/statistics', name: 'statistics', component: () => import('../views/StatisticsView.vue'), meta: { title: '运营统计', adminOnly: true } },
    { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '账户设置' } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach(async to => {
  const auth = useAuthStore()
  await auth.initialize()
  if (to.meta.guest) return auth.user ? '/dashboard' : true
  if (!auth.user) return { name: 'login', query: { next: to.fullPath } }
  if (to.meta.adminOnly && auth.user.role !== 'ADMIN') return { name: 'dashboard' }
  if (to.meta.userOnly && auth.user.role !== 'USER') return { name: 'dashboard' }
  return true
})
export default router
