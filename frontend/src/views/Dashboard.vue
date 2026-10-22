<template>
  <div ref="pageRef" class="bigscreen">
    <div class="bg-grid"></div>
    <div class="bg-aurora aurora-a"></div>
    <div class="bg-aurora aurora-b"></div>

    <header class="screen-top">
      <div class="screen-brand">
        <span class="screen-logo">净</span>
        <div>
          <div class="screen-kicker">FIXED SOURCE INTELLIGENCE · REGULATORY COMMAND</div>
          <h1>固定污染源监管指挥大屏</h1>
        </div>
      </div>
      <div class="screen-meta">
        <span class="live-chip"><i></i> 实时同步</span>
        <span class="screen-date">{{ dateText }}</span>
        <strong class="screen-clock">{{ clockText }}</strong>
      </div>
      <div class="screen-tools">
        <button class="tool-btn" @click="load"><el-icon><Refresh /></el-icon>刷新</button>
        <button class="tool-btn" @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>{{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
        <button class="collect-btn" :disabled="collecting" @click="runCollect">
          <el-icon><VideoPlay /></el-icon>{{ collecting ? '采集中…' : '运行今日采集' }}
        </button>
      </div>
    </header>

    <div class="ticker">
      <span class="ticker-label"><i></i> LIVE</span>
      <div class="ticker-main">
        <span>{{ tickerText }}</span>
      </div>
      <span class="ticker-owner">值班 {{ auth.user?.name || '—' }} · {{ auth.user?.roleLabel || '监管人员' }}</span>
    </div>

    <div class="kpi-grid">
      <div v-for="kpi in kpis" :key="kpi.label" class="kpi" :class="`tone-${kpi.tone}`">
        <div class="kpi-icon"><el-icon><component :is="kpi.icon" /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">{{ kpi.label }}</div>
          <div class="kpi-value">{{ fmt(kpi.value) }}<small>{{ kpi.unit }}</small></div>
          <div class="kpi-hint">{{ kpi.hint }}</div>
        </div>
        <span class="kpi-index">{{ kpi.index }}</span>
      </div>
    </div>

    <div class="screen-body">
      <aside class="screen-col left-col">
        <section class="panel rank-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">REGIONAL RISK</span>
              <h2>区域风险排行</h2>
            </div>
            <span class="head-note">TOP 8 · 均分</span>
          </div>
          <div class="rank-list">
            <button
              v-for="(row, index) in regionRank"
              :key="row.region"
              class="rank-row"
              :class="{ active: selectedRegion === row.region }"
              @click="selectRegion(row.region)"
            >
              <span class="rank-no" :class="{ hot: index < 3 }">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="rank-main">
                <div class="rank-top">
                  <b>{{ row.region }}</b>
                  <span>{{ row.count }} 家 · 高风险 {{ row.highRisk || 0 }}</span>
                </div>
                <div class="rank-bar"><i :style="{ width: `${Math.min(100, Number(row.avgRisk || 0))}%` }"></i></div>
              </div>
              <strong class="rank-score">{{ row.avgRisk }}</strong>
            </button>
            <div v-if="!regionRank.length" class="empty-line">暂无区域风险数据</div>
          </div>
        </section>

        <section class="panel high-risk-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">KEY WATCHLIST</span>
              <h2>高风险企业</h2>
            </div>
            <router-link class="panel-link" to="/enterprises">全部 <el-icon><ArrowRight /></el-icon></router-link>
          </div>
          <div class="watch-list">
            <button
              v-for="(row, index) in watchList"
              :key="row.id"
              class="watch-row"
              @click="openProfile(row)"
            >
              <span class="watch-avatar" :style="{ background: riskMeta(row.riskLevel).color }">{{ row.name?.slice(0, 1) }}</span>
              <div class="watch-main">
                <b>{{ row.name }}</b>
                <span>{{ row.industry }} · {{ row.region }}</span>
              </div>
              <div class="watch-score">
                <strong :style="{ color: riskMeta(row.riskLevel).color }">{{ row.riskScore }}</strong>
                <small>{{ row.riskLevel }} 级</small>
              </div>
              <span class="watch-index">{{ String(index + 1).padStart(2, '0') }}</span>
            </button>
            <div v-if="!watchList.length" class="empty-line">暂无高风险企业</div>
          </div>
        </section>

        <section class="panel quality-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">DATA PIPELINE</span>
              <h2>采集与数据质量</h2>
            </div>
            <span class="status-tag" :class="{ warn: (latestBatch?.failedCount || 0) > 0 }">
              {{ latestBatch?.status === 'SUCCESS' ? '采集正常' : (latestBatch?.status || '待采集') }}
            </span>
          </div>
          <div class="batch-line">
            <div>
              <span>最新批次</span>
              <b class="mono">{{ latestBatch?.batchNo || '—' }}</b>
            </div>
            <div class="batch-rate">{{ batchRate }}<small>%</small></div>
          </div>
          <div class="quality-bar"><i :style="{ width: `${batchRate}%` }"></i></div>
          <div class="quality-meta">
            <span>{{ latestBatch?.finishedAt || '尚未完成采集' }}</span>
            <span>接收 {{ latestBatch?.receivedCount ?? 0 }} / 应到 {{ latestBatch?.expectedCount ?? 0 }}</span>
          </div>
          <div class="quality-grid">
            <div><span>今日入库</span><b>{{ fmt(overview.monitorRowsToday) }}</b><small>条</small></div>
            <div><span>开放质量问题</span><b class="amber">{{ overview.openIssues || 0 }}</b><small>个</small></div>
            <div><span>近 7 日问题</span><b>{{ overview.qualityIssues7d || 0 }}</b><small>条</small></div>
            <div><span>采集失败</span><b :class="{ red: (latestBatch?.failedCount || 0) > 0 }">{{ latestBatch?.failedCount || 0 }}</b><small>条</small></div>
          </div>
        </section>
      </aside>

      <main class="screen-col center-col">
        <section class="panel map-panel">
          <div class="panel-head map-head">
            <div class="panel-title">
              <span class="panel-kicker">SPATIAL SITUATION · BEIJING</span>
              <h2>固定污染源风险地图</h2>
            </div>
            <div class="map-head-tools">
              <div class="layer-switch">
                <button :class="{ active: layer === 'risk' }" @click="layer = 'risk'">风险热力</button>
                <button :class="{ active: layer === 'density' }" @click="layer = 'density'">企业密度</button>
                <button :class="{ active: layer === 'event' }" @click="layer = 'event'">事件分布</button>
              </div>
              <button class="map-reset" title="放大地图" @click="mapRef?.zoomIn()">+</button>
              <button class="map-reset" title="缩小地图" @click="mapRef?.zoomOut()">−</button>
              <button class="map-reset" title="复位地图" @click="resetMap">
                <el-icon><Refresh /></el-icon>
              </button>
            </div>
          </div>
          <div class="map-stage">
            <BeijingRiskMap
              ref="mapRef"
              :enterprises="mapEnterprises"
              :regions="regionStats"
              :layer="layer"
              :selected-region="selectedRegion"
              :selected-enterprise-id="selectedEnterpriseId"
              @select-region="selectRegion"
              @select-enterprise="selectEnterprise"
            />

            <div class="map-badge">
              <div><span>在管企业</span><b>{{ overview.activeEnterpriseCount || 0 }}</b></div>
              <div><span>监测点位</span><b>{{ overview.pointCount || 0 }}</b></div>
              <div><span>撒点企业</span><b>{{ mapEnterprises.length }}</b></div>
            </div>

            <div class="map-legend">
              <template v-if="layer === 'risk'">
                <span><i style="background:#22c55e"></i>A</span>
                <span><i style="background:#3b82f6"></i>B</span>
                <span><i style="background:#fbbf24"></i>C</span>
                <span><i style="background:#f87171"></i>D</span>
                <em>区面：风险均分 · 点位：企业风险</em>
              </template>
              <template v-else>
                <span>低</span><i class="legend-gradient" :class="layer"></i><span>高</span>
                <em>{{ layer === 'density' ? '区面：在管企业数' : '区面：未闭环事件数' }}</em>
              </template>
            </div>

            <div class="map-scale">
              拖动平移 · 滚轮缩放 · 点击区域或企业点位查看详情
            </div>

            <button v-if="missingCoordinates > 0" class="map-warning" @click="goEnterprises">
              有 {{ missingCoordinates }} 家在管企业尚未维护坐标，前往台账补全
              <el-icon><ArrowRight /></el-icon>
            </button>

            <div v-if="selectedEnterpriseInfo" class="map-inspector">
              <div class="insp-head">
                <span class="insp-level" :style="{ color: riskMeta(selectedEnterpriseInfo.riskLevel).color, background: riskMeta(selectedEnterpriseInfo.riskLevel).soft }">
                  {{ selectedEnterpriseInfo.riskLevel }} 级
                </span>
                <div class="insp-title">
                  <b>{{ selectedEnterpriseInfo.name }}</b>
                  <span>{{ selectedEnterpriseInfo.industry }} · {{ selectedEnterpriseInfo.region }}</span>
                </div>
                <button class="insp-close" @click="clearSelection">×</button>
              </div>
              <div class="insp-risk">
                <strong :style="{ color: riskMeta(selectedEnterpriseInfo.riskLevel).color }">{{ selectedEnterpriseInfo.riskScore }}</strong>
                <div>
                  <span>综合风险分</span>
                  <div class="insp-bar"><i :style="{ width: `${Math.min(100, selectedEnterpriseInfo.riskScore || 0)}%`, background: riskMeta(selectedEnterpriseInfo.riskLevel).color }"></i></div>
                </div>
              </div>
              <div class="insp-grid">
                <div><span>监测点位</span><b>{{ selectedEnterpriseInfo.pointCount ?? 0 }} 个</b></div>
                <div><span>待复核事件</span><b :class="{ hot: selectedEnterpriseInfo.pendingEvents > 0 }">{{ selectedEnterpriseInfo.pendingEvents ?? 0 }} 件</b></div>
                <div><span>生产状态</span><b>{{ productionMeta(selectedEnterpriseInfo.productionStatus).label }}</b></div>
                <div><span>所属区域</span><b>{{ selectedEnterpriseInfo.region }}</b></div>
              </div>
              <div class="insp-actions">
                <button class="insp-primary" @click="openProfile(selectedEnterpriseInfo)">进入企业画像</button>
                <button v-if="canMaintain" class="insp-ghost" @click="editEnterprise(selectedEnterpriseInfo)">维护台账</button>
              </div>
            </div>

            <div v-else-if="selectedRegionInfo" class="map-inspector region-insp">
              <div class="insp-head">
                <span class="insp-level region">{{ selectedRegionInfo.region }}</span>
                <div class="insp-title">
                  <b>区域监管态势</b>
                  <span>风险均分 {{ selectedRegionInfo.avgRisk }} · {{ selectedRegionInfo.count }} 家在管</span>
                </div>
                <button class="insp-close" @click="clearSelection">×</button>
              </div>
              <div class="insp-grid region-grid">
                <div><span>在管企业</span><b>{{ selectedRegionInfo.count }} 家</b></div>
                <div><span>高风险</span><b class="hot">{{ selectedRegionInfo.highRisk || 0 }} 家</b></div>
                <div><span>未闭环事件</span><b>{{ selectedRegionInfo.eventCount || 0 }} 件</b></div>
                <div><span>风险均分</span><b>{{ selectedRegionInfo.avgRisk }}</b></div>
              </div>
              <div class="region-enterprises">
                <div class="region-sub">区域内企业</div>
                <button v-for="item in regionEnterprises.slice(0, 4)" :key="item.id" @click="selectEnterprise(item)">
                  <i :style="{ background: riskMeta(item.riskLevel).color }"></i>
                  <span>{{ item.name }}</span>
                  <b>{{ item.riskScore }}</b>
                </button>
                <div v-if="!regionEnterprises.length" class="empty-line">暂无已定位企业</div>
              </div>
              <div class="insp-actions">
                <button class="insp-primary" @click="goRegionEnterprises(selectedRegionInfo.region)">查看区域企业</button>
                <button v-if="canMaintain" class="insp-ghost" @click="goEnterprises">维护台账</button>
              </div>
            </div>
          </div>
        </section>

        <div class="center-bottom">
          <section class="panel trend-panel">
            <div class="panel-head">
              <div class="panel-title">
                <span class="panel-kicker">7-DAY TREND</span>
                <h2>近 7 日事件趋势</h2>
              </div>
              <span class="head-note">总数 / 高风险</span>
            </div>
            <EChart :option="trendOption" height="116px" />
          </section>
          <section class="panel rule-panel">
            <div class="panel-head">
              <div class="panel-title">
                <span class="panel-kicker">RULE ENGINE</span>
                <h2>规则命中分布</h2>
              </div>
              <span class="head-note">全部线索</span>
            </div>
            <EChart :option="ruleOption" height="116px" />
          </section>
        </div>
      </main>

      <aside class="screen-col right-col">
        <section class="panel event-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">LIVE EVENT STREAM</span>
              <h2>实时异常事件</h2>
            </div>
            <span class="event-count">{{ overview.eventPending || 0 }} 件待复核</span>
          </div>
          <div class="event-feed">
            <button
              v-for="event in recentEvents.slice(0, 6)"
              :key="event.id"
              class="event-row"
              @click="openEvent(event)"
            >
              <span class="event-priority" :class="String(event.priority || '').toLowerCase()">{{ event.priority }}</span>
              <div class="event-main">
                <b>{{ event.title }}</b>
                <span>{{ event.enterpriseName }} · {{ event.pointName }}</span>
              </div>
              <div class="event-side">
                <em :class="`event-status s-${String(event.status || '').toLowerCase()}`">{{ statusMeta(event.status).label }}</em>
                <span>{{ String(event.startTime || '').slice(5) }}</span>
              </div>
            </button>
            <div v-if="!recentEvents.length" class="empty-line">暂无异常事件</div>
          </div>
          <router-link class="panel-foot-link" to="/events">进入事件工作台，查看证据链与 AI 引用 <el-icon><ArrowRight /></el-icon></router-link>
        </section>

        <section class="panel ai-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">HUMAN + AI</span>
              <h2>人机协同研判</h2>
            </div>
            <span class="status-tag">可追溯</span>
          </div>
          <div class="ai-rings">
            <div class="ring-block">
              <div class="ring" :style="ringStyle(aiCoverage, '#2fe3c8')"><strong>{{ aiCoverage }}<small>%</small></strong></div>
              <span>AI 研判覆盖</span>
            </div>
            <div class="ring-block">
              <div class="ring amber" :style="ringStyle(closureRate, '#fbbf24')"><strong>{{ closureRate }}<small>%</small></strong></div>
              <span>任务闭环率</span>
            </div>
          </div>
          <div class="ai-stats">
            <div><span>已研判</span><b>{{ overview.aiDone || 0 }}</b></div>
            <div><span>无依据拒答</span><b class="amber">{{ overview.aiRefusal || 0 }}</b></div>
            <div><span>待处理线索</span><b>{{ overview.cluePending || 0 }}</b></div>
          </div>
          <div class="ai-note">结论必须落到法规条款引用；无依据时明确拒答，须人工复核后才能下发。</div>
        </section>

        <section class="panel task-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="panel-kicker">FIELD CLOSURE</span>
              <h2>核查任务态势</h2>
            </div>
            <router-link class="panel-link" to="/tasks">全部 <el-icon><ArrowRight /></el-icon></router-link>
          </div>
          <div class="task-list">
            <button v-for="task in openTasks" :key="task.id" class="task-row" @click="router.push('/tasks')">
              <span class="task-state" :class="String(task.status || '').toLowerCase()">{{ statusMeta(task.status).label }}</span>
              <div class="task-main">
                <b>{{ task.title }}</b>
                <span>{{ task.enterpriseName }} · 截止 {{ String(task.dueAt || '').slice(5) }}</span>
              </div>
              <span v-if="task.slaStatus === 'OVERDUE'" class="task-overdue">逾期</span>
              <span v-else class="task-urge">督办 {{ task.urgeCount || 0 }}</span>
            </button>
            <div v-if="!openTasks.length" class="empty-line">暂无进行中的核查任务</div>
          </div>
        </section>
      </aside>
    </div>

    <footer class="screen-footer">
      <span><i></i> 数据链路：模拟数据源 → 每日采集 → 规则引擎 → 事件聚合 → AI 研判 → 任务闭环</span>
      <span>最近批次 {{ latestBatch?.batchNo || '—' }} · {{ latestBatch?.finishedAt || '—' }}</span>
      <span>数据仅用于毕业设计演示，不接入真实企业生产数据</span>
    </footer>

    <div v-if="loading && !overview.enterpriseCount" class="screen-loading">
      <span class="scan-line"></span>
      <b>正在汇聚监管数据…</b>
      <small>采集批次 · 规则线索 · 事件风险 · 企业画像</small>
    </div>

    <div v-if="loadError && !overview.enterpriseCount" class="screen-loading error">
      <b>大屏数据加载失败</b>
      <small>{{ loadError }}</small>
      <button class="tool-btn" @click="load">重新加载</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight, CircleCheck, Connection, DataLine, Download, FullScreen,
  OfficeBuilding, Refresh, VideoPlay, Warning
} from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'
import BeijingRiskMap from '../components/BeijingRiskMap.vue'
import { statusMeta } from '../utils/labels'
import { productionMeta } from '../utils/dicts'
import { auth, hasRole } from '../store'

const router = useRouter()
const pageRef = ref(null)
const mapRef = ref(null)
const overview = ref({})
const loading = ref(false)
const collecting = ref(false)
const loadError = ref('')
const layer = ref('risk')
const selectedRegion = ref('')
const selectedEnterpriseId = ref('')
const isFullscreen = ref(false)
const now = ref(new Date())
let clockTimer = null
let refreshTimer = null

const canMaintain = computed(() => hasRole('reviewer', 'admin'))
const latestBatch = computed(() => overview.value.latestBatch || null)
const mapEnterprises = computed(() => overview.value.mapEnterprises || [])
const regionStats = computed(() => overview.value.regionStats || [])
const recentEvents = computed(() => overview.value.recentEvents || [])
const openTasks = computed(() => overview.value.openTasks || [])
const riskRanking = computed(() => overview.value.riskRanking || [])

const regionRank = computed(() =>
  regionStats.value
    .slice()
    .sort((a, b) => Number(b.avgRisk || 0) - Number(a.avgRisk || 0))
    .slice(0, 8)
)

const watchList = computed(() =>
  riskRanking.value.filter((item) => ['C', 'D'].includes(item.riskLevel)).slice(0, 4)
)

const p1Count = computed(() => {
  const row = (overview.value.eventPriorityStats || []).find((item) => item.priority === 'P1')
  return Number(row?.count || 0)
})

const aiCoverage = computed(() => {
  const total = Number(overview.value.totalClues || 0)
  if (!total) return 0
  return Math.round((Number(overview.value.aiDone || 0) / total) * 100)
})

const closureRate = computed(() => {
  const value = Number(overview.value.closureRate || 0)
  return Math.round(value * 10) / 10
})

const batchRate = computed(() => {
  const expected = Number(latestBatch.value?.expectedCount || 0)
  const received = Number(latestBatch.value?.receivedCount || 0)
  if (!expected) return 0
  return Math.min(100, Math.round((received * 1000) / expected) / 10)
})

const selectedEnterpriseInfo = computed(() => {
  const id = String(selectedEnterpriseId.value || '')
  if (!id) return null
  return mapEnterprises.value.find((item) => String(item.id) === id)
    || riskRanking.value.find((item) => String(item.id) === id)
    || null
})

const selectedRegionInfo = computed(() => {
  if (!selectedRegion.value) return null
  return regionStats.value.find((item) => item.region === selectedRegion.value) || {
    region: selectedRegion.value,
    count: 0,
    highRisk: 0,
    eventCount: 0,
    avgRisk: 0
  }
})

const regionEnterprises = computed(() =>
  mapEnterprises.value
    .filter((item) => item.region === selectedRegion.value)
    .sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0))
)

