import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { OperationRequest } from '../api/types'
import { moneyInputValid } from '../utils/format'

const storageKey = 'campus-card-pending-money-v1'
type PendingMoney = { kind: 'RECHARGE'|'CONSUMPTION'; request: OperationRequest }

function validPending(value: unknown): value is { version: 1; userId: number; pending: PendingMoney } {
  if (!value || typeof value !== 'object') return false
  const entry = value as Record<string, unknown>
  if (entry.version !== 1 || !Number.isSafeInteger(entry.userId) || Number(entry.userId) <= 0) return false
  if (!entry.pending || typeof entry.pending !== 'object') return false
  const pending = entry.pending as Record<string, unknown>
  if (pending.kind !== 'RECHARGE' && pending.kind !== 'CONSUMPTION') return false
  if (!pending.request || typeof pending.request !== 'object') return false
  const request = pending.request as Record<string, unknown>
  return typeof request.amount === 'string' && moneyInputValid(request.amount)
    && typeof request.description === 'string' && request.description.length <= 255
    && typeof request.operationId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(request.operationId)
}

export const useOperationsStore = defineStore('operations', () => {
  const pendingMoney = ref<PendingMoney | null>(null)
  function setPendingMoney(value: PendingMoney, userId: number): boolean {
    const entry = { version: 1 as const, userId, pending: value }
    if (!validPending(entry)) return false
    try { sessionStorage.setItem(storageKey, JSON.stringify(entry)) } catch { return false }
    pendingMoney.value = value
    return true
  }
  function restoreForUser(userId: number): boolean {
    try {
      const raw = sessionStorage.getItem(storageKey)
      if (raw === null) { pendingMoney.value = null; return false }
      const parsed: unknown = JSON.parse(raw)
      if (!validPending(parsed) || parsed.userId !== userId) { clear(); return false }
      pendingMoney.value = parsed.pending
      return true
    } catch { clear(); return false }
  }
  function clear() {
    pendingMoney.value = null
    try { sessionStorage.removeItem(storageKey) } catch { /* memory state is still cleared */ }
  }
  return { pendingMoney, setPendingMoney, restoreForUser, clear }
})
