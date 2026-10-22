<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">监测数据分析</h1>
        <p class="page-sub">把每日汇总和逐小时读数放在同一张分析画布：看趋势、峰值、超限时段和数据完整率</p>
      </div>
      <div class="page-actions">
        <span class="soft-pill green">最新业务日 {{ analysis.latestDate || '—' }}</span>
        <router-link to="/collect"><el-button><el-icon><Download /></el-icon> 采集批次</el-button></router-link>
        <el-button :loading="loading" @click="loadAnalysis"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card card-pad filter-panel">
      <div class="filter-bar" style="margin-bottom:0">
        <el-select v-model="enterpriseId" filterable placeholder="选择企业" style="width:250px" @change="onEnterpriseChange">
          <el-option v-for="item in enterprises" :key="item.id" :label="`${item.name} · ${item.region}`" :value="item.id" />
        </el-select>
        <el-select v-model="pointId" placeholder="选择排口 / 监测点" style="width:220px" @change="onPointChange">
          <el-option v-for="item in points" :key="item.id" :label="`${item.pointName}（${item.pointType || '监测点'}）`" :value="item.id" />
        </el-select>
        <el-select v-model="pollutantCode" placeholder="选择污染物" :disabled="!pollutants.length" style="width:190px" @change="loadAnalysis">
          <el-option v-for="item in pollutants" :key="item.code" :label="`${item.name} (${item.unit})`" :value="item.code" />
        </el-select>
        <el-radio-group v-model="days" class="range-switch" @change="loadAnalysis">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="14">近 14 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <div class="grow"></div>
        <div v-if="pollutant" class="standard-chip">
          <span>执行标准</span>
          <b>{{ pollutant.standardRef || '标准依据待维护' }}</b>
        </div>
      </div>
    </div>

    <div class="page-kpis monitor-kpis">
      <div v-for="metric in kpis" :key="metric.label" class="page-kpi" :class="`tone-${metric.tone}`">
        <span class="page-kpi-icon"><el-icon><component :is="metric.icon" /></el-icon></span>
        <div class="page-kpi-copy">
          <span>{{ metric.label }}</span>
          <b>{{ metric.value }}<small v-if="metric.unit && metric.value !== '—'">{{ metric.unit }}</small></b>
          <em>{{ metric.hint }}</em>
        </div>
      </div>
    </div>

    <template v-if="hasData">
      <section class="card card-pad hourly-panel">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 最新业务日逐时曲线</h3>
          <div class="chart-head-meta">
            <span class="chart-legend">
              <i class="legend-line"></i> 实测浓度
              <i class="legend-dot red"></i> 超限点
              <i class="legend-dot gray"></i> 缺失小时
            </span>
            <span class="soft-pill">{{ pollutant?.name }} · {{ pollutant?.unit }}</span>
          </div>
        </div>
        <EChart :option="hourlyOption" height="340px" />
        <div class="chart-notes">
          <span><i class="note-dot teal"></i> 正常小时 {{ normalHours }}</span>
          <span><i class="note-dot red"></i> 超限小时 {{ latestExceed }}</span>
          <span><i class="note-dot gray"></i> 缺失小时 {{ missingHours }}</span>
          <span class="chart-note-tip">红色虚线为执行限值；曲线缺口表示该小时没有有效监测数据。</span>
        </div>
      </section>

      <div class="monitor-split">
        <section class="card card-pad">
          <div class="card-head">
            <h3 class="card-title"><span class="dot"></span> 近 {{ days }} 日浓度走势</h3>
            <span class="soft-pill">日均 / 峰值</span>
          </div>
          <EChart :option="dailyOption" height="300px" />
        </section>
        <section class="card card-pad">
          <div class="card-head">
            <h3 class="card-title"><span class="dot"></span> 24 小时时段画像</h3>
            <span class="soft-pill">{{ days }} 天平均</span>
          </div>
          <EChart :option="profileOption" height="300px" />
        </section>
      </div>

      <section class="card card-pad heatmap-panel">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 近 {{ heatDays }} 日 × 24 小时浓度热力</h3>
          <span class="soft-pill">红框表示超限小时</span>
        </div>
        <EChart :option="heatmapOption" :height="heatmapHeight" />
        <p class="heat-tip">纵轴为业务日期，横轴为小时；空白格表示该小时没有有效监测数据。色阶按当前窗口浓度范围拉伸，红色描边表示超过执行限值。</p>
      </section>

      <section class="card card-pad daily-table-card">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 日汇总数据</h3>
          <span class="soft-pill">按 24 小时应到数据计算完整率</span>
        </div>
        <el-table :data="dailyDesc" max-height="430" style="width:100%">
          <el-table-column label="业务日期" width="120">
            <template #default="{ row }"><span class="mono">{{ row.date }}</span></template>
          </el-table-column>
          <el-table-column label="有效样本" width="100" align="center">
            <template #default="{ row }">{{ row.sampleCount }} / 24</template>
          </el-table-column>
          <el-table-column label="日均浓度" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.avg) }}</template>
          </el-table-column>
          <el-table-column label="峰值" width="110" align="right">
            <template #default="{ row }"><b :class="{ 'val-danger': row.exceedHours > 0 }">{{ fmt(row.max) }}</b></template>
          </el-table-column>
          <el-table-column label="最小" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.min) }}</template>
          </el-table-column>
          <el-table-column label="超限小时" width="100" align="center">
            <template #default="{ row }"><span :class="['count-pill', { danger: row.exceedHours > 0 }]">{{ row.exceedHours }}</span></template>
          </el-table-column>
          <el-table-column label="完整率" min-width="170">
            <template #default="{ row }">
              <div class="completeness">
                <el-progress :percentage="row.completeness || 0" :show-text="false" :stroke-width="7" :color="completenessColor(row.completeness)" />
                <span>{{ row.completeness || 0 }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="row.status === 'EXCEED' ? 'danger' : row.status === 'NO_DATA' ? 'info' : 'success'" size="small">
                {{ row.status === 'EXCEED' ? '有超限' : row.status === 'NO_DATA' ? '无数据' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="data-note">
          <b>数据口径</b>
          <span>逐时值来自 monitoring_record；日汇总按自然日聚合，完整率按每日 24 个应到小时计算；超限按污染物执行限值比较；热力图展示当前窗口最近 7 个业务日。</span>
        </div>
      </section>
    </template>

    <div v-else class="card empty-block monitor-empty">
      <el-icon><DataAnalysis /></el-icon>
      <span>{{ pollutant ? '当前监测点与污染物在所选窗口暂无有效数据' : '请选择企业、监测点和污染物查看图表' }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheck, DataAnalysis, Download, Odometer, Refresh, TrendCharts, Warning } from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'

const route = useRoute()
const loading = ref(false)
const enterprises = ref([])
const points = ref([])
const pollutants = ref([])
const enterpriseId = ref(String(route.query.enterpriseId || ''))
const pointId = ref(String(route.query.pointId || ''))
const pollutantCode = ref(String(route.query.pollutantCode || ''))
const days = ref(7)
const analysis = ref({ points: [], pollutants: [], daily: [], hourly: [], heatmap: [], heatDates: [], profile: [], stats: {}, pollutant: null })

const pollutant = computed(() => analysis.value.pollutant || null)
const unit = computed(() => pollutant.value?.unit || '')
const limit = computed(() => Number(pollutant.value?.limitValue || 0))
const stats = computed(() => analysis.value.stats || {})
const daily = computed(() => analysis.value.daily || [])
const hourly = computed(() => analysis.value.hourly || [])
const profile = computed(() => analysis.value.profile || [])
const heatDates = computed(() => analysis.value.heatDates || [])
const heatDays = computed(() => Math.max(1, heatDates.value.length))
const hasData = computed(() => daily.value.some((item) => Number(item.sampleCount || 0) > 0))
const dailyDesc = computed(() => daily.value.slice().reverse())

const kpis = computed(() => [
  {
    label: '最新日均浓度',
    value: fmt(stats.value.latestAvg),
    unit: unit.value,
    hint: `${analysis.value.latestDate || '—'} · ${pollutant.value?.name || '未选择污染物'}`,
    icon: Odometer,
    tone: 'blue'
  },
  {
    label: `近 ${days.value} 日峰值`,
    value: fmt(stats.value.max),
    unit: unit.value,
    hint: stats.value.peakDate ? `出现在 ${stats.value.peakDate}` : '—',
    icon: TrendCharts,
    tone: 'red'
  },
  {
    label: '超限小时',
    value: stats.value.exceedHours ?? 0,
    unit: 'h',
    hint: limit.value > 0 ? `执行限值 ${fmt(limit.value)} ${unit.value}` : '未维护执行限值',
    icon: Warning,
    tone: 'amber'
  },
  {
    label: '数据完整率',
    value: stats.value.completeness ?? 0,
    unit: '%',
    hint: `${stats.value.sampleCount || 0} / ${stats.value.expectedSamples || 0} 条有效样本`,
    icon: CircleCheck,
    tone: 'green'
  }
])

const latestExceed = computed(() => hourly.value.filter((item) => item.exceed).length)
const missingHours = computed(() => hourly.value.filter((item) => item.status === 'MISSING').length)
const normalHours = computed(() => hourly.value.filter((item) => item.status === 'NORMAL' && !item.exceed).length)

const hourlyLabels = computed(() => hourly.value.map((item) => String(item.time || '').slice(11, 16)))
const hourlyValues = computed(() => hourly.value.map((item) => (item.value === null || item.value === undefined ? null : Number(item.value))))
const exceedValues = computed(() => hourly.value.map((item) => (item.exceed ? Number(item.value) : null)))

const hourlyOption = computed(() => ({
  animationDuration: 450,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(15, 33, 55, 0.92)',
    borderWidth: 0,
    textStyle: { color: '#fff', fontSize: 11 },
    formatter: (params) => {
      const index = params?.[0]?.dataIndex
      const row = hourly.value[index]
      if (!row) return ''
      return `<b>${row.time}</b><br/>实测浓度：${row.value === null ? '缺失' : `${fmt(row.value)} ${unit.value}`}<br/>状态：${statusText(row.status)}`
    }
  },
  grid: { left: 52, right: 22, top: 26, bottom: 38 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: hourlyLabels.value,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dbe7ef' } },
    axisLabel: { color: '#8496a8', fontSize: 9, interval: 2 }
  },
  yAxis: {
    type: 'value',
    max: niceMax(hourlyValues.value, limit.value),
    name: unit.value,
    nameTextStyle: { color: '#94a6b5', fontSize: 9, padding: [0, 0, 0, -28] },
    axisLabel: { color: '#94a6b5', fontSize: 9 },
    splitLine: { lineStyle: { color: '#edf4f8', type: 'dashed' } }
  },
  series: [
    {
      name: '实测浓度',
      type: 'line',
      smooth: 0.25,
      symbol: 'circle',
      symbolSize: 4,
      data: hourlyValues.value,
      connectNulls: false,
      lineStyle: { width: 2.4, color: '#0fb9a6' },
      itemStyle: { color: '#0fb9a6', borderColor: '#fff', borderWidth: 1 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(15, 185, 166, 0.26)' },
            { offset: 1, color: 'rgba(15, 185, 166, 0.01)' }
          ]
        }
      },
      markLine: limit.value > 0 ? {
        silent: true,
        symbol: ['none', 'none'],
        lineStyle: { color: '#e0605a', type: 'dashed', width: 1.3 },
        label: { color: '#c84e49', fontSize: 9, formatter: `限值 ${fmt(limit.value)}`, position: 'insideEndTop' },
        data: [{ yAxis: limit.value }]
      } : undefined
    },
    {
      name: '超限点',
      type: 'scatter',
      data: exceedValues.value,
      symbolSize: 8,
      itemStyle: { color: '#ef4444', borderColor: '#fff', borderWidth: 1.5 },
      z: 6
    }
  ]
}))

