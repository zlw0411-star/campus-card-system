import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function response(payload: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload),
    blob: vi.fn().mockResolvedValue(new Blob()),
  } as unknown as Response
}

async function freshApi() {
  vi.resetModules()
  return import('../src/api/index')
}

describe('API session epoch and uncertain-write handling', () => {
  let expiredEvents: string[]

  beforeEach(() => {
    expiredEvents = []
    const target = new EventTarget()
    target.addEventListener('campus-session-expired', event => expiredEvents.push(event.type))
    vi.stubGlobal('window', target)
    vi.stubGlobal('CustomEvent', class extends Event {
      constructor(type: string) { super(type) }
    })
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => vi.unstubAllGlobals())

  it('does not expire a newer session when an older request returns 401', async () => {
    const pendingMe = deferred<Response>()
    let csrf = 0
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) return response({ token: `csrf-${++csrf}`, headerName: 'X-CSRF-TOKEN' })
      if (url.endsWith('/auth/login')) return response({ id: csrf, username: 'student', role: 'USER' })
      if (url.endsWith('/auth/me')) return pendingMe.promise
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()

    await api.login('student', 'password-1')
    const oldSessionMe = api.me()
    await Promise.resolve()
    await api.login('student', 'password-2')
    pendingMe.resolve(response({ code: 'AUTH_REQUIRED', message: 'expired' }, 401))

    await expect(oldSessionMe).rejects.toMatchObject({ code: 'AUTH_REQUIRED', status: 401 })
    expect(expiredEvents).toEqual([])
  })

  it('cancels a write if the session changes while its initial CSRF request is pending', async () => {
    const firstCsrf = deferred<Response>()
    let csrfCount = 0
    const writes: string[] = []
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) {
        csrfCount++
        if (csrfCount === 1) return firstCsrf.promise
        return response({ token: `csrf-${csrfCount}`, headerName: 'X-CSRF-TOKEN' })
      }
      if (url.endsWith('/auth/login')) return response({ id: 1, username: 'student', role: 'USER' })
      if (url.endsWith('/card/recharge')) { writes.push(String(init?.body)); return response({ id: 1 }) }
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()

    const operation = api.recharge({ amount: '4.00', description: 'test', operationId: 'stable-before-login' })
    await Promise.resolve()
    await api.login('student', 'new-session')
    firstCsrf.resolve(response({ token: 'old-csrf', headerName: 'X-CSRF-TOKEN' }))

    await expect(operation).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    expect(writes).toHaveLength(0)
  })

  it('does not retry a write when the session changes while refreshing CSRF after rejection', async () => {
    const retryCsrf = deferred<Response>()
    const retryStarted = deferred<void>()
    let csrfCount = 0
    let rechargeWrites = 0
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) {
        csrfCount++
        if (csrfCount === 3) { retryStarted.resolve(); return retryCsrf.promise }
        return response({ token: `csrf-${csrfCount}`, headerName: 'X-CSRF-TOKEN' })
      }
      if (url.endsWith('/auth/login')) return response({ id: csrfCount, username: 'student', role: 'USER' })
      if (url.endsWith('/card/consume')) {
        rechargeWrites++
        return response({ code: 'CSRF_INVALID', message: 'refresh' }, 403)
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()
    await api.login('student', 'password-1')

    const operation = api.consume({ amount: '1.00', description: 'test', operationId: 'stable-during-retry' })
    const rejectedOperation = expect(operation).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    await retryStarted.promise
    expect(rechargeWrites).toBe(1)
    await api.login('student', 'password-2')
    retryCsrf.resolve(response({ token: 'old-retry-csrf', headerName: 'X-CSRF-TOKEN' }))

    await rejectedOperation
    expect(rechargeWrites).toBe(1)
  })

  it('turns an unreadable successful response into NETWORK_ERROR without resubmitting the operation', async () => {
    let csrf = 0
    let submissions = 0
    let submittedBody = ''
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) return response({ token: `csrf-${++csrf}`, headerName: 'X-CSRF-TOKEN' })
      if (url.endsWith('/auth/login')) return response({ id: 1, username: 'student', role: 'USER' })
      if (url.endsWith('/card/recharge')) {
        submissions++
        submittedBody = String(init?.body)
        return {
          ok: true,
          status: 200,
          json: vi.fn().mockRejectedValue(new SyntaxError('truncated JSON')),
          blob: vi.fn().mockResolvedValue(new Blob()),
        } as unknown as Response
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()
    await api.login('student', 'password-1')
    const request = { amount: '7.25', description: 'one operation', operationId: 'keep-this-id' }

    await expect(api.recharge(request)).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(submissions).toBe(1)
    expect(JSON.parse(submittedBody)).toMatchObject(request)
  })

  it('does not expire the session for a wrong current password, but expires it for AUTH_REQUIRED', async () => {
    let csrf = 0
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) return response({ token: `csrf-${++csrf}`, headerName: 'X-CSRF-TOKEN' })
      if (url.endsWith('/auth/login')) return response({ id: 7, username: 'student', role: 'USER' })
      if (url.endsWith('/auth/password')) return response({ code: 'LOGIN_FAILED', message: '原密码错误' }, 401)
      if (url.endsWith('/auth/me')) return response({ code: 'AUTH_REQUIRED', message: '会话失效' }, 401)
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()
    await api.login('student', 'correct-password')

    await expect(api.changePassword('wrong-password', 'new-password-123')).rejects.toMatchObject({
      code: 'LOGIN_FAILED', status: 401,
    })
    expect(expiredEvents).toEqual([])

    await expect(api.me()).rejects.toMatchObject({ code: 'AUTH_REQUIRED', status: 401 })
    expect(expiredEvents).toEqual(['campus-session-expired'])
  })

  it('keeps a committed operation id available to recover after a non-JSON 502 response', async () => {
    let csrf = 0
    let committed: { operationId: string; amount: string; balanceAfter: string } | null = null
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/auth/csrf')) return response({ token: `csrf-${++csrf}`, headerName: 'X-CSRF-TOKEN' })
      if (url.endsWith('/auth/login')) return response({ id: 7, username: 'student', role: 'USER' })
      if (url.endsWith('/card/recharge')) {
        const request = JSON.parse(String(init?.body)) as { operationId: string; amount: string }
        committed = { operationId: request.operationId, amount: request.amount, balanceAfter: '107.25' }
        return {
          ok: false,
          status: 502,
          json: vi.fn().mockRejectedValue(new SyntaxError('upstream returned html')),
        } as unknown as Response
      }
      if (url.endsWith('/card/operations/operation-502')) return response(committed)
      throw new Error(`Unexpected request: ${url}`)
    })
    const { api } = await freshApi()
    await api.login('student', 'password-1')
    const operation = { amount: '7.25', description: '502 recovery', operationId: 'operation-502' }

    await expect(api.recharge(operation)).rejects.toMatchObject({ code: 'SERVER_ERROR', status: 502 })
    expect(committed).toMatchObject({ operationId: operation.operationId })
    await expect(api.findOperation(operation.operationId)).resolves.toMatchObject({
      operationId: operation.operationId, amount: operation.amount, balanceAfter: '107.25',
    })
    expect(fetchMock.mock.calls.filter(([input]) => String(input).endsWith('/card/recharge'))).toHaveLength(1)
  })
})
