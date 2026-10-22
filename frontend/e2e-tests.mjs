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

async function makePage(context) {
  const page = await context.newPage()
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push('console: ' + msg.text()) })
  page.on('pageerror', (err) => errors.push('pageerror: ' + err.message))
  return page
}

async function login(page, rolePattern) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: rolePattern }).click()
  await page.waitForURL('**/dashboard', { timeout: 15000 })
  await page.waitForTimeout(900)
}

async function pageCheck(page, path, title, selector, shotName) {
  await page.goto(BASE + path, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)
  const actual = (await page.locator('.page-title').first().textContent() || '').trim()
  assert.ok(actual.includes(title), `${path} title expected ${title}, got ${actual}`)
  if (selector) await page.locator(selector).first().waitFor({ state: 'visible', timeout: 5000 })
  await page.screenshot({ path: `${OUT}/${shotName}`, fullPage: true })
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await makePage(ctx)
  await login(page, /监测分析人员/)

  try {
    await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
    await page.waitForTimeout(2600)
    const title = (await page.locator('.screen-brand h1').textContent() || '').trim()
    assert.ok(title.includes('固定污染源监管指挥大屏'), `dashboard title got ${title}`)
    assert.ok(await page.locator('.kpi').count() === 6, 'KPI count mismatch')
    assert.ok(await page.locator('.enterprise-marker').count() > 0, 'map markers missing')
    await page.screenshot({ path: `${OUT}/20-e2e-bigscreen.png`, fullPage: true })
    record('页面：首页监管大屏与风险地图', true)
  } catch (e) { record('页面：首页监管大屏与风险地图', false, e.message) }
  try {
    const marker = page.locator('.enterprise-marker').first()
    const markerLabel = await marker.getAttribute('aria-label')
    await marker.click()
    await page.waitForTimeout(500)
    const inspector = await page.locator('.map-inspector').innerText()
    assert.ok(markerLabel && inspector.includes(markerLabel.split('，')[0]), 'map inspector did not follow marker')
    await page.getByRole('button', { name: '企业密度' }).click()
    await page.waitForTimeout(500)
    await page.getByRole('button', { name: '事件分布' }).click()
    await page.waitForTimeout(500)
    await page.getByRole('button', { name: '风险热力' }).click()
    record('功能：地图撒点联动与图层切换', true)
  } catch (e) { record('功能：地图撒点联动与图层切换', false, e.message) }
  try {
    await page.getByRole('button', { name: /运行今日采集/ }).click()
    await page.waitForTimeout(2200)
    record('功能：运行今日采集', true)
  } catch (e) { record('功能：运行今日采集', false, e.message) }
  try {
    await page.goto(BASE + '/enterprises', { waitUntil: 'networkidle' })
    await page.waitForTimeout(700)
    await page.locator('.filter-bar input').first().fill('华辰')
    await page.getByRole('button', { name: '查询' }).click()
    await page.waitForTimeout(900)
    assert.equal(await page.locator('.el-table__row').count(), 1)
    record('功能：企业筛选查询', true)
  } catch (e) { record('功能：企业筛选查询', false, e.message) }
  try { await pageCheck(page, '/cockpit', '区域态势', '.echart canvas', '21-e2e-cockpit.png'); record('页面：区域态势', true) } catch (e) { record('页面：区域态势', false, e.message) }
  try {
    await pageCheck(page, '/monitoring', '监测数据分析', '.echart canvas', '41-e2e-monitoring.png')
    assert.equal(await page.locator('.page-kpi').count(), 4, 'monitoring KPI count mismatch')
    assert.ok(await page.locator('.echart').count() >= 4, 'monitoring charts missing')
    assert.ok(await page.locator('.el-table__row').count() >= 7, 'daily summary rows missing')
    record('页面：监测数据小时/日图表', true)
  } catch (e) { record('页面：监测数据小时/日图表', false, e.message) }
  try {
    await page.goto(BASE + '/enterprises', { waitUntil: 'networkidle' })
    await page.waitForTimeout(800)
    await page.locator('.el-table__row').first().getByRole('button', { name: /编辑/ }).click()
    await page.waitForTimeout(400)
    const warning = await page.locator('.el-message').innerText().catch(() => '')
    assert.ok(warning.includes('只读'), 'analyst edit should be guarded')
    record('功能：企业台账只读角色保护', true)
  } catch (e) { record('功能：企业台账只读角色保护', false, e.message) }

  try {
    await pageCheck(page, '/backtest', '规则回测', '.metric-card', '22-e2e-backtest.png')
    await page.getByRole('button', { name: /运行回测/ }).click()
    await page.waitForTimeout(2200)
    assert.equal(await page.locator('.metric-card').count(), 8)
    await page.screenshot({ path: `${OUT}/23-e2e-backtest-run.png`, fullPage: true })
    record('功能：运行规则回测', true)
  } catch (e) { record('功能：运行规则回测', false, e.message) }
  try {
    await pageCheck(page, '/test-data', '测试数据', '.el-table__row', '24-e2e-test-data.png')
    const rows = await page.locator('.el-table__row').count()
    assert.ok(rows > 0, 'no label rows')
    record('页面：测试数据', true, `${rows} 行`)
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /导出 CSV/ }).click()
    ])
    assert.ok(download.suggestedFilename().endsWith('.csv'))
    record('功能：导出测试标签 CSV', true)
  } catch (e) { record('页面：测试数据', false, e.message) }
  try {
    await pageCheck(page, '/data-quality', '数据质量', '.stat-card', '25-e2e-data-quality.png')
    record('页面：数据质量', true)
  } catch (e) { record('页面：数据质量', false, e.message) }
  try {
    await pageCheck(page, '/integration', '集成对账', '.stat-card', '26-e2e-integration.png')
    await page.getByRole('button', { name: /执行对账/ }).click()
    await page.waitForTimeout(1200)
    assert.ok(await page.locator('.reconcile-card').count())
    await page.screenshot({ path: `${OUT}/27-e2e-reconcile.png`, fullPage: true })
    record('功能：执行下发对账', true)
  } catch (e) { record('功能：执行下发对账', false, e.message) }

  try {
    await page.goto(BASE + '/enterprises/E007', { waitUntil: 'networkidle' })
    await page.waitForTimeout(1200)
    await page.getByRole('button', { name: /手工标记线索/ }).click()
    await page.locator('.el-dialog').waitFor({ state: 'visible' })
    await page.locator('.el-dialog textarea').fill('E2E 手工线索：现场巡查发现数据异常波动，标记待复核。')
    await page.getByRole('button', { name: '提交线索' }).click()
    await page.waitForTimeout(1600)
    record('功能：企业画像手工标记线索', true)
  } catch (e) { record('功能：企业画像手工标记线索', false, e.message) }

  try {
    await page.goto(BASE + '/events', { waitUntil: 'networkidle' })
    await page.waitForTimeout(1000)
    await page.getByRole('button', { name: /手工新增线索/ }).click()
    await page.locator('.manual-dialog').waitFor({ state: 'visible' })
    await page.waitForTimeout(900)
    assert.ok(await page.locator('.manual-dialog .el-select').count() >= 3, 'manual clue selects missing')
    assert.ok(await page.locator('.manual-dialog textarea').count() > 0, 'manual clue textarea missing')
    await page.screenshot({ path: `${OUT}/28-e2e-manual-clue.png` })
    await page.getByRole('button', { name: '取消' }).click()
    record('功能：事件工作台手工新增线索入口', true)
  } catch (e) { record('功能：事件工作台手工新增线索入口', false, e.message) }

  try {
    await page.goto(BASE + '/events', { waitUntil: 'networkidle' })
    await page.waitForTimeout(1000)
    await page.locator('.el-table__row').first().click()
    await page.waitForTimeout(1200)
    assert.ok(await page.locator('.clue-card').count() > 0)
    const [popup] = await Promise.all([
      ctx.waitForEvent('page'),
      page.getByRole('button', { name: /生成核查报告/ }).click()
    ])
    await popup.waitForLoadState('networkidle')
    await popup.waitForTimeout(1000)
    const text = await popup.locator('body').innerText()
    assert.ok(text.includes('固定污染源异常线索核查报告'))
    await popup.screenshot({ path: `${OUT}/29-e2e-report.png`, fullPage: true })
    await popup.close()
    record('功能：生成可打印核查报告', true)
  } catch (e) { record('功能：生成可打印核查报告', false, e.message) }

  try {
    await page.goto(BASE + '/reports', { waitUntil: 'networkidle' })
    await page.waitForTimeout(800)
    assert.ok(await page.locator('.el-table__row').count() > 0)
    record('页面：核查报告库', true)
  } catch (e) { record('页面：核查报告库', false, e.message) }

  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const adminPage = await makePage(adminCtx)
  try {
    await login(adminPage, /系统管理员/)
    await adminPage.goto(BASE + '/rules', { waitUntil: 'networkidle' })
    await adminPage.waitForTimeout(800)
    await adminPage.getByRole('button', { name: /编辑/ }).first().click()
    await adminPage.locator('.el-dialog').waitFor({ state: 'visible' })
    await adminPage.getByRole('button', { name: '保存' }).click()
    await adminPage.waitForTimeout(1000)
    record('功能：管理员编辑规则', true)
  } catch (e) { record('功能：管理员编辑规则', false, e.message) }
  try {
    await adminPage.goto(BASE + '/collect', { waitUntil: 'networkidle' })
    await adminPage.waitForTimeout(900)
    await adminPage.getByRole('button', { name: /编辑/ }).first().click()
    await adminPage.locator('.el-dialog').waitFor({ state: 'visible' })
    await adminPage.getByRole('button', { name: '保存' }).click()
    await adminPage.waitForTimeout(1000)
    record('功能：管理员编辑数据源', true)
  } catch (e) { record('功能：管理员编辑数据源', false, e.message) }

  const reviewerCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const reviewerPage = await makePage(reviewerCtx)
  let createdTaskNo = ''
  try {
    await login(reviewerPage, /审核人员/)
    await reviewerPage.goto(BASE + '/enterprises/E001', { waitUntil: 'networkidle' })
    await reviewerPage.waitForTimeout(900)
    assert.ok(await reviewerPage.locator('.archive-card').isVisible(), 'archive card missing')
    await reviewerPage.getByRole('button', { name: /编辑台账/ }).first().click()
    await reviewerPage.locator('.enterprise-edit-dialog').waitFor({ state: 'visible' })
    assert.ok((await reviewerPage.locator('.enterprise-edit-dialog .el-dialog__title').textContent() || '').includes('维护企业台账'))
    await reviewerPage.locator('.enterprise-edit-dialog .el-dialog__body').evaluate((el) => { el.scrollTop = el.scrollHeight })
    await reviewerPage.getByRole('button', { name: '取消' }).click()
    await reviewerPage.waitForTimeout(300)
    await reviewerPage.screenshot({ path: `${OUT}/35-e2e-enterprise-ledger.png`, fullPage: true })
    record('功能：企业台账编辑与档案信息', true)
  } catch (e) { record('功能：企业台账编辑与档案信息', false, e.message) }

  try {
    await reviewerPage.goto(BASE + '/events', { waitUntil: 'networkidle' })
    await reviewerPage.waitForTimeout(900)
    const pendingRow = reviewerPage.locator('.el-table__row').filter({ hasText: '待复核' }).first()
    await pendingRow.click()
    await reviewerPage.waitForTimeout(1000)
    await reviewerPage.getByRole('button', { name: '确认线索' }).first().click()
    await reviewerPage.waitForTimeout(1000)
    await reviewerPage.getByRole('button', { name: '生成任务' }).first().click()
    await reviewerPage.waitForTimeout(1000)
    await reviewerPage.getByRole('button', { name: '下发' }).first().click()
    await reviewerPage.waitForTimeout(1200)
    createdTaskNo = (await reviewerPage.locator('.task-strip b').first().textContent() || '').trim()
    assert.ok(createdTaskNo.startsWith('TSK-'))
    await reviewerPage.screenshot({ path: `${OUT}/34-e2e-reviewer-dispatch.png`, fullPage: true })
    record('功能：审核确认、生成任务、下发', true, createdTaskNo)
  } catch (e) { record('功能：审核确认、生成任务、下发', false, e.message) }

  const mobileCtx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 })
  const mobile = await makePage(mobileCtx)
  try {
    await mobile.goto(BASE + '/login', { waitUntil: 'networkidle' })
    await mobile.getByRole('button', { name: /进入移动 H5 核查端/ }).click()
    await mobile.waitForURL('**/m/tasks', { timeout: 15000 })
    await mobile.waitForTimeout(3200)
    await mobile.screenshot({ path: `${OUT}/30-e2e-mobile-tasks.png` })
    const card = createdTaskNo
      ? mobile.locator('.task-card').filter({ hasText: createdTaskNo }).first()
      : mobile.locator('.task-card').first()
    assert.ok(await card.count() > 0, 'no mobile task')
    await card.click()
    await mobile.waitForTimeout(1200)
    await mobile.screenshot({ path: `${OUT}/31-e2e-mobile-detail.png` })
    const accept = mobile.getByRole('button', { name: /接收任务并开始核查/ })
    if (await accept.count()) {
      await accept.click()
      await mobile.waitForTimeout(1200)
    }
    const fileInput = mobile.locator('.photo-upload input[type=file]')
    await fileInput.setInputFiles({ name: 'evidence.png', mimeType: 'image/png', buffer: fs.readFileSync(`${OUT}/01-login.png`) })
    await mobile.waitForTimeout(3200)
    assert.ok((await mobile.locator('body').innerText()).includes('取证照片已上传'))
    const radios = mobile.locator('.el-radio-button').filter({ hasText: '发现问题' })
    if (await radios.count()) await radios.first().click()
    const tas = mobile.locator('textarea')
    await tas.nth(0).fill('E2E 现场核查：治理设施运行记录不完整，采样管路有冷凝水。')
    await tas.nth(1).fill('已要求企业立即整改，补充运维记录。')
    await mobile.screenshot({ path: `${OUT}/32-e2e-mobile-form.png` })
    await mobile.getByRole('button', { name: /提交反馈并回写闭环/ }).click()
    await mobile.waitForTimeout(2000)
    await mobile.screenshot({ path: `${OUT}/33-e2e-mobile-feedback.png` })
    const body = await mobile.locator('body').innerText()
    assert.ok(body.includes('已提交反馈'))
    assert.ok(body.includes('查看附件'))
    record('功能：移动端接收、取证、反馈', true)
  } catch (e) { record('功能：移动端接收、取证、反馈', false, e.message) }

  try {
    await reviewerPage.goto(BASE + '/tasks', { waitUntil: 'networkidle' })
    await reviewerPage.waitForTimeout(900)
    const row = createdTaskNo ? reviewerPage.locator('.el-table__row').filter({ hasText: createdTaskNo }).first() : reviewerPage.locator('.el-table__row').filter({ hasText: '已反馈' }).first()
    await row.getByRole('button', { name: '关闭' }).click()
    await reviewerPage.waitForTimeout(500)
    await reviewerPage.getByRole('button', { name: '确定' }).click()
    await reviewerPage.waitForTimeout(1200)
    record('功能：PC 端关闭核查任务', true)
  } catch (e) { record('功能：PC 端关闭核查任务', false, e.message) }

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
