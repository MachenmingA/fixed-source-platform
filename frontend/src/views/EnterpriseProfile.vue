<template>
  <div class="profile-page" v-loading="loading">
    <section class="profile-hero">
      <div class="hero-grid"></div>
      <div class="hero-glow"></div>

      <div class="hero-top">
        <button class="hero-back" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
        </button>

        <div class="hero-identity">
          <div class="hero-avatar" :style="{ background: avatarGradient }">
            {{ avatarChar }}
          </div>
          <div class="hero-copy">
            <div class="hero-kicker">
              <span></span>
              ENTERPRISE PROFILE · {{ profile.enterprise?.creditCode || '—' }}
            </div>
            <h1>{{ profile.enterprise?.name || '企业画像' }}</h1>
            <div class="hero-tags">
              <span class="risk-tag" :style="{ color: riskColor, background: riskSoftColor }">
                {{ riskLevel }} 级 · {{ riskText }}
              </span>
              <span class="ghost-tag">{{ profile.enterprise?.managementLevel || '—' }}监管</span>
              <span class="ghost-tag">{{ productionMeta(profile.enterprise?.productionStatus).label }}</span>
              <span v-if="profile.enterprise?.status === 'INACTIVE'" class="ghost-tag warn">台账停用</span>
            </div>
            <div class="hero-meta">
              <span><el-icon><OfficeBuilding /></el-icon>{{ profile.enterprise?.industry || '未分类' }}</span>
              <span><el-icon><Location /></el-icon>{{ profile.enterprise?.region || '—' }} · {{ profile.enterprise?.address || '地址未维护' }}</span>
              <span><el-icon><User /></el-icon>{{ profile.enterprise?.contactName || '未维护' }} · {{ profile.enterprise?.contactPhone || '未维护' }}</span>
            </div>
          </div>
        </div>

        <div class="hero-actions">
          <button class="hero-btn" @click="openEditEnterprise">
            <el-icon><EditPen /></el-icon>编辑台账
          </button>
          <button class="hero-btn" @click="openManual">
            <el-icon><Plus /></el-icon>手工标记线索
          </button>
          <button class="hero-btn" @click="goEvents">
            <el-icon><Warning /></el-icon>全部事件
          </button>
          <button class="hero-btn primary" :class="{ spinning: loading }" @click="loadProfile">
            <el-icon><Refresh /></el-icon>刷新
          </button>
        </div>
      </div>

      <div class="hero-stats">
        <div v-for="stat in heroStats" :key="stat.label" class="hero-stat">
          <span>{{ stat.label }}</span>
          <b :style="{ color: stat.color }">{{ stat.value }}<small>{{ stat.unit }}</small></b>
          <em>{{ stat.hint }}</em>
        </div>
      </div>
    </section>

    <div class="profile-body">
      <div class="grid-top">
        <section class="p-card risk-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">RISK ASSESSMENT</span>
              <h2>企业风险画像</h2>
            </div>
            <span class="level-chip" :style="{ color: riskColor, background: riskSoftColor }">
              {{ riskLevel }} 级 · {{ riskText }}
            </span>
          </header>

          <div class="risk-main">
            <div class="risk-ring" :style="ringStyle">
              <div class="ring-inner">
                <strong>{{ scoreValue }}</strong>
                <span>综合风险分</span>
                <em :style="{ color: riskColor }">{{ riskLevel }} 级</em>
              </div>
            </div>
            <div class="factor-list">
              <div v-for="factor in factorList" :key="factor.label" class="factor">
                <div class="factor-head">
                  <span>{{ factor.label }}</span>
                  <b :style="{ color: factor.color }">{{ factor.display }}</b>
                </div>
                <div class="factor-track">
                  <i :style="{ width: `${factor.percent}%`, background: factor.color }"></i>
                </div>
              </div>
            </div>
          </div>

          <div class="advice">
            <span class="advice-icon"><el-icon><Opportunity /></el-icon></span>
            <div>
              <b>监管建议</b>
              <p>{{ riskAdvice }}</p>
            </div>
          </div>

          <div class="p-foot">
            <span>权重版本 {{ profile.risk?.factors?.weightVersion || 'risk-v1' }}</span>
            <span>评分仅作监管参考，不直接作为执法依据</span>
          </div>
        </section>

        <section class="p-card overview-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">REGULATORY OBJECT</span>
              <h2>监管对象概览</h2>
            </div>
            <span class="mini-chip">已关联</span>
          </header>

          <div class="overview-tiles">
            <div v-for="tile in overviewTiles" :key="tile.label" class="overview-tile" :class="`tone-${tile.tone}`">
              <span class="tile-icon"><el-icon><component :is="tile.icon" /></el-icon></span>
              <div>
                <b>{{ tile.value }}<small>{{ tile.unit }}</small></b>
                <span>{{ tile.label }}</span>
              </div>
            </div>
          </div>

          <div class="rule-block">
            <div class="block-title">
              主要命中规则
              <small>{{ (profile.ruleStats || []).length }} 类</small>
            </div>
            <div class="rule-chips">
              <span v-for="rule in profile.ruleStats || []" :key="rule.ruleCode" class="rule-chip">
                {{ rule.ruleName }} <b>{{ rule.count }}</b>
              </span>
              <span v-if="!(profile.ruleStats || []).length" class="muted tiny">暂无规则命中</span>
            </div>
          </div>

          <div class="score-note">
            <div class="block-title">评分口径</div>
            <p>风险分综合事件严重度、数据完整率、待办任务和历史反馈；权重版本 {{ profile.risk?.factors?.weightVersion || 'risk-v1' }}。</p>
          </div>
        </section>
      </div>

      <section class="p-card trend-card">
        <header class="p-head">
          <div>
            <span class="p-kicker">MONITORING TREND</span>
            <h2>监测趋势与状态</h2>
          </div>
          <div class="trend-filters">
            <el-select v-model="pointId" placeholder="选择排口" style="width:196px" @change="onPointChange">
              <el-option v-for="p in trend.points || []" :key="p.id" :label="p.pointName" :value="p.id" />
            </el-select>
            <el-select v-model="pollutantCode" placeholder="选择污染物" style="width:164px" @change="loadTrend">
              <el-option v-for="p in trend.pollutants || []" :key="p.code" :label="`${p.name} (${p.unit})`" :value="p.code" />
            </el-select>
            <span class="standard-ref">{{ trend.pollutant?.standardRef || '标准依据待维护' }}</span>
            <router-link class="analysis-link" :to="{ path: '/monitoring', query: { enterpriseId: route.params.id, pointId, pollutantCode } }">
              <el-icon><TrendCharts /></el-icon> 图表分析
            </router-link>
          </div>
        </header>

        <template v-if="(trend.series || []).length">
          <div class="trend-summary">
            <div>
              <span>当前污染物</span>
              <b>{{ trend.pollutant?.name || '—' }}</b>
            </div>
            <div>
              <span>执行限值</span>
              <b>{{ trend.pollutant?.limitValue ?? '—' }}<small>{{ trend.pollutant?.unit }}</small></b>
            </div>
            <div>
              <span>数据点</span>
              <b>{{ trend.series.length }}<small>个</small></b>
            </div>
            <div>
              <span>超限 / 异常点</span>
              <b :class="{ danger: alertPoints > 0 }">{{ alertPoints }}<small>个</small></b>
            </div>
          </div>
          <EChart :option="trendOption" height="330px" />
        </template>

        <div v-else class="trend-empty">
          <div class="empty-icon"><el-icon><DataLine /></el-icon></div>
          <b>暂无监测数据</b>
          <p v-if="(profile.points || []).length">
            该排口与污染物还没有采集到有效数据，可以先手工标记线索，或检查采集批次与数据质量。
          </p>
          <p v-else>尚未维护监测点位，请先在台账中新增排口与监测点，再等待每日采集。</p>
          <div class="empty-actions">
            <button v-if="(profile.points || []).length" class="ghost-btn" @click="openManual">手工标记线索</button>
            <button class="ghost-btn" @click="openCreatePoint">新增监测点</button>
          </div>
        </div>
      </section>

      <div class="grid-mid">
        <section class="p-card events-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">ABNORMAL EVENTS</span>
              <h2>异常事件</h2>
            </div>
            <button class="head-link" @click="goEvents">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </button>
          </header>

          <div v-if="(profile.events || []).length" class="event-feed">
            <article
              v-for="event in (profile.events || []).slice(0, 6)"
              :key="event.id"
              class="event-row"
              @click="openEvent(event)"
            >
              <span class="event-priority" :class="String(event.priority || '').toLowerCase()">{{ event.priority }}</span>
              <div class="event-copy">
                <b>{{ event.title }}</b>
                <span>{{ event.pointName }} · {{ event.startTime }}</span>
              </div>
              <span class="event-status" :class="`s-${String(event.status || '').toLowerCase()}`">
                {{ statusMeta(event.status).label }}
              </span>
              <div class="event-cred">
                <i :style="{ width: `${Math.min(100, event.credibility || 0)}%` }"></i>
                <em>{{ event.credibility || 0 }}</em>
              </div>
            </article>
          </div>
          <div v-else class="p-empty">
            <el-icon><CircleCheck /></el-icon>
            <span>暂无异常事件，监测状态良好</span>
          </div>
        </section>

        <section class="p-card points-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">OUTLETS & POINTS</span>
              <h2>排口与监测点</h2>
            </div>
            <div class="head-actions">
              <button v-if="canMaintain" class="head-link" @click="openCreatePoint">
                <el-icon><Plus /></el-icon> 新增点位
              </button>
              <span class="mini-chip">{{ (profile.points || []).length }} 个</span>
            </div>
          </header>

          <div class="point-list">
            <article v-for="point in profile.points || []" :key="point.id" class="point-item">
              <div class="point-icon" :class="point.pointType === '废水' ? 'water' : 'air'">
                <el-icon><Connection /></el-icon>
              </div>
              <div class="point-main">
                <b>{{ point.pointName }}</b>
                <span>{{ point.outletName }} · {{ point.pointType }} · {{ point.pollutantCount }} 项指标</span>
                <em>最近数据 {{ point.lastMonitorTime || '—' }}</em>
              </div>
              <div class="point-side">
                <span class="point-status" :class="point.status.toLowerCase()">{{ pointStatusMeta(point.status).label }}</span>
                <button v-if="canMaintain" class="icon-edit" @click="openEditPoint(point)">
                  <el-icon><EditPen /></el-icon>
                </button>
              </div>
            </article>
            <div v-if="!(profile.points || []).length" class="p-empty compact">
              <el-icon><Connection /></el-icon><span>尚未维护排口与监测点</span>
            </div>
          </div>

          <div class="task-block">
            <div class="block-title">最近核查任务 <small>{{ (profile.tasks || []).length }} 件</small></div>
            <div v-for="task in (profile.tasks || []).slice(0, 4)" :key="task.id" class="task-row">
              <span class="task-no mono">{{ task.taskNo }}</span>
              <span class="task-title">{{ task.title }}</span>
              <span class="task-status" :class="`s-${String(task.status || '').toLowerCase()}`">
                {{ statusMeta(task.status).label }}
              </span>
            </div>
            <div v-if="!(profile.tasks || []).length" class="muted tiny">暂无核查任务</div>
          </div>
        </section>
      </div>

      <div class="grid-bottom">
        <section class="p-card archive-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">ENTERPRISE ARCHIVE</span>
              <h2>企业档案与监管信息</h2>
            </div>
            <div class="head-actions">
              <span class="mini-chip">{{ enterpriseStatusMeta(profile.enterprise?.status).label }}</span>
              <button v-if="canMaintain" class="head-link" @click="openEditEnterprise">
                <el-icon><EditPen /></el-icon> 维护档案
              </button>
            </div>
          </header>

          <div class="archive-grid">
            <div
              v-for="item in archiveItems"
              :key="item.label"
              class="archive-item"
              :class="{ 'span-2': item.span }"
            >
              <span class="archive-icon"><el-icon><component :is="item.icon" /></el-icon></span>
              <div>
                <em>{{ item.label }}</em>
                <b :class="{ mono: item.mono }">{{ item.value }}</b>
              </div>
            </div>
          </div>
        </section>

        <section class="p-card location-card">
          <header class="p-head">
            <div>
              <span class="p-kicker">GEO LOCATION</span>
              <h2>企业位置</h2>
            </div>
            <span class="mini-chip">{{ profile.enterprise?.region || '—' }}</span>
          </header>

          <div v-if="enterpriseMarker.length" class="location-map">
            <BeijingRiskMap
              :enterprises="enterpriseMarker"
              :regions="[]"
              layer="density"
              variant="light"
              :selected-enterprise-id="profile.enterprise?.id"
            />
          </div>
          <div v-else class="location-empty">
            <el-icon><Coordinate /></el-icon>
            <span>尚未维护企业坐标</span>
            <button v-if="canMaintain" class="ghost-btn" @click="openEditEnterprise">去地图选点</button>
          </div>

          <div class="location-foot">
            <div><span>坐标</span><b class="mono">{{ coordinateText }}</b></div>
            <div><span>点位</span><b>{{ (profile.points || []).length }} 个</b></div>
          </div>
        </section>
      </div>
    </div>

    <el-dialog v-model="manualVisible" title="手工标记线索" width="560px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="监测点">
          <el-select v-model="manual.pointId" style="width:100%" @change="loadManualPollutants">
            <el-option v-for="p in profile.points || []" :key="p.id" :label="`${p.pointName}（${p.outletName}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="污染物">
          <el-select v-model="manual.pollutantCode" style="width:100%">
            <el-option v-for="p in manualPollutants" :key="p.code" :label="`${p.name}（${p.unit}）`" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间区间">
          <el-date-picker v-model="manual.range" type="datetimerange" value-format="YYYY-MM-DD HH:mm"
            range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="线索说明">
          <el-input v-model="manual.description" type="textarea" :rows="3" placeholder="说明人工标记的原因、现场观察或数据疑点" />
        </el-form-item>
      </el-form>
      <div class="manual-tip">人工线索会进入正式线索清单，并触发同源事件聚合；AI 研判仍只作为辅助。</div>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSubmitting" @click="submitManual">提交线索</el-button>
      </template>
    </el-dialog>

    <EnterpriseEditDialog v-model="enterpriseEditVisible" :enterprise="profile.enterprise" @saved="loadProfile" />
    <PointEditDialog v-model="pointEditVisible" :enterprise-id="route.params.id" :point="editingPoint" @saved="loadProfile" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, CircleCheck, Clock, Connection, Coordinate, DataLine, Document,
  EditPen, Location, Memo, OfficeBuilding, Opportunity, Phone, Plus, Refresh, SetUp,
  Ticket, TrendCharts, User, Warning
} from '@element-plus/icons-vue'
import api from '../api'
import { ElMessage } from 'element-plus'
import EChart from '../components/EChart.vue'
import BeijingRiskMap from '../components/BeijingRiskMap.vue'
import EnterpriseEditDialog from '../components/EnterpriseEditDialog.vue'
import PointEditDialog from '../components/PointEditDialog.vue'
import { statusMeta } from '../utils/labels'
import { enterpriseStatusMeta, pointStatusMeta, productionMeta } from '../utils/dicts'
import { hasRole } from '../store'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const profile = ref({})
const trend = ref({ points: [], pollutants: [], series: [] })
const pointId = ref('')
const pollutantCode = ref('')
const manualVisible = ref(false)
const manualSubmitting = ref(false)
const manualPollutants = ref([])
const manual = ref({ pointId: '', pollutantCode: '', range: [], description: '' })
const enterpriseEditVisible = ref(false)
const pointEditVisible = ref(false)
const editingPoint = ref(null)
const canMaintain = computed(() => hasRole('reviewer', 'admin'))