const dailyOption = computed(() => ({
  animationDuration: 450,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(15, 33, 55, 0.92)',
    borderWidth: 0,
    textStyle: { color: '#fff', fontSize: 11 },
    formatter: (params) => {
      const index = params?.[0]?.dataIndex
      const row = daily.value[index]
      if (!row) return ''
      return `<b>${row.date}</b><br/>日均：${fmt(row.avg)} ${unit.value}<br/>峰值：${fmt(row.max)} ${unit.value}<br/>超限小时：${row.exceedHours}<br/>完整率：${row.completeness}%`
    }
  },
  legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { color: '#8496a8', fontSize: 10 } },
  grid: { left: 48, right: 18, top: 38, bottom: 30 },
  xAxis: {
    type: 'category',
    data: daily.value.map((item) => String(item.date || '').slice(5)),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dbe7ef' } },
    axisLabel: { color: '#8496a8', fontSize: 9 }
  },
  yAxis: {
    type: 'value',
    max: niceMax(daily.value.map((item) => item.max), limit.value),
    name: unit.value,
    nameTextStyle: { color: '#94a6b5', fontSize: 9, padding: [0, 0, 0, -28] },
    axisLabel: { color: '#94a6b5', fontSize: 9 },
    splitLine: { lineStyle: { color: '#edf4f8', type: 'dashed' } }
  },
  series: [
    {
      name: '日均',
      type: 'bar',
      barWidth: 16,
      data: daily.value.map((item) => item.avg),
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#36cdbd' },
            { offset: 1, color: '#a8e9e1' }
          ]
        }
      }
    },
    {
      name: '峰值',
      type: 'line',
      smooth: 0.2,
      symbol: 'circle',
      symbolSize: 5,
      data: daily.value.map((item) => item.max),
      lineStyle: { color: '#e5a23e', width: 2 },
      itemStyle: { color: '#e5a23e' },
      markLine: limit.value > 0 ? {
        silent: true,
        symbol: ['none', 'none'],
        lineStyle: { color: '#e0605a', type: 'dashed', width: 1.2 },
        label: { color: '#c84e49', fontSize: 8, formatter: `限值 ${fmt(limit.value)}`, position: 'insideEndTop' },
        data: [{ yAxis: limit.value }]
      } : undefined
    }
  ]
}))

