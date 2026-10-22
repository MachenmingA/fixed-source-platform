<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">区域态势</h1>
        <p class="page-sub">区域风险、行业分布、事件密度与闭环情况</p>
      </div>
      <div class="page-actions">
        <span class="soft-pill green">{{ overview.regionCount || 0 }} 个区域</span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card"><div class="stat-label">监管企业</div><div class="stat-value">{{ overview.enterpriseCount || 0 }}<small>家</small></div><div class="stat-hint">覆盖 {{ overview.regionCount || 0 }} 个区域</div></div>
      <div class="stat-card"><div class="stat-label">高风险企业</div><div class="stat-value" style="color:#dc2626">{{ overview.highRiskCount || 0 }}<small>家</small></div><div class="stat-hint">C / D 级企业</div></div>
      <div class="stat-card"><div class="stat-label">待复核事件</div><div class="stat-value" style="color:#d97706">{{ overview.eventPending || 0 }}<small>件</small></div><div class="stat-hint">按 P1/P2/P3 排序</div></div>
      <div class="stat-card"><div class="stat-label">进行中任务</div><div class="stat-value" style="color:#2563eb">{{ overview.taskOpen || 0 }}<small>件</small></div><div class="stat-hint">含待下发、执行中</div></div>
      <div class="stat-card"><div class="stat-label">闭环率</div><div class="stat-value" style="color:#16a34a">{{ overview.closureRate || 0 }}<small>%</small></div><div class="stat-hint">已关闭 / 全部任务</div></div>
      <div class="stat-card"><div class="stat-label">AI 拒答</div><div class="stat-value" style="color:#b45309">{{ overview.aiRefusal || 0 }}<small>条</small></div><div class="stat-hint">无依据时明确拒答</div></div>
    </div>

    <div class="grid-2" style="margin-top:18px">
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 区域风险与事件密度</h3><span class="soft-pill">风险均分 / 事件数</span></div>
        <EChart :option="regionOption" height="310px" />
      </div>
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 风险等级矩阵</h3><span class="soft-pill">A → D</span></div>
        <EChart :option="matrixOption" height="310px" />
      </div>
    </div>

    <div class="grid-2" style="margin-top:18px">
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 行业风险概览</h3><span class="soft-pill">{{ (overview.industries || []).length }} 个行业</span></div>
        <EChart :option="industryOption" height="300px" />
      </div>
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 重点事件</h3><router-link class="more-link" to="/events">进入工作台 <el-icon><ArrowRight /></el-icon></router-link></div>
        <div class="event-list">
          <div v-for="event in overview.topEvents || []" :key="event.id" class="event-row" @click="openEvent(event)">
            <div class="event-priority" :class="event.priority?.toLowerCase()">{{ event.priority }}</div>
            <div class="event-main">
              <b>{{ event.title }}</b>
              <span>{{ event.enterpriseName }} · {{ event.region }} · {{ event.startTime }}</span>
            </div>
            <el-tag :type="statusMeta(event.status).type" size="small">{{ statusMeta(event.status).label }}</el-tag>
          </div>
          <div v-if="!(overview.topEvents || []).length" class="empty-block"><el-icon><DataLine /></el-icon><span>暂无重点事件</span></div>
        </div>
      </div>
    </div>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 区域明细</h3><span class="soft-pill">可下钻到企业画像</span></div>
      <el-table :data="overview.regions || []" style="width:100%" @row-click="goEnterprises">
        <el-table-column prop="region" label="区域" width="120" />
        <el-table-column label="企业" width="90" align="center">
          <template #default="{ row }"><span class="mono">{{ row.enterpriseCount }}</span></template>
        </el-table-column>
        <el-table-column label="高风险" width="90" align="center">
          <template #default="{ row }"><span :class="['count-badge', { hot: row.highRisk > 0 }]">{{ row.highRisk }}</span></template>
        </el-table-column>
        <el-table-column label="风险均分" width="160">
          <template #default="{ row }">
            <div class="risk-bar"><el-progress :percentage="row.avgRisk || 0" :show-text="false" :stroke-width="7" color="#f59e0b" /><span>{{ row.avgRisk }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="事件" width="90" align="center">
          <template #default="{ row }"><span class="mono">{{ row.eventCount }}</span></template>
        </el-table-column>
        <el-table-column label="任务" width="90" align="center">
          <template #default="{ row }"><span class="mono">{{ row.taskCount }}</span></template>
        </el-table-column>
        <el-table-column label="闭环率" min-width="180">
          <template #default="{ row }">
            <div class="risk-bar">
              <el-progress :percentage="row.taskCount ? Math.round((row.closedTasks || 0) * 100 / row.taskCount) : 0" :show-text="false" :stroke-width="7" color="#0fb9a6" />
              <span>{{ row.taskCount ? Math.round((row.closedTasks || 0) * 100 / row.taskCount) : 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="" width="80" align="right"><template #default><el-button link type="primary">查看 <el-icon><ArrowRight /></el-icon></el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, DataLine, Refresh } from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'
import { statusMeta } from '../utils/labels'

const router = useRouter()
const loading = ref(false)
const overview = ref({})

const regionOption = computed(() => {
  const data = (overview.value.regions || []).slice().sort((a, b) => (b.avgRisk || 0) - (a.avgRisk || 0))
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#70829a', fontSize: 11 } },
    grid: { left: 10, right: 18, top: 34, bottom: 10, containLabel: true },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#eef4fa', type: 'dashed' } }, axisLabel: { color: '#9aa8bb', fontSize: 10 } },
    yAxis: { type: 'category', data: data.map((x) => x.region), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#5f7188', fontSize: 11 } },
    series: [
      { name: '风险均分', type: 'bar', data: data.map((x) => x.avgRisk), barWidth: 11, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#f59e0b' } },
      { name: '事件数', type: 'bar', data: data.map((x) => x.eventCount), barWidth: 11, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#3b82f6' } }
    ]
  }
})

const matrixOption = computed(() => {
  const regions = [...new Set((overview.value.riskMatrix || []).map((x) => x.region))]
  const levels = ['A', 'B', 'C', 'D']
  const rows = overview.value.riskMatrix || []
  const data = []
  regions.forEach((region, xi) => levels.forEach((level, yi) => {
    const item = rows.find((x) => x.region === region && x.riskLevel === level)
    data.push([xi, yi, item ? item.count : 0])
  }))
  return {
    tooltip: { position: 'top', formatter: (p) => `${regions[p.data[0]]} · ${levels[p.data[1]]} 级：${p.data[2]} 家` },
    grid: { left: 34, right: 18, top: 20, bottom: 42 },
    xAxis: { type: 'category', data: regions, splitArea: { show: true }, axisLabel: { color: '#6a7d90', fontSize: 9, rotate: 28, interval: 0 }, axisLine: { show: false }, axisTick: { show: false } },
    yAxis: { type: 'category', data: levels.map((x) => x + ' 级'), splitArea: { show: true }, axisLabel: { color: '#6a7d90', fontSize: 10 }, axisLine: { show: false }, axisTick: { show: false } },
    visualMap: { min: 0, max: Math.max(1, ...data.map((x) => x[2])), calculable: true, orient: 'horizontal', left: 'center', bottom: 0, itemWidth: 10, itemHeight: 80, textStyle: { color: '#8a99ad', fontSize: 9 }, inRange: { color: ['#eef8f6', '#8fdcd3', '#0fb9a6', '#0a7a71'] } },
    series: [{ type: 'heatmap', data, label: { show: true, color: '#24465b', fontSize: 10 }, itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 5 } }]
  }
})

const industryOption = computed(() => {
  const data = (overview.value.industries || []).slice(0, 8).sort((a, b) => (b.avgRisk || 0) - (a.avgRisk || 0))
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#70829a', fontSize: 11 } },
    grid: { left: 10, right: 18, top: 34, bottom: 10, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef4fa', type: 'dashed' } }, axisLabel: { color: '#9aa8bb', fontSize: 10 } },
    yAxis: { type: 'category', data: data.map((x) => x.industry), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#5f7188', fontSize: 10 } },
    series: [
      { name: '风险均分', type: 'bar', data: data.map((x) => x.avgRisk), barWidth: 10, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#0fb9a6' } },
      { name: '事件数', type: 'bar', data: data.map((x) => x.eventCount), barWidth: 10, itemStyle: { borderRadius: [0, 8, 8, 0], color: '#c4b5fd' } }
    ]
  }
})

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/cockpit/overview')
    overview.value = data
  } finally {
    loading.value = false
  }
}

function openEvent(event) {
  router.push({ path: '/events', query: { id: event.id } })
}

function goEnterprises(row) {
  router.push({ path: '/enterprises', query: { region: row.region } })
}

onMounted(load)
</script>

<style scoped>
.event-list { display: flex; flex-direction: column; gap: 7px; }
.event-row { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 11px; cursor: pointer; transition: background 0.2s; }
.event-row:hover { background: #f7fbfa; }
.event-priority { width: 30px; height: 30px; border-radius: 8px; background: #f2f6fb; color: #7d8da2; font-size: 9px; font-weight: 800; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.event-priority.p1 { background: #fff0f0; color: #dc2626; }
.event-priority.p2 { background: #fff7e8; color: #d97706; }
.event-priority.p3 { background: #eef6ff; color: #2563eb; }
.event-main { flex: 1; min-width: 0; }
.event-main b { display: block; font-size: 11px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-main span { display: block; margin-top: 3px; font-size: 9px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.more-link { font-size: 11px; color: var(--teal-dark); display: inline-flex; align-items: center; gap: 3px; font-weight: 600; }
.risk-bar { display: flex; align-items: center; gap: 8px; }
.risk-bar .el-progress { flex: 1; }
.risk-bar span { width: 36px; font-size: 10px; color: var(--muted); text-align: right; }
.count-badge { display: inline-flex; min-width: 24px; height: 22px; border-radius: 7px; align-items: center; justify-content: center; background: #f2f6fb; color: #8494a8; font-size: 11px; font-weight: 700; }
.count-badge.hot { background: #fff0f0; color: #dc2626; }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 11px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
