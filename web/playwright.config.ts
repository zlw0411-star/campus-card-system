import { defineConfig, devices } from '@playwright/test'
import { join } from 'node:path'

const requestedUrl = process.env.CC_E2E_URL
const testUrl = process.env.CC_TEST_DB_URL
const databaseName = testUrl?.split('?', 1)[0]?.replace(/\/$/, '').split('/').pop()
const enabled = Boolean(requestedUrl)
const javaExecutable = process.env.JAVA_HOME
  ? `"${join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')}"`
  : 'java'

function assertSafeTarget() {
  if (!requestedUrl) return
  let url: URL
  try { url = new URL(requestedUrl) }
  catch { throw new Error('CC_E2E_URL must be an absolute loopback URL') }
  if (url.origin !== 'http://127.0.0.1:8089' || url.pathname !== '/' || url.search || url.hash)
    throw new Error('Playwright E2E must target the isolated server origin http://127.0.0.1:8089')
  if (!testUrl || !databaseName?.endsWith('_test') || process.env.CC_DB_URL !== testUrl
      || process.env.CC_DB_USER !== process.env.CC_TEST_DB_USER
      || process.env.CC_DB_PASSWORD !== process.env.CC_TEST_DB_PASSWORD)
    throw new Error('E2E server must use matching CC_DB_* and CC_TEST_DB_* values for an isolated *_test database')
}

assertSafeTarget()

const baseURL = requestedUrl || 'http://127.0.0.1:8089'

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  ...(enabled ? {
    webServer: {
      command: `${javaExecutable} -jar ../server/target/campus-card-web.jar --server.address=127.0.0.1 --server.port=8089`,
      url: `${baseURL}/api/health`,
      reuseExistingServer: false,
      timeout: 60_000,
      env: {
        ...process.env,
        CC_DB_URL: testUrl!,
        CC_DB_USER: process.env.CC_TEST_DB_USER!,
        CC_DB_PASSWORD: process.env.CC_TEST_DB_PASSWORD!,
      },
    },
  } : {}),
})