const profileOption = computed(() => ({
  animationDuration: 450,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(15, 33, 55, 0.92)',
    borderWidth: 0,
    textStyle: { color: '#fff', fontSize: 11 },
    formatter: (params) => {
      const index = params?.[0]?.dataIndex
      const row = profile.value[index]
      if (!row) return ''
      return `<b>${String(row.hour).padStart(2, '0')}:00</b><br/>时段均值：${row.avg === null ? '无数据' : `${fmt(row.avg)} ${unit.value}`}<br/>时段峰值：${row.max === null ? '—' : `${fmt(row.max)} ${unit.value}`}<br/>样本数：${row.count}`
    }
  },
  grid: { left: 48, right: 18, top: 24, bottom: 32 },
  xAxis: {
    type: 'category',
    data: profile.value.map((item) => `${String(item.hour).padStart(2, '0')}:00`),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dbe7ef' } },
    axisLabel: { color: '#8496a8', fontSize: 9, interval: 2 }
  },
  yAxis: {
    type: 'value',
    max: niceMax(profile.value.map((item) => item.avg), limit.value),
    name: unit.value,
    nameTextStyle: { color: '#94a6b5', fontSize: 9, padding: [0, 0, 0, -28] },
    axisLabel: { color: '#94a6b5', fontSize: 9 },
    splitLine: { lineStyle: { color: '#edf4f8', type: 'dashed' } }
  },
  series: [{
    name: '时段均值',
    type: 'bar',
    barWidth: '54%',
    data: profile.value.map((item) => ({
      value: item.avg,
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: item.avg !== null && limit.value > 0 && Number(item.avg) > limit.value ? '#ef7d6b' : '#4fc4b6'
      }
    })),
    markLine: limit.value > 0 ? {
      silent: true,
      symbol: ['none', 'none'],
      lineStyle: { color: '#e0605a', type: 'dashed', width: 1.2 },
      label: { show: false },
      data: [{ yAxis: limit.value }]
    } : undefined
  }]
}))

