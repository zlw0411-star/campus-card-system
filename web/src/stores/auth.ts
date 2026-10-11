import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api'
import type { User } from '../api/types'
import { useOperationsStore } from './operations'

const pendingLogoutKey = 'campus-card-logout-pending'
function hasPendingLogout() {
  try { return sessionStorage.getItem(pendingLogoutKey) === '1' } catch { return false }
}
function persistPendingLogout(pending: boolean) {
  try { if (pending) sessionStorage.setItem(pendingLogoutKey, '1'); else sessionStorage.removeItem(pendingLogoutKey) } catch { /* current page still tracks the pending state in memory */ }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const ready = ref(false)
  const generation = ref(0)
  const logoutPending = ref(hasPendingLogout())
  const logoutBusy = ref(false)
  const logoutError = ref('')
  let initializing: Promise<void> | null = null
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  async function initialize() {
    if (ready.value) return
    if (hasPendingLogout()) {
      logoutPending.value = true
      logoutError.value = '上次退出未能确认，请先重试安全退出。'
      user.value = null
      ready.value = true
      useOperationsStore().clear()
      return
    }
    if (initializing) return initializing
    const g = generation.value
    initializing = api.me().then(value => {
      if (g === generation.value) {
        user.value = value
        useOperationsStore().restoreForUser(value.id)
      }
    })
      .catch(error => {
        if (g === generation.value) {
          user.value = null
          const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
          const code = error && typeof error === 'object' && 'code' in error ? (error as { code?: unknown }).code : undefined
          if (status === 401 || ['AUTH_REQUIRED', 'USER_DISABLED', 'FORBIDDEN'].includes(String(code))) useOperationsStore().clear()
        }
      })
      .finally(() => { if (g === generation.value) ready.value = true; initializing = null })
    return initializing
  }
  async function login(username: string, password: string) {
    if (logoutPending.value) throw new Error('请先确认上一个账户已安全退出，再登录。')
    const g = ++generation.value
    const value = await api.login(username, password)
    if (g !== generation.value) return false
    useOperationsStore().restoreForUser(value.id)
    user.value = value; ready.value = true; return true
  }
  async function logout() {
    ++generation.value; user.value = null; ready.value = true; useOperationsStore().clear()
    logoutPending.value = true
    persistPendingLogout(true)
    logoutError.value = ''
    return retryLogout()
  }
  async function retryLogout() {
    if (!logoutPending.value || logoutBusy.value) return false
    logoutBusy.value = true
    logoutError.value = ''
    try {
      await api.logout()
      logoutPending.value = false
      persistPendingLogout(false)
      return true
    } catch (error) {
      const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
      if (status === 401) {
        logoutPending.value = false
        persistPendingLogout(false)
        logoutError.value = '当前登录状态已失效，已确认退出。'
        return true
      }
      logoutError.value = '暂时无法确认安全退出，请检查网络后重试。'
      return false
    } finally {
      logoutBusy.value = false
    }
  }
  function expire() { ++generation.value; user.value = null; ready.value = true; useOperationsStore().clear(); if (hasPendingLogout()) logoutPending.value = true }
  return { user, ready, generation, isAdmin, logoutPending, logoutBusy, logoutError, initialize, login, logout, retryLogout, expire }
})
