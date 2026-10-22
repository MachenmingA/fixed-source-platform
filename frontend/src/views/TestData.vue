<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">测试数据</h1>
        <p class="page-sub">异常注入真值标签与规则命中结果的逐条对照，支撑回测和论文实验</p>
      </div>
      <div class="page-actions">
        <router-link to="/backtest"><el-button type="primary" plain><el-icon><TrendCharts /></el-icon> 去回测</el-button></router-link>
        <el-button @click="exportCsv"><el-icon><Download /></el-icon> 导出 CSV</el-button>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card card-pad">
      <div class="filter-bar" style="margin-bottom:0">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
          start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" style="width:260px" @change="load" />
        <el-select v-model="filters.ruleCode" placeholder="全部规则" clearable>
          <el-option v-for="r in ruleOptions" :key="r" :label="r" :value="r" />
        </el-select>
        <el-select v-model="filters.matchStatus" placeholder="全部命中状态" clearable>
          <el-option label="已命中" value="MATCHED" />
          <el-option label="漏报" value="MISSED" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="企业 / 点位 / 污染物" clearable :prefix-icon="Search" />
        <div class="grow"></div>
        <span class="tiny muted">{{ filteredLabels.length }} 条标签</span>
      </div>
    </div>

    <div class="metric-grid" style="margin-top:18px">
      <div class="metric-card"><span>真值标签</span><b>{{ summary.labelCount || overall.labels || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>规则命中</span><b>{{ overall.detected || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>正确命中 TP</span><b class="green">{{ overall.tp || 0 }}</b></div>
      <div class="metric-card"><span>误报 FP</span><b class="red">{{ overall.fp || 0 }}</b></div>
      <div class="metric-card"><span>漏报 FN</span><b class="amber">{{ overall.fn || 0 }}</b></div>
      <div class="metric-card highlight"><span>Precision</span><b>{{ pct(overall.precision) }}</b></div>
      <div class="metric-card highlight"><span>Recall</span><b>{{ pct(overall.recall) }}</b></div>
      <div class="metric-card highlight"><span>F1</span><b>{{ pct(overall.f1) }}</b></div>
    </div>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 异常剧本分布</h3><span class="soft-pill">{{ (summary.scenarios || []).length }} 类</span></div>
      <div class="scenarios">
        <div v-for="s in summary.scenarios || []" :key="s.anomalyType" class="scenario">
          <div class="scenario-icon" :class="String(s.anomalyType).toLowerCase()">{{ typeShort(s.anomalyType) }}</div>
          <div><b>{{ typeLabel(s.anomalyType) }}</b><span>{{ s.ruleCode }} · {{ s.count }} 个标签</span></div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <el-table :data="filteredLabels" style="width:100%">
        <el-table-column label="日期" width="110" prop="bizDate" />
        <el-table-column label="企业与点位" min-width="230">
          <template #default="{ row }">
            <div class="strong">{{ row.enterpriseName }}</div>
            <div class="sub">{{ row.pointName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="污染物" width="110">
          <template #default="{ row }"><b>{{ row.pollutantName }}</b><div class="sub">{{ row.pollutantCode }}</div></template>
        </el-table-column>
        <el-table-column label="规则 / 剧本" width="150">
          <template #default="{ row }"><b>{{ row.ruleCode }}</b> · {{ typeLabel(row.anomalyType) }}<div class="sub">{{ row.description }}</div></template>
        </el-table-column>
        <el-table-column label="预期时段" width="120">
          <template #default="{ row }">{{ row.startHour }}:00 — {{ row.endHour }}:00</template>
        </el-table-column>
        <el-table-column label="预期严重度" width="100" align="center">
          <template #default="{ row }"><el-tag :type="severityMeta(row.expectedSeverity).type" size="small" effect="plain">{{ severityMeta(row.expectedSeverity).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="命中数" width="80" align="center">
          <template #default="{ row }"><span :class="['hit-count', { hit: row.detectedCount > 0 }]">{{ row.detectedCount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="94">
          <template #default="{ row }">
            <el-tag :type="row.detectedCount > 0 ? 'success' : 'danger'" size="small">{{ row.detectedCount > 0 ? '已命中' : '漏报' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!filteredLabels.length" class="empty-block"><el-icon><DataAnalysis /></el-icon><span>当前筛选条件下没有测试标签</span></div>
    </div>

    <div class="card card-pad tip-card" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 数据口径</h3></div>
      <div class="tip-grid">
        <div><b>标签来源</b><span>模拟源内置 6 类异常剧本，按点位、污染物、规则、小时段生成真值标签。</span></div>
        <div><b>命中判断</b><span>同一规则 + 点位 + 污染物 + 日期的规则结果计为命中；标签数为真值，检测数超出部分计入 FP。</span></div>
        <div><b>使用方式</b><span>在回测页运行指定日期窗口，指标会落到回测批次，便于版本对比。</span></div>
        <div><b>注意</b><span>结果依赖合成数据，不代表真实业务准确率；时间边界和状态排除仍需细粒度评估。</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { DataAnalysis, Download, Refresh, Search, TrendCharts } from '@element-plus/icons-vue'
import api from '../api'
import { severityMeta } from '../utils/labels'

const loading = ref(false)
const summary = ref({})
const labels = ref([])
const range = ref(defaultRange())
const filters = reactive({ ruleCode: '', matchStatus: '', keyword: '' })

const overall = computed(() => summary.value.overall || {})
const ruleOptions = computed(() => [...new Set(labels.value.map((x) => x.ruleCode).filter(Boolean))].sort())
const filteredLabels = computed(() => labels.value.filter((x) => {
  if (filters.ruleCode && x.ruleCode !== filters.ruleCode) return false
  if (filters.matchStatus === 'MATCHED' && !(x.detectedCount > 0)) return false
  if (filters.matchStatus === 'MISSED' && x.detectedCount > 0) return false
  if (filters.keyword) {
    const text = `${x.enterpriseName} ${x.pointName} ${x.pollutantName} ${x.ruleCode}`.toLowerCase()
    if (!text.includes(filters.keyword.toLowerCase())) return false
  }
  return true
}))

function fmtDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  return [fmtDate(start), fmtDate(end)]
}
function pct(value) { return `${(Number(value || 0) * 100).toFixed(1)}%` }
function typeLabel(type) {
  return { EXCEED: '浓度超限', MISSING: '数据缺失', CONSTANT: '连续恒值', JUMP: '异常突变', STATUS: '状态不一致' }[type] || type
}
function typeShort(type) {
  return { EXCEED: '超', MISSING: '缺', CONSTANT: '恒', JUMP: '突', STATUS: '态' }[type] || '异'
}

async function load() {
  loading.value = true
  try {
    const params = { startDate: range.value[0], endDate: range.value[1] }
    const [summaryRes, labelsRes] = await Promise.all([api.get('/test-data/summary', { params }), api.get('/test-data/labels', { params })])
    summary.value = summaryRes.data
    labels.value = labelsRes.data
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  const headers = ['日期', '企业', '点位', '污染物', '规则', '异常类型', '预期时段', '预期严重度', '命中数', '状态']
  const rows = filteredLabels.value.map((x) => [
    x.bizDate, x.enterpriseName, x.pointName, x.pollutantName, x.ruleCode,
    typeLabel(x.anomalyType), `${x.startHour}:00-${x.endHour}:00`,
    x.expectedSeverity, x.detectedCount, x.detectedCount > 0 ? '已命中' : '漏报'
  ])
  const csv = '\ufeff' + [headers, ...rows].map((row) => row.map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `test-labels-${range.value[0]}-${range.value[1]}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<style scoped>
.metric-card b.green, .green { color: #16a34a; }
.metric-card b.red, .red { color: #dc2626; }
.metric-card b.amber, .amber { color: #d97706; }
.scenarios { display: flex; gap: 10px; flex-wrap: wrap; }
.scenario { display: flex; align-items: center; gap: 9px; border: 1px solid var(--line); background: #f9fcfe; border-radius: 11px; padding: 9px 12px; min-width: 160px; }
.scenario-icon { width: 30px; height: 30px; border-radius: 9px; background: #eef6ff; color: #2563eb; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 800; }
.scenario-icon.exceed { background: #fff0f0; color: #dc2626; }
.scenario-icon.missing { background: #fff7e8; color: #d97706; }
.scenario-icon.constant { background: #f3f0ff; color: #7c3aed; }
.scenario-icon.jump { background: #eef6ff; color: #2563eb; }
.scenario-icon.status { background: #f0fdf4; color: #16a34a; }
.scenario b { display: block; font-size: 11px; color: var(--ink); }
.scenario span { display: block; margin-top: 3px; font-size: 9px; color: var(--muted); }
.strong { font-size: 11px; font-weight: 700; color: var(--ink); }
.sub { margin-top: 3px; font-size: 9px; color: var(--muted); line-height: 1.5; }
.hit-count { display: inline-flex; min-width: 24px; height: 22px; border-radius: 7px; align-items: center; justify-content: center; background: #fff0f0; color: #dc2626; font-size: 11px; font-weight: 700; }
.hit-count.hit { background: #ecfdf5; color: #16a34a; }
.tip-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 11px; }
.tip-grid div { padding: 12px; background: #f9fcfe; border: 1px solid #eef5fa; border-radius: 11px; }
.tip-grid b { display: block; font-size: 11px; color: var(--ink); margin-bottom: 5px; }
.tip-grid span { font-size: 10px; line-height: 1.7; color: var(--muted); }
@media (max-width: 1280px) { .tip-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 700px) { .tip-grid { grid-template-columns: 1fr; } }
</style>
