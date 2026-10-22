<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">数据质量</h1>
        <p class="page-sub">采集缺失、异常数据留痕与点位质量分布；异常数据不静默丢弃</p>
      </div>
      <div class="page-actions">
        <router-link to="/collect"><el-button><el-icon><Download /></el-icon> 采集批次</el-button></router-link>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card card-pad">
      <div class="filter-bar" style="margin-bottom:0">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" style="width:260px" @change="load" />
        <el-select v-model="filters.severity" placeholder="全部级别" clearable>
          <el-option label="ERROR" value="ERROR" />
          <el-option label="WARN" value="WARN" />
        </el-select>
        <el-select v-model="filters.issueType" placeholder="全部类型" clearable>
          <el-option label="数据缺失" value="MISSING" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="grow"></div>
        <span class="tiny muted">按日期范围统计</span>
      </div>
    </div>

    <div class="stat-grid" style="margin-top:18px">
      <div class="stat-card"><div class="stat-label">质量问题</div><div class="stat-value">{{ summary.total || 0 }}<small>条</small></div><div class="stat-hint">全部问题记录</div></div>
      <div class="stat-card"><div class="stat-label">ERROR</div><div class="stat-value" style="color:#dc2626">{{ summary.error || 0 }}<small>条</small></div><div class="stat-hint">缺失 ≥ 6 小时</div></div>
      <div class="stat-card"><div class="stat-label">WARN</div><div class="stat-value" style="color:#d97706">{{ summary.warn || 0 }}<small>条</small></div><div class="stat-hint">缺失 1—5 小时</div></div>
      <div class="stat-card"><div class="stat-label">涉及点位</div><div class="stat-value">{{ (summary.byPoint || []).length }}<small>个</small></div><div class="stat-hint">按问题数排序</div></div>
    </div>

    <div class="grid-2" style="margin-top:18px">
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 问题点数 Top 10</h3><span class="soft-pill">缺失小时</span></div>
        <EChart :option="pointOption" height="280px" />
      </div>
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 问题类型</h3><span class="soft-pill">当前以缺失为主</span></div>
        <EChart :option="typeOption" height="280px" />
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <el-table :data="issues" style="width:100%">
        <el-table-column label="日期" width="110">
          <template #default="{ row }"><span class="mono">{{ row.monitorDate }}</span></template>
        </el-table-column>
        <el-table-column label="企业与点位" min-width="230">
          <template #default="{ row }"><b class="strong">{{ row.enterpriseName }}</b><div class="sub">{{ row.pointName }}</div></template>
        </el-table-column>
        <el-table-column label="污染物" width="110">
          <template #default="{ row }">{{ row.pollutantName }} <span class="sub">{{ row.pollutantCode }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag size="small" type="warning" effect="plain">{{ row.issueType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }"><el-tag size="small" :type="row.severity === 'ERROR' ? 'danger' : 'warning'">{{ row.severity }}</el-tag></template>
        </el-table-column>
        <el-table-column label="说明" min-width="300" prop="detail" show-overflow-tooltip />
        <el-table-column label="批次" width="190">
          <template #default="{ row }"><span class="mono tiny">{{ row.batchNo }}</span></template>
        </el-table-column>
      </el-table>
      <div v-if="!issues.length" class="empty-block"><el-icon><CircleCheck /></el-icon><span>当前窗口没有数据质量问题</span></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CircleCheck, Download, Refresh } from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'

const loading = ref(false)
const summary = ref({})
const issues = ref([])
const range = ref(defaultRange())
const filters = reactive({ severity: '', issueType: '' })

const pointOption = computed(() => {
  const data = (summary.value.byPoint || []).slice(0, 10).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 22, top: 10, bottom: 10, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef4fa', type: 'dashed' } }, axisLabel: { color: '#9aa8bb', fontSize: 10 } },
    yAxis: { type: 'category', data: data.map((x) => x.pointId), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#5f7188', fontSize: 10 } },
    series: [{ type: 'bar', data: data.map((x) => x.count), barWidth: 11, itemStyle: { color: '#f59e0b', borderRadius: [0, 8, 8, 0] }, label: { show: true, position: 'right', color: '#7d8da2', fontSize: 10 } }]
  }
})

const typeOption = computed(() => {
  const data = summary.value.byType || []
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 9, itemHeight: 9, textStyle: { color: '#70829a', fontSize: 11 } },
    series: [{ type: 'pie', radius: ['50%', '70%'], center: ['50%', '43%'], itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 8 }, label: { show: false }, data: data.map((x, i) => ({ name: x.issueType, value: x.count, itemStyle: { color: i === 0 ? '#f59e0b' : '#3b82f6' } })) }]
  }
})

function fmtDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  return [fmtDate(start), fmtDate(end)]
}

async function load() {
  loading.value = true
  try {
    const params = { startDate: range.value[0], endDate: range.value[1], severity: filters.severity, issueType: filters.issueType }
    const [summaryRes, listRes] = await Promise.all([api.get('/data-quality/summary', { params }), api.get('/data-quality', { params })])
    summary.value = summaryRes.data
    issues.value = listRes.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.strong { font-size: 11px; color: var(--ink); }
.sub { margin-top: 3px; font-size: 9px; color: var(--muted); }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
</style>