const RISK_COLORS = { A: '#22c55e', B: '#3b82f6', C: '#f59e0b', D: '#ef4444' }
const RISK_SOFTS = { A: '#e8f8ef', B: '#e9f1fd', C: '#fff5e4', D: '#fdeceb' }

const riskLevel = computed(() => profile.value.enterprise?.riskLevel || 'B')
const riskText = computed(() => ({
  A: '低风险',
  B: '一般风险',
  C: '较高风险',
  D: '高风险'
}[riskLevel.value] || '风险待定'))
const riskColor = computed(() => RISK_COLORS[riskLevel.value] || '#3b82f6')
const riskSoftColor = computed(() => RISK_SOFTS[riskLevel.value] || '#e9f1fd')
const scoreValue = computed(() => Number(profile.value.enterprise?.riskScore || 0))
const avatarChar = computed(() => (profile.value.enterprise?.name || '企').slice(0, 1))
const avatarGradient = computed(() =>
  `linear-gradient(135deg, ${riskColor.value}, ${riskColor.value}bb)`
)

const completeness = computed(() => Number(profile.value.risk?.factors?.dataCompleteness ?? 100))
const pendingEvents = computed(() => (profile.value.events || []).filter((item) => item.status !== 'CLOSED').length)
const closedTasks = computed(() => (profile.value.tasks || []).filter((item) => item.status === 'CLOSED').length)