const missingCoordinates = computed(() =>
  Math.max(0, Number(overview.value.activeEnterpriseCount || 0) - mapEnterprises.value.length)
)

const tickerText = computed(() => {
  if (!latestBatch.value) return '监管数据正在汇聚，等待首轮采集批次…'
  const top = riskRanking.value[0]
  return `最新批次 ${latestBatch.value.batchNo}，接收 ${latestBatch.value.receivedCount || 0} 条数据；当前待复核事件 ${overview.value.eventPending || 0} 件，其中 P1 ${p1Count.value} 件；风险最高对象 ${top ? `${top.name}（${top.riskScore} 分）` : '待计算'}`
})

const dateText = computed(() => {
  const d = now.value
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${String(d.getMonth() + 1).padStart(2, '0')} 月 ${String(d.getDate()).padStart(2, '0')} 日 · 星期${week}`
})

const clockText = computed(() => {
  const d = now.value
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
})

const kpis = computed(() => [
  {
    label: '在管企业',
    value: overview.value.activeEnterpriseCount || 0,
    unit: '家',
    hint: `监测点位 ${overview.value.pointCount || 0} 个`,
    icon: OfficeBuilding,
    tone: 'teal',
    index: '01'
  },
  {
    label: '高风险企业',
    value: overview.value.highRiskCount || 0,
    unit: '家',
    hint: 'C / D 级重点监管',
    icon: Warning,
    tone: 'red',
    index: '02'
  },
  {
    label: '待复核事件',
    value: overview.value.eventPending || 0,
    unit: '件',
    hint: `P1 优先 ${p1Count.value} 件`,
    icon: DataLine,
    tone: 'amber',
    index: '03'
  },
  {
    label: '待处理线索',
    value: overview.value.cluePending || 0,
    unit: '条',
    hint: `AI 已研判 ${overview.value.aiDone || 0} 条`,
    icon: Connection,
    tone: 'blue',
    index: '04'
  },
  {
    label: '任务闭环率',
    value: closureRate.value,
    unit: '%',
    hint: `已关闭 ${overview.value.taskClosed || 0} / ${(overview.value.taskClosed || 0) + (overview.value.taskOpen || 0)} 件`,
    icon: CircleCheck,
    tone: 'teal',
    index: '05'
  },
  {
    label: '今日入库数据',
    value: overview.value.monitorRowsToday || 0,
    unit: '条',
    hint: `开放质量问题 ${overview.value.openIssues || 0} 个`,
    icon: Download,
    tone: 'blue',
    index: '06'
  }
])

const trendOption = computed(() => {
  const data = overview.value.eventTrend || []
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(6,23,31,0.96)',
      borderColor: 'rgba(64,160,165,0.55)',
      textStyle: { color: '#dcf7f4', fontSize: 11 }
    },
    legend: { right: 0, top: 0, itemWidth: 9, itemHeight: 9, textStyle: { color: '#7e9fa5', fontSize: 10 } },
    grid: { left: 36, right: 12, top: 28, bottom: 22 },
    xAxis: {
      type: 'category',
      data: data.map((item) => String(item.bizDay).slice(5)),
      axisLine: { lineStyle: { color: 'rgba(96,150,156,0.35)' } },
      axisTick: { show: false },
      axisLabel: { color: '#74949a', fontSize: 9 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(96,150,156,0.15)', type: 'dashed' } },
      axisLabel: { color: '#74949a', fontSize: 9 }
    },
    series: [
      {
        name: '事件总数',
        type: 'bar',
        barWidth: 13,
        data: data.map((item) => item.total),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(47,227,200,0.9)' }, { offset: 1, color: 'rgba(47,227,200,0.16)' }]
          }
        }
      },
      {
        name: '高风险',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: data.map((item) => item.highCount),
        lineStyle: { width: 2, color: '#fbbf24' },
        itemStyle: { color: '#fbbf24' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(251,191,36,0.20)' }, { offset: 1, color: 'rgba(251,191,36,0)' }]
          }
        }
      }
    ]
  }
})

const ruleOption = computed(() => {
  const data = (overview.value.ruleStats || []).slice(0, 6).reverse()
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(6,23,31,0.96)',
      borderColor: 'rgba(64,160,165,0.55)',
      textStyle: { color: '#dcf7f4', fontSize: 11 }
    },
    grid: { left: 8, right: 28, top: 8, bottom: 6, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(96,150,156,0.15)', type: 'dashed' } },
      axisLabel: { color: '#74949a', fontSize: 9 }
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => item.ruleName),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#9db8bc', fontSize: 10 }
    },
    series: [{
      type: 'bar',
      barWidth: 9,
      data: data.map((item) => item.count),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [{ offset: 0, color: 'rgba(47,227,200,0.22)' }, { offset: 1, color: '#2fe3c8' }]
        }
      },
      label: { show: true, position: 'right', color: '#8fb5b9', fontSize: 9 }
    }]
  }
})

function fmt(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function riskMeta(level) {
  return {
    A: { color: '#34d399', soft: 'rgba(52,211,153,0.16)' },
    B: { color: '#60a5fa', soft: 'rgba(96,165,250,0.16)' },
    C: { color: '#fbbf24', soft: 'rgba(251,191,36,0.16)' },
    D: { color: '#f87171', soft: 'rgba(248,113,113,0.16)' }
  }[level] || { color: '#94a3b8', soft: 'rgba(148,163,184,0.14)' }
}

function ringStyle(percent, color) {
  const safe = Math.max(0, Math.min(100, Number(percent || 0)))
  return { background: `conic-gradient(${color} ${safe}%, rgba(255,255,255,0.07) ${safe}% 100%)` }
}

function selectRegion(name) {
  selectedRegion.value = name
  selectedEnterpriseId.value = ''
}

function selectEnterprise(item) {
  selectedRegion.value = item.region || selectedRegion.value
  selectedEnterpriseId.value = item.id
}

function clearSelection() {
  selectedRegion.value = ''
  selectedEnterpriseId.value = ''
}

function resetMap() {
  mapRef.value?.reset()
  clearSelection()
}

function openProfile(row) {
  if (row?.id) router.push(`/enterprises/${row.id}`)
}

function editEnterprise(row) {
  if (!row?.id) return
  if (!canMaintain.value) {
    ElMessage.warning('当前角色只读，请使用审核人员或管理员账号维护台账')
    return
  }
  router.push(`/enterprises/${row.id}?edit=1`)
}

function openEvent(event) {
  router.push({ path: '/events', query: { id: event.id } })
}

function goEnterprises() {
  router.push('/enterprises')
}

function goRegionEnterprises(region) {
  router.push({ path: '/enterprises', query: { region } })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/dashboard/overview')
    overview.value = data
    if (selectedEnterpriseId.value) {
      const exists = [...(data.mapEnterprises || []), ...(data.riskRanking || [])]
        .some((item) => String(item.id) === String(selectedEnterpriseId.value))
      if (!exists) selectedEnterpriseId.value = ''
    }
    if (!selectedEnterpriseId.value && !selectedRegion.value && (data.riskRanking || []).length) {
      const first = (data.riskRanking || [])[0]
      selectedEnterpriseId.value = first.id
    }
  } catch (error) {
    loadError.value = error?.response?.data?.message || error?.message || '请求失败'
  } finally {
    loading.value = false
  }
}

async function runCollect() {
  collecting.value = true
  try {
    const d = new Date()
    const bizDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const { data } = await api.post('/collect-batches', { bizDate })
    ElMessage.success(`采集完成：接收 ${data.receivedCount} 条，新增线索 ${data.clues ?? 0} 条`)
    await load()
  } finally {
    collecting.value = false
  }
}

async function toggleFullscreen() {
  try {
    if (!document.fullscreenElement) {
      await pageRef.value?.requestFullscreen()
    } else {
      await document.exitFullscreen()
    }
  } catch {
    ElMessage.warning('当前浏览器不支持全屏，可使用 F11')
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

function tickClock() {
  now.value = new Date()
}

onMounted(() => {
  load()
  clockTimer = window.setInterval(tickClock, 1000)
  refreshTimer = window.setInterval(load, 60000)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.clearInterval(clockTimer)
  window.clearInterval(refreshTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.bigscreen {
  --screen-bg: #04121b;
  --panel-bg: linear-gradient(180deg, rgba(8, 31, 41, 0.94), rgba(5, 23, 32, 0.9));
  --panel-line: rgba(56, 119, 129, 0.34);
  --screen-text: #d9eef0;
  --screen-muted: #7e9fa5;
  --screen-teal: #2fe3c8;
  --screen-amber: #fbbf24;
  --screen-red: #f87171;
  position: relative;
  min-height: calc(100vh - 78px);
  padding: 12px 22px 12px;
  color: var(--screen-text);
  background:
    radial-gradient(circle at 50% 0%, rgba(18, 78, 81, 0.18), transparent 44%),
    linear-gradient(180deg, #061923 0%, #04121b 46%, #030d14 100%);
  overflow: hidden;
}

:global(.screen-shell .content) {
  padding: 0 !important;
  max-width: none !important;
  background: #04121b;
}

.bigscreen:fullscreen {
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  overflow: auto;
  padding: 12px 22px 12px;
}
.bigscreen:fullscreen::backdrop {
  background: #04121b;
}

.bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.48;
  background-image:
    linear-gradient(rgba(47, 227, 200, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(47, 227, 200, 0.035) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at 50% 40%, #000 0%, transparent 82%);
}

.bg-aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.aurora-a {
  width: 480px;
  height: 480px;
  right: -140px;
  top: 120px;
  background: rgba(15, 158, 144, 0.14);
}
.aurora-b {
  width: 360px;
  height: 360px;
  left: 34%;
  top: 300px;
  background: rgba(38, 99, 140, 0.11);
}

.screen-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 2px 2px 10px;
  border-bottom: 1px solid rgba(56, 119, 129, 0.24);
}
.screen-brand {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 400px;
}
.screen-logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2fe3c8, #0a7a71);
  color: #031418;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  box-shadow: 0 0 28px rgba(47, 227, 200, 0.28);
}
.screen-kicker {
  font-size: 8px;
  letter-spacing: 2px;
  color: rgba(123, 176, 181, 0.7);
  font-family: "Cascadia Code", Consolas, monospace;
}
.screen-brand h1 {
  margin: 4px 0 0;
  font-size: 22px;
  letter-spacing: 1px;
  font-weight: 700;
  background: linear-gradient(90deg, #f2ffff, #9fe8e2);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.screen-meta {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--screen-muted);
  font-size: 11px;
}
.live-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border: 1px solid rgba(47, 227, 200, 0.25);
  background: rgba(47, 227, 200, 0.07);
  border-radius: 999px;
  color: #8cebDF;
  font-size: 10px;
}
.live-chip i,
.screen-footer i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2fe3c8;
  box-shadow: 0 0 0 4px rgba(47, 227, 200, 0.12), 0 0 14px rgba(47, 227, 200, 0.8);
  animation: blink 1.8s ease-in-out infinite;
}
.screen-date { letter-spacing: 0.4px; }
.screen-clock {
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 22px;
  letter-spacing: 2px;
  color: #edfffd;
  text-shadow: 0 0 20px rgba(47, 227, 200, 0.28);
}
.screen-tools { display: flex; align-items: center; gap: 8px; }
.tool-btn,
.collect-btn {
  height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.tool-btn {
  border: 1px solid rgba(64, 129, 137, 0.36);
  background: rgba(13, 43, 53, 0.72);
  color: #a7c8cb;
}
.tool-btn:hover { border-color: rgba(47, 227, 200, 0.48); color: #dffffa; }
.collect-btn {
  border: 1px solid rgba(47, 227, 200, 0.4);
  background: linear-gradient(135deg, rgba(47, 227, 200, 0.92), #0f9e90);
  color: #031418;
  font-weight: 700;
}
.collect-btn:hover { box-shadow: 0 0 20px rgba(47, 227, 200, 0.24); }
.collect-btn:disabled { opacity: 0.6; cursor: wait; }

.ticker {
  position: relative;
  z-index: 2;
  margin-top: 9px;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 13px;
  border: 1px solid rgba(56, 119, 129, 0.24);
  border-radius: 8px;
  background: linear-gradient(90deg, rgba(12, 45, 53, 0.84), rgba(6, 27, 37, 0.54));
  font-size: 10px;
  color: #90afb4;
  overflow: hidden;
}
.ticker-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 12px;
  border-right: 1px solid rgba(56, 119, 129, 0.32);
  color: #4fe6d3;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 9px;
  letter-spacing: 1px;
  flex-shrink: 0;
}
.ticker-label i { width: 5px; height: 5px; border-radius: 50%; background: #f87171; box-shadow: 0 0 8px #f87171; }
.ticker-main { flex: 1; min-width: 0; }
.ticker-main span { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ticker-owner { flex-shrink: 0; color: #6e9399; }

.kpi-grid {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 9px;
  margin-top: 10px;
}
.kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 80px;
  padding: 10px 14px;
  border: 1px solid var(--panel-line);
  border-radius: 10px;
  background:
    linear-gradient(135deg, rgba(18, 57, 67, 0.58), rgba(7, 27, 37, 0.88));
  overflow: hidden;
}
.kpi::after {
  content: "";
  position: absolute;
  width: 92px;
  height: 92px;
  right: -38px;
  top: -44px;
  border-radius: 50%;
  border: 1px solid currentColor;
  opacity: 0.12;
}
.kpi-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: rgba(47, 227, 200, 0.08);
  border: 1px solid rgba(47, 227, 200, 0.22);
  color: var(--screen-teal);
  flex-shrink: 0;
}
.kpi-body { min-width: 0; }
.kpi-label { font-size: 10px; color: var(--screen-muted); letter-spacing: 0.5px; }
.kpi-value {
  margin-top: 2px;
  font-size: 23px;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
  color: #eefffd;
  text-shadow: 0 0 18px rgba(47, 227, 200, 0.16);
}
.kpi-value small { margin-left: 3px; font-size: 10px; font-weight: 400; color: var(--screen-muted); }
.kpi-hint { margin-top: 2px; font-size: 9px; color: #668a90; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.kpi-index {
  position: absolute;
  right: 9px;
  bottom: 5px;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  color: rgba(103, 150, 155, 0.4);
}
.tone-red .kpi-icon { color: #f87171; background: rgba(248,113,113,0.08); border-color: rgba(248,113,113,0.24); }
.tone-red .kpi-value { text-shadow: 0 0 18px rgba(248,113,113,0.18); }
.tone-amber .kpi-icon { color: #fbbf24; background: rgba(251,191,36,0.08); border-color: rgba(251,191,36,0.24); }
.tone-blue .kpi-icon { color: #60a5fa; background: rgba(96,165,250,0.08); border-color: rgba(96,165,250,0.24); }

.screen-body {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: 292px minmax(460px, 1fr) 326px;
  gap: 12px;
  margin-top: 10px;
  align-items: stretch;
}
.screen-col { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.center-col { min-width: 0; }

.panel {
  position: relative;
  border: 1px solid var(--panel-line);
  border-radius: 11px;
  background: var(--panel-bg);
  box-shadow: inset 0 1px 0 rgba(122, 205, 205, 0.045), 0 12px 34px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.panel::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  width: 42px;
  height: 2px;
  background: linear-gradient(90deg, #2fe3c8, transparent);
  opacity: 0.8;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 15px 10px;
}
.panel-title { min-width: 0; }
.panel-kicker {
  display: block;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 7px;
  letter-spacing: 1.5px;
  color: rgba(74, 143, 149, 0.8);
}
.panel-title h2 {
  margin: 3px 0 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: #d9f3f3;
}
.head-note { font-size: 9px; color: #688c92; }
.panel-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 9px;
  color: #4fc8bd;
}
.panel-foot-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 9px;
  border-top: 1px solid rgba(56, 119, 129, 0.2);
  color: #5fbeb6;
  font-size: 9px;
}
.empty-line { padding: 16px 14px; text-align: center; color: #5d7f85; font-size: 10px; }

.rank-panel { flex: 1.5; min-height: 304px; }
.rank-list { padding: 0 9px 10px; overflow: auto; }
.rank-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease;
}
.rank-row:hover,
.rank-row.active { background: rgba(47, 227, 200, 0.075); }
.rank-no {
  width: 21px;
  height: 21px;
  border-radius: 5px;
  background: rgba(63, 108, 116, 0.22);
  color: #6f9499;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 8px;
  font-family: "Cascadia Code", Consolas, monospace;
  flex-shrink: 0;
}
.rank-no.hot { background: rgba(47, 227, 200, 0.13); color: #4fe6d3; }
.rank-main { flex: 1; min-width: 0; }
.rank-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rank-top b { font-size: 10px; color: #c9e5e7; font-weight: 500; white-space: nowrap; }
.rank-top span { font-size: 8px; color: #62838a; white-space: nowrap; }
.rank-bar {
  height: 3px;
  margin-top: 5px;
  border-radius: 3px;
  background: rgba(68, 116, 122, 0.18);
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(47, 227, 200, 0.32), #2fe3c8);
  box-shadow: 0 0 8px rgba(47, 227, 200, 0.38);
}
.rank-score { width: 28px; text-align: right; font-size: 12px; color: #e8fffd; font-weight: 600; }

.high-risk-panel { flex: 0.62; min-height: 122px; }
.watch-list { padding: 0 9px 9px; }
.watch-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.watch-row:hover { background: rgba(47, 227, 200, 0.06); }
.watch-avatar {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  color: #04131a;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  opacity: 0.9;
}
.watch-main { flex: 1; min-width: 0; }
.watch-main b { display: block; font-size: 10px; font-weight: 500; color: #cbe5e7; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.watch-main span { display: block; margin-top: 2px; font-size: 8px; color: #66888e; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.watch-score { text-align: right; }
.watch-score strong { display: block; font-size: 13px; line-height: 1; }
.watch-score small { font-size: 8px; color: #6d8d92; }
.watch-index { font-size: 7px; color: rgba(109, 141, 146, 0.4); font-family: "Cascadia Code", Consolas, monospace; }

.quality-panel { flex: 0.94; min-height: 178px; }
.status-tag {
  padding: 3px 7px;
  border: 1px solid rgba(47, 227, 200, 0.24);
  border-radius: 999px;
  color: #63dcca;
  font-size: 8px;
  background: rgba(47, 227, 200, 0.06);
}
.status-tag.warn { color: #fbbf24; border-color: rgba(251,191,36,0.25); background: rgba(251,191,36,0.06); }
.batch-line { display: flex; justify-content: space-between; align-items: flex-end; padding: 2px 15px 0; }
.batch-line span { display: block; font-size: 8px; color: #67888e; }
.batch-line b { display: block; margin-top: 3px; font-size: 9px; font-weight: 500; color: #a8ced0; letter-spacing: 0.2px; }
.batch-rate { font-size: 22px; font-weight: 700; color: #2fe3c8; line-height: 1; }
.batch-rate small { font-size: 9px; color: #6f9498; margin-left: 2px; }
.quality-bar { height: 4px; margin: 9px 15px 0; border-radius: 4px; background: rgba(67, 112, 118, 0.2); overflow: hidden; }
.quality-bar i { display: block; height: 100%; background: linear-gradient(90deg, rgba(47,227,200,0.3), #2fe3c8); border-radius: 4px; box-shadow: 0 0 10px rgba(47,227,200,0.35); }
.quality-meta { display: flex; justify-content: space-between; padding: 5px 15px 0; font-size: 8px; color: #5f8188; }
.quality-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1px 12px; margin: 9px 15px 12px; padding-top: 8px; border-top: 1px solid rgba(56,119,129,0.18); }
.quality-grid div { display: flex; align-items: baseline; gap: 4px; font-size: 8px; color: #688a90; }
.quality-grid span { flex: 1; }
.quality-grid b { color: #d2ebec; font-size: 12px; font-weight: 600; }
.quality-grid small { color: #5f8188; font-size: 8px; }
.amber { color: #fbbf24 !important; }
.red { color: #f87171 !important; }

.map-panel { flex: 1; min-height: 452px; display: flex; flex-direction: column; }
.map-head { padding-bottom: 8px; }
.map-head-tools { display: flex; align-items: center; gap: 7px; }
.layer-switch { display: flex; padding: 2px; border: 1px solid rgba(56, 119, 129, 0.3); border-radius: 7px; background: rgba(6, 27, 37, 0.6); }
.layer-switch button {
  border: 0;
  background: transparent;
  color: #789ba0;
  border-radius: 5px;
  padding: 5px 9px;
  font-size: 9px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.layer-switch button.active { background: rgba(47, 227, 200, 0.14); color: #6ff0e1; }
.map-reset {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  border: 1px solid rgba(56, 119, 129, 0.3);
  background: rgba(6, 27, 37, 0.6);
  color: #789ba0;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.map-stage {
  position: relative;
  flex: 1;
  min-height: 358px;
  margin: 0 8px 8px;
  border: 1px solid rgba(56, 119, 129, 0.16);
  border-radius: 8px;
  background:
    radial-gradient(circle at 50% 46%, rgba(28, 117, 113, 0.14), transparent 52%),
    repeating-linear-gradient(0deg, transparent 0 39px, rgba(47, 227, 200, 0.026) 39px 40px),
    repeating-linear-gradient(90deg, transparent 0 39px, rgba(47, 227, 200, 0.026) 39px 40px);
  overflow: hidden;
}
.map-stage::before,
.map-stage::after {
  content: "";
  position: absolute;
  width: 84px;
  height: 84px;
  border-color: rgba(47, 227, 200, 0.18);
  pointer-events: none;
}
.map-stage::before { left: 10px; top: 10px; border-left: 1px solid; border-top: 1px solid; }
.map-stage::after { right: 10px; bottom: 10px; border-right: 1px solid; border-bottom: 1px solid; }

.map-badge {
  position: absolute;
  left: 16px;
  top: 14px;
  display: flex;
  gap: 18px;
  padding: 8px 12px;
  border: 1px solid rgba(56, 119, 129, 0.26);
  border-radius: 8px;
  background: rgba(5, 23, 32, 0.76);
  backdrop-filter: blur(8px);
  pointer-events: none;
}
.map-badge div { text-align: left; }
.map-badge span { display: block; font-size: 8px; color: #6c8f95; }
.map-badge b { display: block; font-size: 14px; color: #e8fffc; font-family: "Cascadia Code", Consolas, monospace; }
.map-legend {
  position: absolute;
  left: 16px;
  bottom: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border: 1px solid rgba(56, 119, 129, 0.22);
  border-radius: 7px;
  background: rgba(5, 23, 32, 0.74);
  font-size: 8px;
  color: #8aabb0;
  backdrop-filter: blur(8px);
}
.map-legend span { display: inline-flex; align-items: center; gap: 4px; }
.map-legend i { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.map-legend em { margin-left: 5px; font-style: normal; color: #5f8086; }
.legend-gradient { width: 58px !important; height: 5px !important; border-radius: 4px !important; }
.legend-gradient.density { background: linear-gradient(90deg, #0b2731, #0f9e96, #5eead4); }
.legend-gradient.event { background: linear-gradient(90deg, #0b2731, #d99a2b, #ef4444); }
.map-scale {
  position: absolute;
  right: 16px;
  bottom: 15px;
  font-size: 8px;
  color: #597b81;
  pointer-events: none;
}
.map-warning {
  position: absolute;
  left: 50%;
  bottom: 45px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid rgba(251, 191, 36, 0.28);
  border-radius: 999px;
  padding: 5px 11px;
  background: rgba(94, 66, 18, 0.45);
  color: #f3cd7a;
  font-size: 8px;
  cursor: pointer;
  backdrop-filter: blur(6px);
}

.map-inspector {
  position: absolute;
  right: 14px;
  top: 14px;
  width: 272px;
  border: 1px solid rgba(47, 227, 200, 0.3);
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(8, 34, 43, 0.96), rgba(4, 24, 32, 0.95));
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 38px rgba(0, 0, 0, 0.28);
  padding: 12px;
  animation: inspector-in 0.24s ease both;
}
.insp-head { display: flex; align-items: flex-start; gap: 8px; }
.insp-level {
  padding: 3px 6px;
  border-radius: 5px;
  font-size: 8px;
  font-weight: 700;
  flex-shrink: 0;
}
.insp-level.region { color: #2fe3c8; background: rgba(47, 227, 200, 0.12); }
.insp-title { flex: 1; min-width: 0; }
.insp-title b { display: block; font-size: 12px; color: #efffff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.insp-title span { display: block; margin-top: 3px; font-size: 8px; color: #7e9fa4; }
.insp-close {
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 5px;
  background: rgba(255,255,255,0.06);
  color: #87a7ab;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
}
.insp-risk { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.insp-risk > strong { font-size: 30px; line-height: 1; letter-spacing: -1px; }
.insp-risk > div { flex: 1; }
.insp-risk span { font-size: 8px; color: #6d8f95; }
.insp-bar { height: 4px; margin-top: 6px; background: rgba(68,116,122,0.2); border-radius: 3px; overflow: hidden; }
.insp-bar i { display: block; height: 100%; border-radius: 3px; }
.insp-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1px 10px; margin-top: 11px; padding-top: 9px; border-top: 1px solid rgba(56,119,129,0.2); }
.insp-grid div { display: flex; justify-content: space-between; align-items: baseline; padding: 4px 0; font-size: 8px; color: #6c8e94; }
.insp-grid b { font-size: 10px; font-weight: 500; color: #cbe6e8; }
.insp-grid b.hot { color: #fbbf24; }
.insp-actions { display: flex; gap: 6px; margin-top: 10px; }
.insp-actions button {
  flex: 1;
  height: 28px;
  border-radius: 6px;
  font-size: 9px;
  cursor: pointer;
}
.insp-primary { border: 1px solid rgba(47, 227, 200, 0.35); background: rgba(47, 227, 200, 0.12); color: #6ff0e1; }
.insp-ghost { border: 1px solid rgba(56,119,129,0.28); background: transparent; color: #8aa9ad; }
.region-enterprises { margin-top: 10px; padding-top: 8px; border-top: 1px solid rgba(56,119,129,0.2); }
.region-sub { margin-bottom: 3px; font-size: 8px; color: #6d8f95; }
.region-enterprises button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: #b6d7d9;
  font-size: 9px;
  text-align: left;
  cursor: pointer;
}
.region-enterprises button:hover { color: #6ff0e1; }
.region-enterprises i { width: 5px; height: 5px; border-radius: 50%; flex-shrink: 0; }
.region-enterprises span { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.region-enterprises b { color: #d8f3f3; font-size: 9px; }

.center-bottom { display: grid; grid-template-columns: 1.35fr 1fr; gap: 12px; height: 180px; }
.trend-panel, .rule-panel { height: 180px; display: flex; flex-direction: column; }
.trend-panel :deep(.echart), .rule-panel :deep(.echart) { flex: 1; min-height: 0 !important; }
.trend-panel .panel-head, .rule-panel .panel-head { padding: 10px 15px 0; }

.event-panel { flex: 1.38; min-height: 276px; display: flex; flex-direction: column; }
.event-count { font-size: 8px; color: #fbbf24; }
.event-feed { flex: 1; padding: 0 8px; overflow: auto; }
.event-feed::-webkit-scrollbar { width: 3px; }
.event-feed::-webkit-scrollbar-thumb { background: rgba(79, 144, 150, 0.28); border-radius: 3px; }
.event-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 7px;
  border: 0;
  border-bottom: 1px solid rgba(56, 119, 129, 0.15);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease;
}
.event-row:hover { background: rgba(47, 227, 200, 0.055); }
.event-priority {
  width: 25px;
  height: 25px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  font-weight: 700;
  flex-shrink: 0;
  background: rgba(100, 116, 139, 0.16);
  color: #94a3b8;
}
.event-priority.p1 { background: rgba(248, 113, 113, 0.14); color: #fca5a5; }
.event-priority.p2 { background: rgba(251, 191, 36, 0.12); color: #fcd34d; }
.event-priority.p3 { background: rgba(96, 165, 250, 0.12); color: #93c5fd; }
.event-main { flex: 1; min-width: 0; }
.event-main b { display: block; font-size: 9.5px; font-weight: 500; color: #cfe8ea; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.event-main span { display: block; margin-top: 3px; font-size: 8px; color: #66888e; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.event-side { text-align: right; flex-shrink: 0; }
.event-side em { display: block; font-style: normal; font-size: 8px; color: #6cc8bc; }
.event-side em.s-feedback { color: #93c5fd; }
.event-side em.s-closed { color: #789ba0; }
.event-side span { display: block; margin-top: 3px; font-size: 7px; color: #5f7f85; }

.ai-panel { flex: 0.95; min-height: 172px; }
.ai-rings { display: flex; justify-content: center; gap: 32px; padding-top: 2px; }
.ring-block { display: flex; flex-direction: column; align-items: center; gap: 5px; }
.ring-block > span { font-size: 8px; color: #6c8f95; }
.ring {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  padding: 6px;
  box-shadow: 0 0 20px rgba(47, 227, 200, 0.08);
}
.ring::before { content: none; }
.ring > strong {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #071c25;
  color: #eafffd;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 16px;
}
.ring small { font-size: 7px; color: #7e9fa4; margin-left: 1px; }
.ai-stats { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 4px; margin: 12px 14px 0; padding-top: 9px; border-top: 1px solid rgba(56,119,129,0.18); }
.ai-stats div { text-align: center; }
.ai-stats span { display: block; font-size: 8px; color: #6a8b91; }
.ai-stats b { display: block; margin-top: 2px; font-size: 13px; color: #d7eeee; font-weight: 600; }
.ai-note { margin: 9px 14px 12px; padding: 6px 8px; border-radius: 5px; background: rgba(47, 227, 200, 0.045); color: #5f8c8f; font-size: 8px; line-height: 1.55; }

.task-panel { flex: 1; min-height: 158px; }
.task-list { padding: 0 9px 9px; }
.task-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px;
  border: 0;
  border-bottom: 1px solid rgba(56, 119, 129, 0.14);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.task-row:hover { background: rgba(47, 227, 200, 0.05); }
.task-state {
  padding: 3px 5px;
  border-radius: 4px;
  background: rgba(47, 227, 200, 0.09);
  color: #65dccc;
  font-size: 7px;
  flex-shrink: 0;
}
.task-state.dispatched { background: rgba(251,191,36,0.09); color: #f5cd72; }
.task-state.feedback { background: rgba(96,165,250,0.1); color: #93c5fd; }
.task-main { flex: 1; min-width: 0; }
.task-main b { display: block; font-size: 9px; font-weight: 500; color: #c9e2e4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-main span { display: block; margin-top: 2px; font-size: 7.5px; color: #62838a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-overdue { color: #f87171; font-size: 8px; }
.task-urge { color: #6b8c91; font-size: 7px; }

.screen-footer {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid rgba(56, 119, 129, 0.2);
  font-size: 8px;
  color: #557a80;
  flex-wrap: wrap;
}
.screen-footer span { display: inline-flex; align-items: center; gap: 6px; }

.screen-loading {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(3, 14, 21, 0.84);
  backdrop-filter: blur(4px);
}
.screen-loading b { font-size: 15px; color: #d8f8f5; letter-spacing: 1px; }
.screen-loading small { font-size: 10px; color: #6f9298; }
.screen-loading.error b { color: #fca5a5; }
.scan-line {
  width: 160px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #2fe3c8, transparent);
  animation: scan 1.8s linear infinite;
}

@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }
@keyframes scan { 0% { transform: translateX(-60px); opacity: 0; } 30%, 70% { opacity: 1; } 100% { transform: translateX(60px); opacity: 0; } }
@keyframes inspector-in { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } }

@media (max-width: 1540px) {
  .screen-body { grid-template-columns: 265px minmax(420px, 1fr) 296px; }
  .screen-brand { min-width: 340px; }
  .screen-brand h1 { font-size: 20px; }
  .screen-meta { gap: 10px; }
  .screen-clock { font-size: 19px; }
  .kpi { padding: 11px 10px; gap: 8px; }
  .kpi-icon { width: 32px; height: 32px; font-size: 15px; }
  .kpi-value { font-size: 23px; }
  .map-inspector { width: 252px; }
}

@media (max-width: 1320px) {
  .screen-body { grid-template-columns: 245px minmax(380px, 1fr) 275px; }
  .screen-brand { min-width: 300px; }
  .screen-brand h1 { font-size: 18px; }
  .screen-date { display: none; }
  .kpi-grid { gap: 7px; }
  .kpi-hint { display: none; }
  .map-badge { gap: 10px; }
  .map-scale { display: none; }
}

@media (max-width: 1100px) {
  .bigscreen { padding: 12px; }
  .screen-body { grid-template-columns: 1fr; }
  .screen-col { display: grid; grid-template-columns: 1fr 1fr; }
  .center-col { display: flex; }
  .left-col .quality-panel { grid-column: span 2; }
  .right-col .task-panel { grid-column: span 2; }
  .map-panel { min-height: 480px; }
  .kpi-grid { grid-template-columns: repeat(3, 1fr); }
  .screen-top { flex-wrap: wrap; }
  .screen-meta { order: 3; width: 100%; justify-content: flex-start; }
  .center-bottom { height: auto; }
  .trend-panel, .rule-panel { height: 180px; }
  .screen-footer { flex-direction: column; }
}
</style>