const heatmapOption = computed(() => {
  const dateIndex = new Map(heatDates.value.map((date, index) => [date, index]))
  const cells = analysis.value.heatmap || []
  const values = cells.map((item) => Number(item.value)).filter(Number.isFinite)
  const minValue = values.length ? Math.floor(Math.min(...values)) : 0
  const maxValue = values.length ? Math.ceil(Math.max(...values)) : Math.max(limit.value, 1)
  const scaleMax = Math.max(maxValue, minValue + 1)
  const cellLookup = new Map(cells.map((item) => [`${item.date}-${item.hour}`, item]))
  return {
    animationDuration: 450,
    tooltip: {
      position: 'top',
      backgroundColor: 'rgba(15, 33, 55, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 11 },
      formatter: (params) => {
        const data = params?.data?.value || params?.data || []
        const date = heatDates.value[data[1]]
        const cell = cellLookup.get(`${date}-${data[0]}`) || {}
        const value = cell.value !== undefined ? cell.value : data[2]
        return `<b>${date} ${String(data[0]).padStart(2, '0')}:00</b><br/>浓度：${fmt(value)} ${unit.value}<br/>状态：${statusText(cell.status)}${limit.value > 0 && Number(value) > limit.value ? '<br/><span style="color:#ffb4ad">超过执行限值</span>' : ''}`
      }
    },
    grid: { left: 58, right: 22, top: 18, bottom: 58 },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe7ef' } },
      axisLabel: { color: '#8496a8', fontSize: 9, interval: 2 },
      splitArea: { show: true, areaStyle: { color: ['#fbfdfe', '#f6fafc'] } }
    },
    yAxis: {
      type: 'category',
      data: heatDates.value.map((date) => String(date).slice(5)),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#6e8295', fontSize: 9 },
      splitArea: { show: true, areaStyle: { color: ['#fbfdfe', '#f6fafc'] } }
    },
    visualMap: {
      min: minValue,
      max: scaleMax,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemWidth: 10,
      itemHeight: 100,
      textStyle: { color: '#8a9bab', fontSize: 8 },
      text: [`高 ${fmt(scaleMax)}`, `低 ${fmt(minValue)}`],
      inRange: { color: ['#f3fbfa', '#b9ece5', '#6bcbbf', '#249d91', '#0c666d'] }
    },
    series: [{
      name: '小时浓度',
      type: 'heatmap',
      data: cells.map((item) => {
        const date = String(item.date)
        const dayIndex = dateIndex.get(date)
        const value = Number(item.value)
        return {
          value: [Number(item.hour), dayIndex, value],
          itemStyle: limit.value > 0 && value > limit.value
            ? { borderColor: '#b42318', borderWidth: 2 }
            : undefined
        }
      }).filter((item) => item.value[1] !== undefined),
      label: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 1.5, borderRadius: 3 },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(15, 33, 55, 0.2)' } }
    }]
  }
})

