import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../api'
import type { User } from '../api/types'
import { useOperationsStore } from './operations'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const ready = ref(false)
  const generation = ref(0)
  let initializing: Promise<void> | null = null
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  async function initialize() {
    if (ready.value) return
    if (initializing) return initializing
    const g = generation.value
    initializing = api.me().then(value => { if (g === generation.value) user.value = value })
      .catch(() => { if (g === generation.value) user.value = null })
      .finally(() => { if (g === generation.value) ready.value = true; initializing = null })
    return initializing
  }
  async function login(username: string, password: string) {
    const g = ++generation.value
    const value = await api.login(username, password)
    if (g !== generation.value) return false
    useOperationsStore().clear()
    user.value = value; ready.value = true; return true
  }
  async function logout() {
    ++generation.value; user.value = null; ready.value = true; useOperationsStore().clear()
    try { await api.logout() } catch { /* local session is already cleared */ }
  }
  function expire() { ++generation.value; user.value = null; ready.value = true; useOperationsStore().clear() }
  return { user, ready, generation, isAdmin, initialize, login, logout, expire }
})