const coordinateText = computed(() => {
  const enterprise = profile.value.enterprise
  if (!enterprise || enterprise.longitude == null || enterprise.latitude == null) return '未维护'
  return `${Number(enterprise.longitude).toFixed(5)}, ${Number(enterprise.latitude).toFixed(5)}`
})

const ringStyle = computed(() => ({
  background: `conic-gradient(${riskColor.value} ${scoreValue.value}%, #e9f0f5 ${scoreValue.value}% 100%)`
}))

const heroStats = computed(() => [
  { label: '风险分', value: scoreValue.value, unit: '分', color: riskColor.value, hint: `${riskLevel.value} 级` },
  { label: '监测点位', value: (profile.value.points || []).length, unit: '个', color: '#3b82f6', hint: `正常 ${(profile.value.points || []).filter((p) => p.status === 'NORMAL').length}` },
  { label: '异常事件', value: (profile.value.events || []).length, unit: '件', color: '#e8a23d', hint: `待复核 ${pendingEvents.value}` },
  { label: '核查任务', value: (profile.value.tasks || []).length, unit: '件', color: '#8e6fca', hint: `已关闭 ${closedTasks.value}` },
  { label: '数据完整率', value: completeness.value, unit: '%', color: '#2aa198', hint: '近 24 小时采集' },
  { label: '历史反馈', value: Number(profile.value.risk?.factors?.feedbackCount ?? 0), unit: '条', color: '#5c8f9c', hint: '现场核查记录' }
])