const heatmapHeight = computed(() => `${Math.max(270, heatDates.value.length * 36 + 100)}px`)

function fmt(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '—'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(digits) : String(value)
}

function statusText(value) {
  return {
    NORMAL: '正常',
    FAULT: '设备故障',
    STOP: '停运',
    STOPPED: '停运',
    MISSING: '缺失',
    EXCEED: '超限'
  }[value] || value || '正常'
}

function completenessColor(value) {
  const number = Number(value || 0)
  if (number >= 95) return '#16a34a'
  if (number >= 80) return '#e5a23e'
  return '#ef7d6b'
}

function niceMax(values, limitValue) {
  const max = Math.max(Number(limitValue || 0), ...values.filter((value) => value !== null && value !== undefined && Number.isFinite(Number(value))).map(Number))
  if (!Number.isFinite(max) || max <= 0) return undefined
  return Math.ceil((max * 1.15) / 5) * 5
}

async function loadEnterprises() {
  const { data } = await api.get('/enterprises')
  enterprises.value = Array.isArray(data) ? data : []
  if (!enterpriseId.value) enterpriseId.value = enterprises.value[0]?.id || ''
}

async function loadAnalysis() {
  if (!enterpriseId.value) return
  loading.value = true
  try {
    const { data } = await api.get('/monitoring/analysis', {
      params: {
        enterpriseId: enterpriseId.value,
        pointId: pointId.value || undefined,
        pollutantCode: pollutantCode.value || undefined,
        days: days.value
      }
    })
    analysis.value = data || {}
    points.value = data?.points || []
    pollutants.value = data?.pollutants || []
    if (data?.pointId) pointId.value = data.pointId
    if (data?.pollutantCode) pollutantCode.value = data.pollutantCode
  } finally {
    loading.value = false
  }
}

