export const money = (value: string | number | null | undefined) => {
  const n = Number(value ?? 0)
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: 2 }).format(Number.isFinite(n) ? n : 0)
}
export const dateTime = (value: string) => value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
export const dateOnly = (value: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(`${value}T00:00:00`)) : ''
export const todayIso = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
export const errorText = (error: unknown) => error instanceof Error ? error.message : '操作失败，请稍后重试'
export const isUncertain = (error: unknown) => error instanceof Error && 'code' in error && ['NETWORK_ERROR', 'DATABASE_ERROR'].includes(String((error as {code:string}).code))
export const moneyInputValid = (s: string) => /^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/.test(s) && Number(s) > 0 && Number(s) <= 9999999999.99
export const shortName = (name = '') => name.trim().split(/\s+/)[0] || '同学'
