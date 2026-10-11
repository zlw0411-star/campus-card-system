import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'

const enabled = Boolean(process.env.CC_E2E_URL)

async function login(page: import('@playwright/test').Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '安全登录' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

async function noHorizontalOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
}

test('student recovers from login error, manages the card, filters and exports records, and renews navigation on login', async ({ page }, testInfo) => {
  test.skip(!enabled, 'Set CC_E2E_URL and matching isolated *_test database settings to run browser tests')

  await page.goto('/login')
  await page.getByLabel('用户名').fill('student')
  await page.getByLabel('密码', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: '安全登录' }).click()
  await expect(page.getByText('用户名或密码错误')).toBeVisible()

  await login(page, 'student', 'student123')
  await expect(page.getByRole('heading', { name: /欢迎回来/ })).toBeVisible()
  const navigation = page.locator('.main-nav')
  await expect(navigation.getByRole('link', { name: '用户管理', exact: true })).toHaveCount(0)
  await page.goto('/users')
  await expect(page).toHaveURL(/\/dashboard$/)

  await navigation.getByRole('link', { name: '我的校园卡', exact: true }).click()
  await expect(page.getByRole('heading', { name: '我的校园卡' })).toBeVisible()
  await expect(page.locator('.balance-value')).toContainText('¥')

  const transactionTag = `E2E-${Date.now()}`
  const rechargeDescription = `${transactionTag} recharge`
  const consumeDescription = `${transactionTag} lunch`
  await page.getByLabel('充值金额').fill('0.01')
  await page.getByLabel('备注（选填）').fill(rechargeDescription)
  await page.getByRole('button', { name: '确认充值' }).click()
  await expect(page.getByText(/交易已完成/)).toBeVisible({ timeout: 10_000 })

  await page.getByRole('button', { name: '模拟消费' }).click()
  await page.getByLabel('消费金额').fill('0.01')
  await page.getByLabel('消费说明').fill(consumeDescription)
  await page.getByRole('button', { name: '确认消费' }).click()
  await expect(page.getByText(/交易已完成/)).toBeVisible({ timeout: 10_000 })

  await navigation.getByRole('link', { name: '交易记录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '交易记录' })).toBeVisible()
  await page.getByLabel('交易类型').selectOption('CONSUMPTION')
  await page.getByRole('button', { name: '查询记录' }).click()
  const consumedRow = page.locator('tr').filter({ hasText: consumeDescription })
  await expect(consumedRow.getByText(consumeDescription, { exact: true })).toBeVisible()
  await expect(page.getByText(rechargeDescription, { exact: true })).toHaveCount(0)

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  const download = await downloadPromise
  const csvPath = testInfo.outputPath('student-transactions.csv')
  await download.saveAs(csvPath)
  const csv = await readFile(csvPath, 'utf8')
  expect(csv.charCodeAt(0)).toBe(0xFEFF)
  expect(csv).toContain(consumeDescription)
  expect(csv).not.toContain(rechargeDescription)

  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await login(page, 'admin', 'admin123')
  await expect(page.getByRole('heading', { name: '校园运营总览' })).toBeVisible()
  await expect(navigation.getByRole('link', { name: '用户管理', exact: true })).toBeVisible()
  await expect(navigation.getByRole('link', { name: '我的校园卡', exact: true })).toHaveCount(0)

  await page.setViewportSize({ width: 390, height: 844 })
  await noHorizontalOverflow(page)
})

test('administrator can create, edit, disable, filter, and re-enable a user responsively', async ({ page }) => {
  test.skip(!enabled, 'Set CC_E2E_URL and matching isolated *_test database settings to run browser tests')

  await login(page, 'admin', 'admin123')
  await expect(page.getByRole('heading', { name: '校园运营总览' })).toBeVisible()
  const navigation = page.locator('.main-nav')
  await expect(navigation.getByRole('link', { name: '用户管理', exact: true })).toBeVisible()
  await navigation.getByRole('link', { name: '用户管理', exact: true }).click()
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: '账户状态' })).toHaveValue('')
  await noHorizontalOverflow(page)

  const suffix = `${Date.now()}`
  const username = `e2e-${suffix}`
  await page.getByRole('button', { name: '新建用户' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('初始密码').fill('e2e-password-123')
  await page.getByLabel('姓名').fill('E2E Student')
  await page.getByLabel('学号').fill(`E2E-${suffix}`)
  await page.getByRole('dialog').getByRole('button', { name: '保存用户' }).click()
  const row = page.locator('tr').filter({ hasText: username })
  await expect(row.getByText(username, { exact: true })).toBeVisible({ timeout: 10_000 })
  await row.getByRole('button', { name: '编辑' }).click()
  await expect(page.getByRole('dialog').getByRole('heading', { name: '编辑用户资料' })).toBeVisible()
  await page.getByLabel('姓名').fill('E2E Updated')
  await page.getByLabel('学号').fill(`EDIT-${suffix}`)
  await page.getByRole('dialog').getByRole('button', { name: '保存用户' }).click()
  await expect(page.getByText('E2E Updated')).toBeVisible()

  await row.getByRole('button', { name: '停用' }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认停用' }).click()
  await expect(row.getByText('已停用')).toBeVisible()
  await page.getByRole('combobox', { name: '账户状态' }).selectOption('false')
  await page.getByRole('button', { name: '搜索用户' }).click()
  await expect(page.getByText(username)).toBeVisible()

  await row.getByRole('button', { name: '启用' }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('alertdialog').getByRole('button', { name: '启用' }).click()
  await expect(row).toHaveCount(0)
  await page.getByRole('combobox', { name: '账户状态' }).selectOption('true')
  await page.getByRole('button', { name: '搜索用户' }).click()
  await expect(page.getByText(username)).toBeVisible()
  await expect(row.getByText('已启用')).toBeVisible()

  await page.setViewportSize({ width: 390, height: 844 })
  await noHorizontalOverflow(page)
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
})