function onEnterpriseChange() {
  pointId.value = ''
  pollutantCode.value = ''
  loadAnalysis()
}

function onPointChange() {
  pollutantCode.value = ''
  loadAnalysis()
}

onMounted(async () => {
  await loadEnterprises()
  await loadAnalysis()
})
</script>

<style scoped>
.filter-panel { margin-bottom: 16px; }
.monitor-kpis { margin-bottom: 16px; }
.range-switch :deep(.el-radio-button__inner) { padding: 8px 13px; font-size: 11px; }
.standard-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 150px;
  padding-left: 12px;
  border-left: 1px solid var(--line);
}
.standard-chip span { color: var(--muted-2); font-size: 8px; }
.standard-chip b { color: #45677a; font-size: 9px; font-weight: 600; }
.hourly-panel { margin-bottom: 16px; }
.monitor-split { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-head-meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.chart-legend { display: flex; align-items: center; gap: 6px; color: var(--muted); font-size: 9px; white-space: nowrap; }
.legend-line { width: 16px; height: 2px; border-radius: 2px; background: #0fb9a6; }
.legend-dot, .note-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.legend-dot.red, .note-dot.red { background: #ef4444; }
.legend-dot.gray, .note-dot.gray { background: #b7c6d2; }
.note-dot.teal { background: #0fb9a6; }
.chart-notes { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-top: 10px; padding-top: 10px; border-top: 1px dashed #edf3f8; color: var(--muted); font-size: 9px; }
.chart-notes span { display: inline-flex; align-items: center; gap: 5px; }
.chart-note-tip { margin-left: auto; color: var(--muted-2); }
.heatmap-panel { margin-bottom: 16px; }
.heat-tip { margin: 8px 0 0; color: var(--muted-2); font-size: 9px; line-height: 1.65; }
.daily-table-card { margin-bottom: 4px; }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.completeness { display: flex; align-items: center; gap: 9px; }
.completeness .el-progress { flex: 1; }
.completeness span { width: 36px; color: var(--muted); font-size: 9px; text-align: right; }
.count-pill {
  display: inline-flex;
  min-width: 24px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: #f2f6fb;
  color: #7e91a5;
  font-size: 10px;
  font-weight: 700;
}
.count-pill.danger { background: #fff0ef; color: #dc2626; }
.val-danger { color: #dc2626; }
.data-note { display: flex; gap: 8px; align-items: flex-start; margin-top: 12px; padding-top: 11px; border-top: 1px dashed #edf3f8; color: var(--muted); font-size: 9px; line-height: 1.7; }
.data-note b { flex-shrink: 0; color: #476779; font-weight: 700; }
.monitor-empty { min-height: 280px; }
@media (max-width: 1280px) {
  .monitor-split { grid-template-columns: 1fr; }
}
@media (max-width: 900px) {
  .filter-bar .el-select { width: 100% !important; }
  .range-switch { width: 100%; }
  .range-switch :deep(.el-radio-button) { flex: 1; }
  .range-switch :deep(.el-radio-button__inner) { width: 100%; }
  .standard-chip { border-left: 0; padding-left: 0; }
  .chart-note-tip { margin-left: 0; }
}
</style>
