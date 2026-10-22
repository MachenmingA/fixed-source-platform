import { chromium } from 'playwright'

const BASE = 'http://localhost:8088'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const failures = []

const routes = ['/dashboard', '/cockpit', '/enterprises', '/enterprises/E001', '/events', '/tasks', '/collect', '/monitoring', '/data-quality', '/rules', '/backtest', '/test-data', '/integration', '/data-hub', '/reports']

for (const width of [1600, 1366, 1280]) {
  const ctx = await browser.newContext({ viewport: { width, height: width === 1280 ? 800 : 1000 } })
  const page = await ctx.newPage()
  const errors = []
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push(msg.text()) })
  page.on('pageerror', (err) => errors.push('pageerror: ' + err.message))
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /监测分析人员/ }).click()
  await page.waitForURL('**/dashboard', { timeout: 15000 })
  await page.waitForTimeout(1200)
  for (const path of routes) {
    await page.goto(BASE + path, { waitUntil: 'networkidle' })
    await page.waitForTimeout(850)
    const info = await page.evaluate(() => {
      const de = document.documentElement
      const content = document.querySelector('.content')
      const pageEl = document.querySelector('.page, .hub-page, .screen-wrap, .screen-shell')
      // ignore intentional scroll containers (tables / map) by checking document width only
      return {
        cw: de.clientWidth,
        sw: de.scrollWidth,
        contentW: content ? Math.round(content.getBoundingClientRect().width) : null,
        pageRight: pageEl ? Math.round(pageEl.getBoundingClientRect().right) : null,
        tablesOverflow: [...document.querySelectorAll('.el-table__body-wrapper')].filter(el => el.scrollWidth > el.clientWidth + 1).length
      }
    })
    if (info.sw > info.cw + 1) failures.push(`${width}px ${path} horizontal overflow ${info.sw}/${info.cw}`)
    console.log(`${width}px ${path}`, JSON.stringify(info))
  }
  if (errors.length) failures.push(`${width}px console errors: ${errors.join(' | ')}`)
  await ctx.close()
}

// mobile pages
const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
const mp = await mctx.newPage()
const mErrors = []
mp.on('console', (msg) => { if (msg.type() === 'error') mErrors.push(msg.text()) })
mp.on('pageerror', (err) => mErrors.push('pageerror: ' + err.message))
await mp.goto(BASE + '/login', { waitUntil: 'networkidle' })
const loginInfo = await mp.evaluate(() => ({ cw: document.documentElement.clientWidth, sw: document.documentElement.scrollWidth }))
if (loginInfo.sw > loginInfo.cw + 1) failures.push(`390px /login horizontal overflow ${loginInfo.sw}/${loginInfo.cw}`)
console.log('390px /login', JSON.stringify(loginInfo))
await mp.getByRole('button', { name: /现场核查人员/ }).click()
await mp.waitForURL('**/m/tasks', { timeout: 15000 })
await mp.waitForTimeout(2200)
const mInfo = await mp.evaluate(() => ({ cw: document.documentElement.clientWidth, sw: document.documentElement.scrollWidth, cards: document.querySelectorAll('.task-card').length, guides: document.querySelectorAll('.guide-item').length }))
if (mInfo.sw > mInfo.cw + 1) failures.push(`390px /m/tasks horizontal overflow ${mInfo.sw}/${mInfo.cw}`)
console.log('390px /m/tasks', JSON.stringify(mInfo))
const card = mp.locator('.task-card').first()
if (await card.count()) {
  await card.click()
  await mp.waitForTimeout(1000)
  const dInfo = await mp.evaluate(() => ({ cw: document.documentElement.clientWidth, sw: document.documentElement.scrollWidth, flow: document.querySelectorAll('.mobile-flow-step').length }))
  if (dInfo.sw > dInfo.cw + 1) failures.push(`390px /m/tasks/:no horizontal overflow ${dInfo.sw}/${dInfo.cw}`)
  console.log('390px /m/tasks/:no', JSON.stringify(dInfo))
}
if (mErrors.length) failures.push('mobile console errors: ' + mErrors.join(' | '))
await mctx.close()

await browser.close()
console.log('\nFAILURES:', failures.length ? '\n' + failures.join('\n') : 'none')
if (failures.length) process.exitCode = 1
