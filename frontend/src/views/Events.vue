<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">事件工作台</h1>
        <p class="page-sub">自动规则与人工线索汇入同一证据池，聚合为异常事件；先看证据和 AI 依据，再由人确认并下发核查</p>
      </div>
      <div class="page-actions">
        <div class="soft-pill green">{{ events.length }} 个事件</div>
        <el-button type="primary" @click="openManual"><el-icon><CirclePlus /></el-icon> 手工新增线索</el-button>
        <router-link to="/reports"><el-button><el-icon><Document /></el-icon> 报告库</el-button></router-link>
        <el-button :disabled="selectedEvents.length < 2" @click="mergeSelected">
          合并选中{{ selectedEvents.length ? ` (${selectedEvents.length})` : '' }}
        </el-button>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="page-kpis">
      <div v-for="stat in eventStats" :key="stat.label" class="page-kpi" :class="`tone-${stat.tone}`">
        <span class="page-kpi-icon"><el-icon><component :is="stat.icon" /></el-icon></span>
        <div class="page-kpi-copy">
          <span>{{ stat.label }}</span>
          <b>{{ stat.value }}<small>{{ stat.unit }}</small></b>
          <em>{{ stat.hint }}</em>
        </div>
      </div>
    </div>

    <div class="card card-pad" style="margin-bottom:18px">
      <div class="filter-bar" style="margin-bottom:0">
        <el-input v-model="filters.keyword" placeholder="搜索事件 / 企业" clearable :prefix-icon="Search" @keyup.enter="load" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable>
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="全部优先级" clearable>
          <el-option label="P1 紧急" value="P1" />
          <el-option label="P2 关注" value="P2" />
          <el-option label="P3 常规" value="P3" />
        </el-select>
        <el-input v-if="filters.enterpriseId" :model-value="filters.enterpriseId" disabled style="width:150px" />
        <el-button type="primary" @click="load">查询</el-button>
        <div class="grow"></div>
        <span class="tiny muted">按优先级与时间排序</span>
      </div>
    </div>

    <div class="card">
      <el-table :data="events" v-loading="loading" style="width:100%" @row-click="onRowClick" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="44" />
        <el-table-column label="事件号" width="154">
          <template #default="{ row }"><span class="event-no">{{ row.eventNo }}</span></template>
        </el-table-column>
        <el-table-column label="事件" min-width="310">
          <template #default="{ row }">
            <div class="event-cell">
              <div class="priority-mark" :class="row.priority?.toLowerCase()">{{ row.priority }}</div>
              <div class="event-main">
                <div class="event-title">{{ row.title }}</div>
                <div class="event-sub">{{ row.enterpriseName }} · {{ row.pointName }} · {{ row.pollutantName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="严重度" width="88">
          <template #default="{ row }"><el-tag :type="severityMeta(row.maxSeverity).type" size="small" effect="plain">{{ severityMeta(row.maxSeverity).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="可信度" width="150">
          <template #default="{ row }">
            <div class="cred-bar">
              <el-progress :percentage="row.credibility || 0" :show-text="false" :stroke-width="6" :color="credColor(row.credibility)" />
              <span>{{ row.credibility || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="证据" width="80" align="center">
          <template #default="{ row }"><span class="soft-pill">{{ row.clueCount }} 条</span></template>
        </el-table-column>
        <el-table-column prop="startTime" label="时间" width="150" />
        <el-table-column label="" width="86" align="right">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openEvent(row)">研判 <el-icon><ArrowRight /></el-icon></el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !events.length" class="empty-block"><el-icon><Warning /></el-icon><span>没有符合条件的异常事件</span></div>
    </div>

    <el-drawer v-model="drawer" size="760px" :with-header="false" class="event-drawer">
      <div v-if="detail" class="drawer-body">
        <div class="drawer-head">
          <div class="drawer-title-row">
            <el-tag :type="priorityMeta(detail.event.priority).type" effect="dark">{{ priorityMeta(detail.event.priority).label }}</el-tag>
            <el-tag :type="statusMeta(detail.event.status).type">{{ statusMeta(detail.event.status).label }}</el-tag>
            <span class="soft-pill">{{ detail.event.eventNo }}</span>
            <el-button class="report-btn" size="small" type="primary" plain @click="generateReport">
              <el-icon><Document /></el-icon> 生成核查报告
            </el-button>
          </div>
          <h2>{{ detail.event.title }}</h2>
          <p>{{ detail.event.enterpriseName }} · {{ detail.event.pointName }} · {{ detail.event.pollutantName }}</p>
          <div class="drawer-meta">
            <span><b>时间</b>{{ detail.event.startTime }} 至 {{ detail.event.endTime }}</span>
            <span><b>可信度</b>{{ detail.event.credibility }}</span>
            <span><b>关联线索</b>{{ detail.event.clueCount }} 条</span>
            <span><b>标准</b>{{ detail.event.standardRef }}</span>
          </div>
        </div>

        <div class="section">
          <div class="section-head">
            <h3>证据链与 AI 辅助研判</h3>
            <div class="section-actions">
              <span class="muted tiny">AI 结论仅作辅助，不替代人工判断</span>
              <el-button v-if="canSplit" size="small" :disabled="!splitSelected.length" @click="splitSelectedClues">
                拆分选中{{ splitSelected.length ? ` (${splitSelected.length})` : '' }}
              </el-button>
            </div>
          </div>

          <div v-for="clue in detail.clues" :key="clue.id" class="clue-card">
            <div class="clue-head">
              <div class="clue-title">
                <el-tag :type="severityMeta(clue.severity).type" size="small">{{ clue.ruleCode }} {{ clue.ruleName }}</el-tag>
                <el-tag :type="statusMeta(clue.status).type" size="small" effect="plain">{{ statusMeta(clue.status).label }}</el-tag>
                <span v-if="clue.source === 'MANUAL'" class="manual-badge"><el-icon><EditPen /></el-icon> 人工标记</span>
                <span class="clue-time">{{ clue.startTime }} — {{ clue.endTime }}</span>
              </div>
              <div class="clue-actions">
                <el-checkbox v-if="canSplit" :model-value="splitSelected.includes(clue.id)" class="split-check" @change="toggleSplit(clue.id)">拆出</el-checkbox>
                <el-button v-if="canConfirm(clue)" size="small" type="primary" plain @click="confirmClue(clue)">确认线索</el-button>
                <el-button v-if="canCreateTask(clue)" size="small" type="primary" @click="createTask(clue)">生成任务</el-button>
                <el-button v-if="taskForClue(clue)?.status === 'PENDING_DISPATCH'" size="small" type="success" @click="dispatchTask(taskForClue(clue))">下发</el-button>
                <el-button v-if="!clue.ai" size="small" @click="runAi(clue)">生成 AI 研判</el-button>
              </div>
            </div>

            <div class="evidence-grid">
              <div class="evidence-box">
                <div class="label">触发证据</div>
                <div class="metric-row">
                  <span>触发值 <b>{{ fmtNum(clue.triggerValue) }}</b></span>
                  <span>阈值 <b>{{ fmtNum(clue.thresholdValue) }}</b></span>
                  <span>可信度 <b>{{ clue.credibility }}</b></span>
                </div>
                <ul class="evidence-values">
                  <li v-for="v in (clue.evidence?.values || [])" :key="v">{{ v }}</li>
                </ul>
              </div>
              <div class="ai-box" :class="{ refusal: clue.ai?.refusal }">
                <div class="label">
                  AI 辅助研判
                  <span v-if="clue.ai" class="ai-model">{{ clue.ai.model }} · {{ clue.ai.promptVersion }}</span>
                </div>
                <template v-if="clue.ai">
                  <div v-if="clue.ai.refusal" class="refusal-banner">
                    <el-icon><CircleClose /></el-icon>
                    未检索到可引用条款，AI 已拒答；请人工补充依据后再研判。
                  </div>
                  <p class="ai-summary">{{ clue.ai.content?.explanation }}</p>
                  <div class="ai-citations">
                    <div v-for="c in clue.ai.citations" :key="c.id" class="citation">
                      <div class="citation-head">
                        <b>{{ c.law }}</b>
                        <span>{{ c.clause }} · 生效 {{ c.effectiveDate }}</span>
                      </div>
                      <p>{{ c.quote }}</p>
                    </div>
                  </div>
                  <div v-if="(clue.ai.content?.suggestions || []).length" class="suggestions">
                    <div class="suggest-title">建议核查</div>
                    <ul><li v-for="s in clue.ai.content.suggestions" :key="s">{{ s }}</li></ul>
                  </div>
                  <div class="uncertainty">{{ clue.ai.content?.uncertainty }}</div>
                  <div class="ai-foot">
                    <span>置信度 {{ Math.round((clue.ai.confidence || 0) * 100) }}%</span>
                    <span v-if="clue.ai.refusal" class="red">无依据拒答</span>
                  </div>
                </template>
                <div v-else class="empty-ai">
                  <el-icon><MagicStick /></el-icon>
                  <span>尚未生成 AI 研判</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="(detail.tasks || []).length" class="section">
          <div class="section-head"><h3>核查任务</h3><span class="muted tiny">{{ detail.tasks.length }} 个</span></div>
          <div v-for="task in detail.tasks" :key="task.id" class="task-strip">
            <div>
              <b>{{ task.taskNo }}</b>
              <span>{{ task.assignee }} · {{ statusMeta(task.status).label }}</span>
            </div>
            <div class="task-strip-actions">
              <el-button v-if="task.status === 'PENDING_DISPATCH'" size="small" type="success" @click="dispatchTask(task)">下发</el-button>
              <el-button size="small" @click="router.push('/tasks')">去任务中心</el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-block"><el-icon><Loading /></el-icon><span>正在加载事件详情</span></div>
    </el-drawer>

    <el-dialog v-model="manualVisible" title="手工新增线索" width="640px" top="7vh" class="manual-dialog" destroy-on-close>
      <div class="manual-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>人工线索与每日自动线索进入同一证据池，参与事件聚合和风险计算。严重度默认为低，可信度 65，可继续补充 AI 研判或人工确认。</span>
      </div>
      <el-form label-position="top" class="manual-form">
        <el-form-item label="监管企业" required>
          <el-select v-model="manual.enterpriseId" filterable placeholder="请选择企业" style="width:100%" @change="onManualEnterprise">
            <el-option v-for="item in enterprises" :key="item.id" :label="`${item.name} · ${item.region}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="manual-grid">
          <el-form-item label="监测点" required>
            <el-select v-model="manual.pointId" placeholder="请选择监测点" style="width:100%" @change="onManualPoint">
              <el-option v-for="item in manualPoints" :key="item.id" :label="item.pointName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="污染物" required>
            <el-select v-model="manual.pollutantCode" placeholder="请选择污染物" :disabled="!manualPollutants.length" style="width:100%">
              <el-option v-for="item in manualPollutants" :key="item.code" :label="`${item.name} (${item.code})`" :value="item.code" />
            </el-select>
          </el-form-item>
        </div>
        <div v-if="manual.pointId && !manualPollutants.length" class="manual-empty-tip">
          该监测点尚未维护监测污染物，暂不能创建人工线索。
        </div>
        <el-form-item label="异常时段" required>
          <el-date-picker v-model="manual.range" type="datetimerange" value-format="YYYY-MM-DD HH:mm"
            range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" :clearable="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="线索描述" required>
          <el-input v-model="manual.description" type="textarea" :rows="3" maxlength="200" show-word-limit
            placeholder="例如：现场巡查发现采样管路冷凝水，需核查数据有效性" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="submitManualClue">创建线索</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheck, CircleClose, CirclePlus, Clock, Document, EditPen, Flag, InfoFilled, Loading, MagicStick, Refresh, Search, Warning } from '@element-plus/icons-vue'
import api from '../api'
import { hasRole } from '../store'
import { fmtNum, priorityMeta, severityMeta, statusMeta } from '../utils/labels'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const events = ref([])
const eventStats = computed(() => [
  { label: '事件总数', value: events.value.length, unit: '件', hint: '同源线索聚合', icon: Warning, tone: 'navy' },
  { label: 'P1 紧急', value: events.value.filter((item) => item.priority === 'P1').length, unit: '件', hint: '需优先处置', icon: Flag, tone: 'red' },
  { label: '待复核', value: events.value.filter((item) => item.status === 'PENDING').length, unit: '件', hint: '等待人工研判', icon: Clock, tone: 'amber' },
  { label: '已闭环', value: events.value.filter((item) => item.status === 'CLOSED').length, unit: '件', hint: '事件流程结束', icon: CircleCheck, tone: 'green' }
])
const drawer = ref(false)
const detail = ref(null)
const filters = reactive({
  keyword: '',
  status: '',
  priority: '',
  enterpriseId: route.query.enterpriseId || ''
})
const selectedEvents = ref([])
const splitSelected = ref([])
const canSplit = computed(() => hasRole('reviewer') && (detail.value?.clues?.length || 0) > 1)

const enterprises = ref([])
const manualVisible = ref(false)
const manualSaving = ref(false)
const manualPoints = ref([])
const manualPollutants = ref([])
const manual = reactive({
  enterpriseId: '',
  pointId: '',
  pollutantCode: '',
  range: [],
  description: ''
})

const statusOptions = [
  { label: '待复核', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已下发', value: 'DISPATCHED' },
  { label: '执行中', value: 'IN_PROGRESS' },
  { label: '已反馈', value: 'FEEDBACK' },
  { label: '已关闭', value: 'CLOSED' }
]

function credColor(value) {
  const n = Number(value || 0)
  if (n >= 75) return '#16a34a'
  if (n >= 50) return '#f59e0b'
  return '#ef4444'
}

function canConfirm(clue) {
  return hasRole('reviewer') && clue.status === 'PENDING'
}

function canCreateTask(clue) {
  return hasRole('reviewer') && clue.status === 'CONFIRMED' && !taskForClue(clue)
}

function taskForClue(clue) {
  return (detail.value?.tasks || []).find((t) => String(t.clueId) === String(clue.id))
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/events', { params: filters })
    events.value = data
  } finally {
    loading.value = false
  }
}

async function onSelectionChange(rows) {
  selectedEvents.value = rows
}

function onRowClick(row, column) {
  if (column?.type === 'selection') return
  openEvent(row)
}

function toggleSplit(id) {
  const index = splitSelected.value.indexOf(id)
  if (index >= 0) splitSelected.value.splice(index, 1)
  else splitSelected.value.push(id)
}

async function splitSelectedClues() {
  if (!detail.value || !splitSelected.value.length) return
  const sourceId = detail.value.event.id
  await api.post(`/events/${sourceId}/split`, { clueIds: splitSelected.value })
  ElMessage.success('已拆分出新事件。可在列表中选中两个事件再合并。')
  splitSelected.value = []
  drawer.value = false
  detail.value = null
  await load()
}

async function mergeSelected() {
  if (selectedEvents.value.length < 2) return
  const ids = selectedEvents.value.map((x) => x.id)
  const { data } = await api.post('/events/merge', { eventIds: ids })
  ElMessage.success('事件已合并')
  selectedEvents.value = []
  if (drawer.value && detail.value?.event?.id === data.event?.id) {
    detail.value = data
  }
  await load()
}

async function generateReport() {
  if (!detail.value) return
  const { data } = await api.post(`/events/${detail.value.event.id}/report`)
  ElMessage.success(`报告 ${data.reportNo} 已生成`)
  window.open(`/reports/${data.id}`, '_blank')
}

async function openEvent(row) {
  drawer.value = true
  detail.value = null
  splitSelected.value = []
  const { data } = await api.get(`/events/${row.id}`)
  detail.value = data
}

async function refreshDetail() {
  if (!detail.value) return
  const { data } = await api.get(`/events/${detail.value.event.id}`)
  detail.value = data
  await load()
}

async function confirmClue(clue) {
  await api.post(`/clues/${clue.id}/confirm`, { status: 'CONFIRMED', note: '审核通过，转入核查' })
  ElMessage.success('线索已确认')
  await refreshDetail()
}

async function createTask(clue) {
  const { data } = await api.post('/inspection-tasks', { clueId: clue.id })
  ElMessage.success(`任务已生成：${data.taskNo}`)
  await refreshDetail()
}

async function dispatchTask(task) {
  await api.post(`/inspection-tasks/${task.id}/dispatch`)
  ElMessage.success('任务已下发到移动端')
  await refreshDetail()
}

async function runAi(clue) {
  await api.post(`/clues/${clue.id}/ai-assessment`)
  ElMessage.success('AI 研判已生成')
  await refreshDetail()
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function fmtLocalTime(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function defaultManualRange() {
  const end = new Date()
  const start = new Date(end.getTime() - 2 * 60 * 60 * 1000)
  return [fmtLocalTime(start), fmtLocalTime(end)]
}

async function loadEnterprises() {
  if (enterprises.value.length) return
  const { data } = await api.get('/enterprises')
  enterprises.value = Array.isArray(data) ? data : []
}

async function onManualEnterprise() {
  manual.pointId = ''
  manual.pollutantCode = ''
  manualPoints.value = []
  manualPollutants.value = []
  if (!manual.enterpriseId) return
  const { data } = await api.get(`/enterprises/${manual.enterpriseId}/profile`)
  manualPoints.value = data.points || []
  if (!manualPoints.value.length) return
  manual.pointId = manualPoints.value[0].id
  await onManualPoint()
}

async function onManualPoint() {
  manual.pollutantCode = ''
  manualPollutants.value = []
  if (!manual.enterpriseId || !manual.pointId) return
  const { data } = await api.get('/monitoring/trend', {
    params: { enterpriseId: manual.enterpriseId, pointId: manual.pointId, hours: 24 }
  })
  manualPollutants.value = data.pollutants || []
  manual.pollutantCode = manualPollutants.value[0]?.code || ''
}

async function openManual() {
  await loadEnterprises()
  manual.enterpriseId = enterprises.value[0]?.id || ''
  manual.pointId = ''
  manual.pollutantCode = ''
  manual.range = defaultManualRange()
  manual.description = ''
  manualVisible.value = true
  if (manual.enterpriseId) await onManualEnterprise()
}

async function submitManualClue() {
  if (!manual.enterpriseId || !manual.pointId || !manual.pollutantCode || !manual.range?.length) {
    ElMessage.warning('请选择企业、监测点、污染物和异常时段')
    return
  }
  if (!manual.description.trim()) {
    ElMessage.warning('请填写线索描述')
    return
  }
  manualSaving.value = true
  try {
    const { data } = await api.post('/clues/manual', {
      enterpriseId: manual.enterpriseId,
      pointId: manual.pointId,
      pollutantCode: manual.pollutantCode,
      startTime: manual.range[0],
      endTime: manual.range[1],
      description: manual.description.trim()
    })
    ElMessage.success(`人工线索 ${data.clueNo} 已创建，并进入事件聚合`)
    manualVisible.value = false
    await load()
    if (data.eventId) {
      const eventRes = await api.get(`/events/${data.eventId}`)
      detail.value = eventRes.data
      drawer.value = true
    }
  } finally {
    manualSaving.value = false
  }
}

watch(() => [filters.status, filters.priority], load)

onMounted(async () => {
  await Promise.all([load(), loadEnterprises()])
  if (route.query.id) {
    const row = events.value.find((e) => String(e.id) === String(route.query.id))
    if (row) openEvent(row)
    else {
      try {
        drawer.value = true
        const { data } = await api.get(`/events/${route.query.id}`)
        detail.value = data
      } catch { drawer.value = false }
    }
  }
})
</script>

<style scoped>
.event-no { font-family: "Cascadia Code", Consolas, monospace; font-size: 9px; color: #7d8da2; }
.event-cell { display: flex; align-items: center; gap: 11px; }
.priority-mark {
  width: 36px; height: 34px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 800; letter-spacing: 0.4px; flex-shrink: 0;
  background: #f2f6fb; color: #7d8da2;
}
.priority-mark.p1 { background: #fff0f0; color: #dc2626; }
.priority-mark.p2 { background: #fff7e8; color: #d97706; }
.priority-mark.p3 { background: #eef6ff; color: #2563eb; }
.event-title { font-size: 13px; font-weight: 700; color: var(--ink); }
.event-sub { margin-top: 4px; font-size: 10px; color: var(--muted); }
.cred-bar { display: flex; align-items: center; gap: 7px; }
.cred-bar .el-progress { flex: 1; }
.cred-bar span { width: 22px; font-size: 11px; color: var(--muted); text-align: right; }
.drawer-body { padding: 6px 4px 24px; }
.drawer-head { padding: 18px 20px 18px; border-radius: 16px; background: linear-gradient(135deg, #0c1e32, #123f53); color: #fff; }
.drawer-title-row { display: flex; gap: 7px; align-items: center; flex-wrap: wrap; }
.report-btn { margin-left: auto; background: rgba(255,255,255,0.12); border-color: rgba(255,255,255,0.25); color: #fff; }
.drawer-head h2 { margin: 13px 0 6px; font-size: 22px; letter-spacing: -0.5px; }
.drawer-head p { margin: 0; font-size: 12px; color: rgba(222, 238, 250, 0.65); }
.drawer-meta { display: flex; flex-wrap: wrap; gap: 14px 22px; margin-top: 16px; padding-top: 13px; border-top: 1px solid rgba(255,255,255,0.1); }
.drawer-meta span { font-size: 10px; color: rgba(222, 238, 250, 0.55); display: flex; gap: 6px; }
.drawer-meta b { color: rgba(255,255,255,0.9); font-weight: 700; }
.section { margin-top: 20px; }
.section-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 11px; }
.section-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.split-check { margin-right: 2px; }
.split-check :deep(.el-checkbox__label) { font-size: 10px; color: #6b7d92; }
.section-head h3 { margin: 0; font-size: 14px; }
.clue-card { border: 1px solid var(--line); border-radius: 15px; padding: 14px; margin-bottom: 12px; background: #fff; }
.clue-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.clue-title { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.clue-time { font-size: 10px; color: var(--muted); }
.clue-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.evidence-grid { display: grid; grid-template-columns: 0.92fr 1.08fr; gap: 11px; margin-top: 12px; }
.evidence-box, .ai-box { background: #f9fcfe; border: 1px solid #eef5fa; border-radius: 12px; padding: 12px; min-width: 0; }
.ai-box { background: #f6fdfb; }
.ai-box.refusal { background: #fffbf5; border-color: #fde9c8; }
.label { font-size: 10px; font-weight: 700; color: var(--muted); letter-spacing: 0.4px; display: flex; justify-content: space-between; gap: 8px; }
.ai-model { font-weight: 400; color: #9aa8bb; }
.metric-row { display: flex; gap: 13px; flex-wrap: wrap; margin-top: 9px; font-size: 10px; color: var(--muted); }
.metric-row b { color: var(--ink); font-size: 13px; margin-left: 3px; }
.evidence-values { margin: 9px 0 0; padding-left: 15px; font-size: 10px; line-height: 1.85; color: #5c6f84; max-height: 150px; overflow: auto; }
.refusal-banner { margin-top: 9px; display: flex; gap: 7px; align-items: center; padding: 8px 10px; border-radius: 8px; background: #fff3e0; color: #b45309; font-size: 10px; }
.ai-summary { margin: 9px 0 0; font-size: 11px; line-height: 1.8; color: #496174; }
.ai-citations { margin-top: 9px; display: flex; flex-direction: column; gap: 7px; }
.citation { border-left: 2px solid #0fb9a6; padding: 4px 0 4px 9px; }
.citation-head { display: flex; flex-direction: column; gap: 2px; }
.citation-head b { font-size: 10px; color: #24465b; }
.citation-head span { font-size: 9px; color: #8a99ad; }
.citation p { margin: 4px 0 0; font-size: 9px; line-height: 1.7; color: #6a7d90; max-height: 62px; overflow: hidden; }
.suggestions { margin-top: 9px; }
.suggest-title { font-size: 10px; font-weight: 700; color: #2c5364; }
.suggestions ul { margin: 4px 0 0; padding-left: 15px; font-size: 9px; line-height: 1.8; color: #5c7286; }
.uncertainty { margin-top: 8px; font-size: 9px; color: #8b7d61; background: #fffaf0; border-radius: 6px; padding: 6px 8px; line-height: 1.6; }
.ai-foot { display: flex; justify-content: space-between; margin-top: 8px; font-size: 9px; color: #8a99ad; }
.ai-foot .red { color: #dc2626; }
.empty-ai { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 110px; color: #9aa8bb; gap: 7px; font-size: 10px; }
.empty-ai .el-icon { font-size: 24px; color: #b9c8d8; }
.task-strip { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 11px; margin-bottom: 7px; background: #fbfdff; }
.task-strip b { font-size: 11px; color: var(--ink); display: block; }
.task-strip span { font-size: 9px; color: var(--muted); display: block; margin-top: 3px; }
.task-strip-actions { display: flex; gap: 6px; }
.manual-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 5px;
  background: #fff7e8;
  color: #b45309;
  font-size: 8px;
  font-weight: 600;
}
.manual-tip {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 10px 12px;
  margin-bottom: 15px;
  border: 1px solid #e3f2ee;
  border-radius: 10px;
  background: #f2fbf9;
  color: #4c7b74;
  font-size: 10px;
  line-height: 1.7;
}
.manual-tip .el-icon { margin-top: 2px; color: var(--teal-dark); flex-shrink: 0; }
.manual-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.manual-form :deep(.el-form-item) { margin-bottom: 14px; }
.manual-form :deep(.el-form-item__label) { padding-bottom: 5px; font-size: 11px; color: #506b7f; }
.manual-empty-tip {
  margin: -4px 0 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fffaf0;
  color: #a36a26;
  font-size: 10px;
  line-height: 1.6;
}
@media (max-width: 700px) {
  .manual-grid { grid-template-columns: 1fr; gap: 0; }
}
@media (max-width: 900px) {
  .evidence-grid { grid-template-columns: 1fr; }
  :deep(.el-drawer) { width: 100% !important; }
}
:deep(.el-table__row) { cursor: pointer; }
</style>
