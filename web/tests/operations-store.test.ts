import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useOperationsStore } from '../src/stores/operations'

const storageKey = 'campus-card-pending-money-v1'
const operation = {
  kind: 'RECHARGE' as const,
  request: {
    amount: '10.25',
    description: 'persisted transaction',
    operationId: '123e4567-e89b-42d3-a456-426614174000',
  },
}

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  failWrites = false
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) {
    if (this.failWrites) throw new Error('storage quota denied')
    this.values.set(key, String(value))
  }
}

describe('pending transaction persistence', () => {
  let storage: MemoryStorage

  beforeEach(() => {
    storage = new MemoryStorage()
    vi.stubGlobal('sessionStorage', storage)
    setActivePinia(createPinia())
  })

  afterEach(() => vi.unstubAllGlobals())

  it('restores the same operation id and request for the same user after store recreation', () => {
    const firstStore = useOperationsStore()
    expect(firstStore.setPendingMoney(operation, 41)).toBe(true)
    expect(firstStore.pendingMoney).toEqual(operation)

    setActivePinia(createPinia())
    const reloadedStore = useOperationsStore()
    expect(reloadedStore.restoreForUser(41)).toBe(true)
    expect(reloadedStore.pendingMoney).toEqual(operation)
    expect(reloadedStore.pendingMoney?.request.operationId).toBe(operation.request.operationId)
  })

  it('clears a pending operation instead of restoring it for a different user', () => {
    const firstStore = useOperationsStore()
    expect(firstStore.setPendingMoney(operation, 41)).toBe(true)

    setActivePinia(createPinia())
    const switchedUserStore = useOperationsStore()
    expect(switchedUserStore.restoreForUser(99)).toBe(false)
    expect(switchedUserStore.pendingMoney).toBeNull()
    expect(storage.getItem(storageKey)).toBeNull()
  })

  it('handles malformed and invalid stored values without throwing or restoring them', () => {
    const store = useOperationsStore()
    storage.setItem(storageKey, '{truncated json')
    expect(() => store.restoreForUser(41)).not.toThrow()
    expect(store.pendingMoney).toBeNull()
    expect(storage.getItem(storageKey)).toBeNull()

    storage.setItem(storageKey, JSON.stringify({
      version: 1,
      userId: 41,
      pending: { ...operation, request: { ...operation.request, operationId: 'invalid-id' } },
    }))
    expect(() => store.restoreForUser(41)).not.toThrow()
    expect(store.pendingMoney).toBeNull()
    expect(storage.getItem(storageKey)).toBeNull()
  })

  it('refuses to submit when session storage cannot persist the operation', () => {
    storage.failWrites = true
    const store = useOperationsStore()

    expect(store.setPendingMoney(operation, 41)).toBe(false)
    expect(store.pendingMoney).toBeNull()
    expect(storage.getItem(storageKey)).toBeNull()
  })
})