const overviewTiles = computed(() => [
  { label: '监测点位', value: (profile.value.points || []).length, unit: '个', icon: Connection, tone: 'blue' },
  { label: '异常事件', value: (profile.value.events || []).length, unit: '件', icon: Warning, tone: 'amber' },
  { label: '核查任务', value: (profile.value.tasks || []).length, unit: '件', icon: Ticket, tone: 'violet' },
  { label: '数据完整率', value: completeness.value, unit: '%', icon: CircleCheck, tone: 'green' }
])

const factorList = computed(() => {
  const f = profile.value.risk?.factors || {}
  const high = Number(f.highEvents ?? 0)
  const medium = Number(f.mediumEvents ?? 0)
  const low = Number(f.lowEvents ?? 0)
  const pending = Number(f.pendingTasks ?? 0)
  const feedback = Number(f.feedbackCount ?? 0)
  return [
    { label: '高风险事件', display: `${high} 件`, percent: Math.min(100, high * 30 + (high ? 16 : 0)), color: '#e35d5b' },
    { label: '中风险事件', display: `${medium} 件`, percent: Math.min(100, medium * 18 + (medium ? 12 : 0)), color: '#e8a23d' },
    { label: '低风险事件', display: `${low} 件`, percent: Math.min(100, low * 10 + (low ? 8 : 0)), color: '#4c86c6' },
    { label: '数据完整率', display: `${completeness.value}%`, percent: completeness.value, color: '#2aa198' },
    { label: '待办任务', display: `${pending} 件`, percent: Math.min(100, pending * 25 + (pending ? 15 : 0)), color: '#8e6fca' },
    { label: '历史反馈', display: `${feedback} 条`, percent: Math.min(100, feedback * 12 + (feedback ? 8 : 0)), color: '#5f9ea0' }
  ]
})

const archiveItems = computed(() => {
  const e = profile.value.enterprise || {}
  return [
    { label: '企业地址', value: e.address || '未维护', icon: Location, span: true },
    { label: '法定代表人', value: e.legalRepresentative || '未维护', icon: User },
    { label: '环保联系人', value: e.contactName || '未维护', icon: User },
    { label: '联系电话', value: e.contactPhone || '未维护', icon: Phone },
    { label: '排污许可证号', value: e.permitNo || '未登记', icon: Document, mono: true },
    { label: '生产状态', value: productionMeta(e.productionStatus).label, icon: SetUp },
    { label: '企业坐标', value: coordinateText.value, icon: Coordinate, mono: true },
    { label: '台账更新时间', value: e.updatedAt || '—', icon: Clock },
    { label: '备注', value: e.remark || '暂无备注', icon: Memo, span: true }
  ]
})

const enterpriseMarker = computed(() => {
  const e = profile.value.enterprise
  if (!e || e.longitude == null || e.latitude == null) return []
  return [{
    id: e.id,
    name: e.name,
    industry: e.industry,
    region: e.region,
    riskLevel: e.riskLevel,
    riskScore: e.riskScore,
    productionStatus: e.productionStatus,
    longitude: Number(e.longitude),
    latitude: Number(e.latitude),
    pointCount: (profile.value.points || []).length,
    pendingEvents: pendingEvents.value
  }]
})

