import { chromium } from 'playwright'
import fs from 'fs'
import assert from 'node:assert/strict'

const BASE = 'http://localhost:8088'
const OUT = '../screenshots'
fs.mkdirSync(OUT, { recursive: true })
const results = []
const errors = []
function record(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`)
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 }, acceptDownloads: true })
  const page = await ctx.newPage()
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push('console: ' + msg.text()) })
  page.on('pageerror', (err) => errors.push('pageerror: ' + err.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /审核人员/ }).click()
  await page.waitForURL('**/dashboard')
  await page.goto(BASE + '/data-hub', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)

  try {
    assert.ok((await page.locator('.hero-copy h1').textContent() || '').includes('国家生态环境公共数据信息库'))
    assert.equal(await page.locator('.catalog-item').count(), 7)
    assert.ok(await page.locator('.subscriber-item').count() >= 4)
    assert.ok(await page.locator('.package-panel .el-table__row').count() >= 5)
    await page.screenshot({ path: `${OUT}/70-data-hub.png`, fullPage: true })
    record('页面：国家数据共享平台总览', true)
  } catch (e) { record('页面：国家数据共享平台总览', false, e.message) }

  try {
    const packagesBefore = await page.locator('.package-panel .el-table__row').count()
    await page.getByRole('button', { name: /发布数据包/ }).first().click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '发布数据包并分发' })
    await dialog.waitFor({ state: 'visible' })
    await dialog.locator('.el-select').first().click()
    await page.getByRole('option', { name: /污染物与排放限值/ }).click()
    await page.waitForTimeout(300)
    assert.ok(await dialog.locator('.publish-targets span').count() >= 1)
    await page.screenshot({ path: `${OUT}/72-data-hub-publish.png` })
    await dialog.getByRole('button', { name: '发布并自动分发' }).click()
    await page.waitForTimeout(1600)
    assert.ok(await page.locator('.package-drawer').isVisible(), 'package drawer missing')
    assert.ok((await page.locator('.payload-preview').innerText()).includes('header'))
    assert.ok(await page.locator('.drawer-dispatch-list .drawer-dispatch').count() >= 1)
    await page.screenshot({ path: `${OUT}/74-data-hub-package-created.png` })
    await page.getByRole('button', { name: '关闭', exact: true }).last().click()
    await page.waitForTimeout(400)
    assert.ok(await page.locator('.package-panel .el-table__row').count() >= packagesBefore)
    record('功能：发布数据包并自动分发', true)
  } catch (e) { record('功能：发布数据包并自动分发', false, e.message) }

  try {
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 10000 }),
      page.locator('.package-panel .el-table__row').first().getByRole('button', { name: '下载' }).click()
    ])
    assert.ok(download.suggestedFilename().endsWith('.json'))
    record('功能：下载数据包 JSON', true, download.suggestedFilename())
  } catch (e) { record('功能：下载数据包 JSON', false, e.message) }

  try {
    await page.locator('.subscriber-item').first().getByRole('button', { name: '维护' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '维护订阅系统' })
    await dialog.waitFor({ state: 'visible' })
    assert.ok(await dialog.locator('.sub-form-grid').isVisible())
    await page.screenshot({ path: `${OUT}/75-data-hub-subscriber-edit.png` })
    await dialog.getByRole('button', { name: '取消' }).click()
    record('功能：订阅系统维护界面', true)
  } catch (e) { record('功能：订阅系统维护界面', false, e.message) }

  try {
    const okRes = await ctx.request.get(BASE + '/open-data/v1/catalog', {
      headers: { 'X-Open-Data-Key': 'national-open-demo-key' }
    })
    assert.equal(okRes.status(), 200)
    const body = await okRes.json()
    assert.equal(body.catalogs.length, 7)
    const badRes = await ctx.request.get(BASE + '/open-data/v1/catalog', {
      headers: { 'X-Open-Data-Key': 'wrong-key' }
    })
    assert.equal(badRes.status(), 401)
    record('接口：开放目录 Key 校验', true)
  } catch (e) { record('接口：开放目录 Key 校验', false, e.message) }

  const failed = results.filter((r) => !r.ok)
  console.log('\nSUMMARY', `${results.length - failed.length}/${results.length} passed`)
  console.log('CONSOLE_ERRORS', JSON.stringify(errors, null, 2))
  if (failed.length || errors.length) process.exitCode = 1
} catch (e) {
  console.error(e)
  process.exitCode = 1
} finally {
  await browser.close()
}
