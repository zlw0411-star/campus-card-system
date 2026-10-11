import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { OperationRequest } from '../api/types'
export const useOperationsStore = defineStore('operations', () => {
  const pendingMoney = ref<{ kind: 'RECHARGE'|'CONSUMPTION'; request: OperationRequest } | null>(null)
  function clear() { pendingMoney.value = null }
  return { pendingMoney, clear }
})