const riskAdvice = computed(() => {
  return {
    A: '低风险企业，保持常规监测和年度核查，重点关注数据连续性与自动监测设备运行状态。',
    B: '按常规频次监管，持续关注规则命中趋势、缺失数据和现场反馈。',
    C: '建议提高巡查频次，优先复核高、中风险事件与缺失数据，必要时安排现场核查。',
    D: '建议纳入重点监管，优先安排现场核查并跟踪整改闭环，同步关注相邻时段数据。'
  }[riskLevel.value] || '暂无足够数据生成监管建议。'
})

const alertPoints = computed(() => {
  const limit = Number(trend.value.pollutant?.limitValue || 0)
  return (trend.value.series || []).filter((x) =>
    (x.status && x.status !== 'NORMAL') || (limit > 0 && Number(x.value) > limit)
  ).length
})

const trendOption = computed(() => {
  const series = trend.value.series || []
  const limit = Number(trend.value.pollutant?.limitValue || 0)
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(12, 36, 61, .96)',
      borderColor: 'rgba(22, 143, 125, .45)',
      textStyle: { color: '#e8f7fa', fontSize: 11 },
      formatter: (items) => {
        const point = items[0]
        const row = series[point.dataIndex] || {}
        return `${point.axisValue}<br/>实测值：<b>${point.data}</b> ${trend.value.pollutant?.unit || ''}<br/>状态：${row.status || 'NORMAL'}`
      }
    },
    grid: { left: 48, right: 24, top: 30, bottom: 42 },
    xAxis: {
      type: 'category',
      data: series.map((x) => String(x.time).slice(5)),
      axisLine: { lineStyle: { color: '#dfe8ef' } },
      axisTick: { show: false },
      axisLabel: { color: '#8ba0b4', fontSize: 10, interval: Math.max(0, Math.floor(series.length / 8) - 1) }
    },
    yAxis: {
      type: 'value',
      name: trend.value.pollutant?.unit || '',
      nameTextStyle: { color: '#9aacb7', fontSize: 10 },
      splitLine: { lineStyle: { color: '#eef4f7', type: 'dashed' } },
      axisLabel: { color: '#8ba0b4', fontSize: 10 }
    },
    series: [{
      type: 'line',
      data: series.map((x) => x.value),
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2.6, color: '#168f7d' },
      itemStyle: {
        color: (p) => (series[p.dataIndex]?.status && series[p.dataIndex].status !== 'NORMAL' ? '#e05a55' : '#168f7d')
      },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(22,143,125,0.24)' }, { offset: 1, color: 'rgba(22,143,125,0.01)' }]
        }
      },
      markLine: limit ? {
        silent: true,
        symbol: 'none',
        lineStyle: { color: '#e05a55', type: 'dashed', width: 1.4 },
        label: { formatter: `限值 ${limit}`, color: '#e05a55', fontSize: 10, position: 'insideEndTop' },
        data: [{ yAxis: limit }]
      } : undefined
    }]
  }
})

async function loadProfile() {
  loading.value = true
  try {
    const { data } = await api.get(`/enterprises/${route.params.id}/profile`)
    profile.value = data || {}
    if ((data?.points || []).length) {
      pointId.value = pointId.value || data.points[0].id
      await loadTrend()
    } else {
      trend.value = { points: [], pollutants: [], series: [] }
    }
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  if (!pointId.value) return
  const { data } = await api.get('/monitoring/trend', {
    params: { enterpriseId: route.params.id, pointId: pointId.value, pollutantCode: pollutantCode.value, hours: 72 }
  })
  trend.value = data
  pointId.value = data.pointId
  pollutantCode.value = data.pollutantCode
}

function onPointChange() {
  pollutantCode.value = ''
  loadTrend()
}

function fmtDateTime(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function openManual() {
  const firstPoint = (profile.value.points || [])[0]
  if (!firstPoint) return
  const end = new Date()
  const start = new Date(end.getTime() - 2 * 60 * 60 * 1000)
  manual.value = { pointId: firstPoint.id, pollutantCode: '', range: [fmtDateTime(start), fmtDateTime(end)], description: '' }
  manualVisible.value = true
  await loadManualPollutants()
}

async function loadManualPollutants() {
  if (!manual.value.pointId) return
  const { data } = await api.get('/monitoring/trend', {
    params: { enterpriseId: route.params.id, pointId: manual.value.pointId, hours: 24 }
  })
  manualPollutants.value = data.pollutants || []
  manual.value.pollutantCode = manualPollutants.value[0]?.code || ''
}

async function submitManual() {
  if (!manual.value.pointId || !manual.value.pollutantCode || !manual.value.range?.length) {
    ElMessage.warning('请选择监测点、污染物和时间区间')
    return
  }
  manualSubmitting.value = true
  try {
    await api.post('/clues/manual', {
      enterpriseId: route.params.id,
      pointId: manual.value.pointId,
      pollutantCode: manual.value.pollutantCode,
      startTime: manual.value.range[0],
      endTime: manual.value.range[1],
      description: manual.value.description || '人工标记线索'
    })
    ElMessage.success('人工线索已创建')
    manualVisible.value = false
    await loadProfile()
  } finally {
    manualSubmitting.value = false
  }
}

function openEvent(row) {
  router.push({ path: '/events', query: { id: row.id } })
}

function goEvents() {
  router.push({ path: '/events', query: { enterpriseId: route.params.id } })
}

function ensureMaintainer() {
  if (canMaintain.value) return true
  ElMessage.warning('当前账号只读，请使用审核人员或管理员账号维护企业台账')
  return false
}

function openEditEnterprise() {
  if (!ensureMaintainer()) return
  enterpriseEditVisible.value = true
}

function openCreatePoint() {
  if (!ensureMaintainer()) return
  editingPoint.value = null
  pointEditVisible.value = true
}

function openEditPoint(point) {
  if (!ensureMaintainer()) return
  editingPoint.value = { ...point }
  pointEditVisible.value = true
}

onMounted(async () => {
  await loadProfile()
  if (route.query.edit === '1' && canMaintain.value) {
    enterpriseEditVisible.value = true
  }
})
</script>

<style scoped>
.profile-page {
  --ink: #17384e;
  --ink-2: #3c5a6e;
  --muted: #8298a5;
  --line: #e3ecf1;
  --card: #ffffff;
  padding-bottom: 10px;
}

/* ===== Hero ===== */
.profile-hero {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  color: #eefcff;
  background:
    radial-gradient(circle at 82% 18%, rgba(48, 182, 187, 0.22), transparent 34%),
    linear-gradient(118deg, #071f37 0%, #0d3c5c 52%, #0e5f60 100%);
  box-shadow: 0 16px 38px rgba(11, 47, 86, 0.16);
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(116, 214, 211, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(116, 214, 211, 0.06) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: linear-gradient(90deg, #000, transparent 82%);
  pointer-events: none;
}
.hero-glow {
  position: absolute;
  right: -80px;
  top: -90px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  border: 1px solid rgba(151, 232, 226, 0.14);
  box-shadow: 0 0 0 38px rgba(151, 232, 226, 0.035), 0 0 0 76px rgba(151, 232, 226, 0.02);
  pointer-events: none;
}
.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 22px 26px 18px;
}
.hero-back {
  width: 36px;
  height: 36px;
  margin-top: 3px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.08);
  color: #d8f4f3;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}
.hero-back:hover { background: rgba(255, 255, 255, 0.16); }
.hero-identity {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 15px;
}
.hero-avatar {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #06262b;
  font-size: 26px;
  font-weight: 800;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
  flex-shrink: 0;
}
.hero-copy { min-width: 0; }
.hero-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #7fd8d0;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  letter-spacing: 1.6px;
}
.hero-kicker span {
  width: 16px;
  height: 1px;
  background: linear-gradient(90deg, #5fe3d5, transparent);
}
.hero-copy h1 {
  margin: 7px 0 8px;
  font-size: 26px;
  line-height: 1.25;
  letter-spacing: 0.5px;
  color: #f4feff;
}
.hero-tags { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.risk-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}
.ghost-tag {
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.07);
  color: #bfe6e7;
  font-size: 9px;
}
.ghost-tag.warn { color: #f5c96e; border-color: rgba(245, 201, 110, 0.32); }
.hero-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: rgba(215, 240, 243, 0.6);
  font-size: 10px;
}
.hero-meta span { display: inline-flex; align-items: center; gap: 5px; }
.hero-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 420px;
}
.hero-btn {
  height: 33px;
  padding: 0 11px;
  border-radius: 9px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.08);
  color: #ddf5f4;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.hero-btn:hover { background: rgba(255, 255, 255, 0.16); }
.hero-btn.primary {
  border-color: rgba(94, 231, 215, 0.5);
  background: linear-gradient(135deg, #2bc9b8, #168f7d);
  color: #04292b;
  font-weight: 700;
}
.hero-btn .el-icon { font-size: 13px; }
.hero-btn.spinning .el-icon { animation: hero-spin 0.9s linear infinite; }

.hero-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 1px;
  padding: 0 16px 18px;
}
.hero-stat {
  padding: 11px 13px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.hero-stat span { display: block; color: rgba(207, 236, 238, 0.55); font-size: 9px; }
.hero-stat b { display: block; margin-top: 3px; font-size: 21px; line-height: 1.1; letter-spacing: -0.4px; }
.hero-stat b small { margin-left: 2px; font-size: 9px; font-weight: 400; color: rgba(207, 236, 238, 0.5); }
.hero-stat em { display: block; margin-top: 3px; color: rgba(196, 228, 231, 0.42); font-size: 8px; font-style: normal; }

/* ===== Body / cards ===== */
.profile-body { display: flex; flex-direction: column; gap: 15px; margin-top: 15px; }
.grid-top { display: grid; grid-template-columns: 1.12fr 0.88fr; gap: 15px; align-items: stretch; }
.grid-mid { display: grid; grid-template-columns: 1.08fr 0.92fr; gap: 15px; align-items: stretch; }
.grid-bottom { display: grid; grid-template-columns: 1.45fr 0.75fr; gap: 15px; align-items: stretch; }

.p-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(25, 63, 88, 0.045);
  padding: 18px 20px;
}
.p-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.p-kicker {
  display: block;
  color: #97aab6;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 7px;
  letter-spacing: 1.4px;
}
.p-head h2 { margin: 4px 0 0; color: var(--ink); font-size: 15px; font-weight: 700; }
.p-foot {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 14px;
  padding-top: 11px;
  border-top: 1px dashed var(--line);
  color: #9aacb7;
  font-size: 9px;
}
.level-chip, .mini-chip {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
}
.mini-chip { color: #6e8896; background: #f1f6f9; }
.head-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 0;
  background: transparent;
  color: #168f7d;
  font-size: 10px;
  cursor: pointer;
  padding: 2px;
}
.head-link:hover { color: #0f6f61; }

/* 风险画像 */
.risk-main { display: flex; align-items: center; gap: 26px; }
.risk-ring {
  width: 156px;
  height: 156px;
  border-radius: 50%;
  padding: 12px;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(226, 237, 242, 0.9), 0 14px 30px rgba(15, 33, 55, 0.1);
}
.ring-inner {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px #f1f6f8;
}
.ring-inner strong { color: var(--ink); font-size: 40px; line-height: 1; letter-spacing: -1.5px; }
.ring-inner span { margin-top: 6px; color: #91a5b1; font-size: 9px; }
.ring-inner em { margin-top: 3px; font-size: 11px; font-weight: 700; font-style: normal; }
.factor-list { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 9px; }
.factor-head { display: flex; align-items: baseline; justify-content: space-between; font-size: 10px; color: #7d93a0; }
.factor-head b { font-size: 11px; }
.factor-track {
  height: 5px;
  margin-top: 5px;
  border-radius: 4px;
  background: #eef4f7;
  overflow: hidden;
}
.factor-track i { display: block; height: 100%; border-radius: 4px; transition: width 0.5s ease; }
.advice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 15px;
  padding: 11px 12px;
  border-radius: 11px;
  background: linear-gradient(120deg, #f1faf8, #f7fcfb);
  border: 1px solid #dff2ed;
}
.advice-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #d8f3ee;
  color: #117e6d;
  font-size: 14px;
  flex-shrink: 0;
}
.advice b { display: block; color: #256f64; font-size: 10px; }
.advice p { margin: 4px 0 0; color: #5c807b; font-size: 10px; line-height: 1.7; }

/* 概览 */
.overview-tiles { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.overview-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #ecf3f6;
}
.tile-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  flex-shrink: 0;
}
.overview-tile.tone-blue .tile-icon { background: linear-gradient(135deg, #4a91d8, #2d6da9); }
.overview-tile.tone-amber .tile-icon { background: linear-gradient(135deg, #eabf63, #d79c35); }
.overview-tile.tone-violet .tile-icon { background: linear-gradient(135deg, #8a92d9, #6570bd); }
.overview-tile.tone-green .tile-icon { background: linear-gradient(135deg, #59b88b, #2f9168); }
.overview-tile b { display: block; color: var(--ink); font-size: 20px; line-height: 1.1; }
.overview-tile b small { margin-left: 2px; font-size: 9px; font-weight: 400; color: #94a7b2; }
.overview-tile span { display: block; margin-top: 2px; color: #8ba0ad; font-size: 9px; }
.rule-block { margin-top: 14px; }
.block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: #6d8492;
  font-size: 10px;
  font-weight: 700;
}
.block-title small { color: #9db0ba; font-weight: 400; }
.rule-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.rule-chip {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f2f8fc;
  color: #5b7189;
  font-size: 10px;
}
.rule-chip b { margin-left: 3px; color: #168f7d; }
.score-note { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line); }
.score-note p { margin: 5px 0 0; color: #8da1ad; font-size: 9px; line-height: 1.7; }

/* 趋势 */
.trend-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.standard-ref {
  max-width: 200px;
  padding: 5px 9px;
  border-radius: 999px;
  background: #f1f8f6;
  color: #3f857a;
  font-size: 9px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.analysis-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 9px;
  border: 1px solid #dcebe8;
  border-radius: 999px;
  color: var(--teal-dark);
  background: #f5fcfb;
  font-size: 9px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.18s ease;
}
.analysis-link:hover { border-color: #9edfd5; background: #eaf9f6; }
.trend-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #edf3f6;
}
.trend-summary div { text-align: left; }
.trend-summary span { display: block; color: #8ba0ad; font-size: 9px; }
.trend-summary b { display: block; margin-top: 4px; color: var(--ink); font-size: 17px; }
.trend-summary b small { margin-left: 2px; font-size: 9px; color: #94a7b2; font-weight: 400; }
.trend-summary b.danger { color: #d95752; }
.trend-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 7px;
  text-align: center;
}
.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f9f8;
  color: #46a596;
  font-size: 26px;
}
.trend-empty b { color: #4d6b7c; font-size: 14px; }
.trend-empty p { max-width: 460px; margin: 0; color: #93a7b2; font-size: 10px; line-height: 1.7; }
.empty-actions { display: flex; gap: 8px; margin-top: 6px; }
.ghost-btn {
  height: 32px;
  padding: 0 13px;
  border-radius: 9px;
  border: 1px solid #cfe6e3;
  background: #f7fdfc;
  color: #168f7d;
  font-size: 10px;
  cursor: pointer;
}
.ghost-btn:hover { border-color: #93d2c9; }

/* 事件 */
.event-feed { display: flex; flex-direction: column; gap: 7px; }
.event-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 11px;
  border-radius: 11px;
  border: 1px solid #eef4f7;
  background: #fbfdfe;
  cursor: pointer;
  transition: all 0.2s ease;
}
.event-row:hover { border-color: #cbe8e3; background: #f6fcfa; transform: translateX(2px); }
.event-priority {
  width: 27px;
  height: 27px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  background: #f1f5f8;
  color: #8497a4;
  flex-shrink: 0;
}
.event-priority.p1 { background: #fdeceb; color: #d95752; }
.event-priority.p2 { background: #fff5e4; color: #c98424; }
.event-priority.p3 { background: #e9f1fd; color: #3b7ac0; }
.event-copy { flex: 1; min-width: 0; }
.event-copy b { display: block; color: #2c485c; font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.event-copy span { display: block; margin-top: 3px; color: #94a7b2; font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.event-status {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 8px;
  white-space: nowrap;
}
.event-status.s-pending { color: #b06f1c; background: #fff5e4; }
.event-status.s-confirmed { color: #3b7ac0; background: #e9f1fd; }
.event-status.s-dispatched { color: #8a6dbf; background: #f2edfc; }
.event-status.s-in_progress { color: #168f7d; background: #e8f8f4; }
.event-status.s-closed { color: #8397a3; background: #f1f5f7; }
.event-cred { width: 62px; flex-shrink: 0; }
.event-cred i { display: block; height: 4px; border-radius: 3px; background: linear-gradient(90deg, #9adfd5, #168f7d); }
.event-cred em { display: block; margin-top: 3px; color: #8fa4af; font-size: 8px; font-style: normal; text-align: right; }

/* 点位 */
.point-list { display: flex; flex-direction: column; gap: 8px; }
.point-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #fbfdfe;
  border: 1px solid #eef4f7;
}
.point-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  flex-shrink: 0;
}
.point-icon.air { background: linear-gradient(135deg, #357fc0, #1d5d91); }
.point-icon.water { background: linear-gradient(135deg, #2bb5a4, #148675); }
.point-main { flex: 1; min-width: 0; }
.point-main b { display: block; color: #2c485c; font-size: 11px; }
.point-main span { display: block; margin-top: 3px; color: #8fa4af; font-size: 9px; }
.point-main em { display: block; margin-top: 3px; color: #a3b4bd; font-size: 8px; font-style: normal; }
.point-side { display: flex; align-items: center; gap: 5px; flex-shrink: 0; }
.point-status { padding: 3px 8px; border-radius: 999px; font-size: 8px; }
.point-status.normal { color: #1f8a68; background: #e8f8f1; }
.point-status.maintenance { color: #b06f1c; background: #fff5e4; }
.point-status.stopped { color: #8397a3; background: #f1f5f7; }
.icon-edit {
  border: 0;
  background: transparent;
  color: #9aacb7;
  cursor: pointer;
  padding: 2px;
}
.icon-edit:hover { color: #168f7d; }
.task-block { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line); }
.task-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed #f0f5f7;
  font-size: 10px;
}
.task-row:last-child { border-bottom: 0; }
.task-no { color: #8fa4af; font-size: 9px; }
.task-title { flex: 1; min-width: 0; color: #3c5a6e; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-status { padding: 3px 7px; border-radius: 999px; font-size: 8px; white-space: nowrap; }
.task-status.s-closed { color: #8397a3; background: #f1f5f7; }
.task-status.s-dispatched { color: #b06f1c; background: #fff5e4; }
.task-status.s-in_progress { color: #168f7d; background: #e8f8f4; }
.task-status.s-feedback { color: #3b7ac0; background: #e9f1fd; }
.task-status.s-pending_dispatch { color: #8a6dbf; background: #f2edfc; }

/* 档案 */
.archive-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.archive-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #eef4f7;
  min-width: 0;
}
.archive-item.span-2 { grid-column: span 2; }
.archive-icon {
  width: 27px;
  height: 27px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eaf5f3;
  color: #168f7d;
  font-size: 13px;
  flex-shrink: 0;
}
.archive-item em { display: block; color: #93a7b2; font-size: 8px; font-style: normal; }
.archive-item b {
  display: block;
  margin-top: 4px;
  color: #33576b;
  font-size: 11px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 位置 */
.location-map {
  height: 240px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #edf3f6;
  background: #fafdfe;
}
.location-map :deep(.risk-map-wrap),
.location-map :deep(.risk-map) { min-height: 0 !important; height: 100%; }
.location-empty {
  height: 240px;
  border-radius: 12px;
  border: 1px dashed #d7e5e8;
  background: #fbfdfe;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a7b2;
  font-size: 10px;
}
.location-empty .el-icon { font-size: 26px; color: #b9c9d1; }
.location-foot {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 10px;
  margin-top: 11px;
}
.location-foot div {
  padding: 9px 10px;
  border-radius: 10px;
  background: #f8fbfd;
  border: 1px solid #eef4f7;
  min-width: 0;
}
.location-foot span { display: block; color: #94a7b2; font-size: 8px; }
.location-foot b { display: block; margin-top: 3px; color: #33576b; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.p-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 16px;
  color: #94a7b2;
  font-size: 10px;
}
.p-empty .el-icon { font-size: 26px; color: #c3d2d9; }
.p-empty.compact { padding: 22px 10px; }
.muted { color: #9aacb7; }
.tiny { font-size: 10px; }
.mono { font-family: "Cascadia Code", Consolas, monospace; }
.manual-tip { margin-top: 6px; padding: 9px 11px; border-radius: 9px; background: #f3fbf9; color: #4c7b74; font-size: 10px; line-height: 1.7; }

@keyframes hero-spin { to { transform: rotate(360deg); } }

@media (max-width: 1500px) {
  .hero-stats { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .hero-stat b { font-size: 18px; }
}
@media (max-width: 1320px) {
  .grid-top, .grid-mid, .grid-bottom { grid-template-columns: 1fr; }
  .hero-top { flex-wrap: wrap; }
  .hero-actions { max-width: none; justify-content: flex-start; }
  .hero-stats { grid-template-columns: repeat(3, 1fr); }
  .archive-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 900px) {
  .hero-identity { flex-direction: column; }
  .hero-stats { grid-template-columns: 1fr 1fr; }
  .risk-main { flex-direction: column; align-items: flex-start; }
  .trend-summary { grid-template-columns: 1fr 1fr; }
  .archive-grid { grid-template-columns: 1fr; }
  .archive-item.span-2 { grid-column: span 1; }
}
</style>