<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">规则回测</h1>
        <p class="page-sub">用带真值标签的模拟数据集评估规则命中效果，输出精确率、召回率和 F1</p>
      </div>
      <div class="page-actions">
        <router-link to="/test-data"><el-button><el-icon><DataAnalysis /></el-icon> 查看测试数据</el-button></router-link>
        <el-button :loading="loading" @click="loadAll"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card card-pad">
      <div class="run-bar">
        <div>
          <div class="run-title">选择回测窗口</div>
          <div class="run-sub">使用当前规则版本 R001—R005/v1.0 对历史线索与真值标签做分组匹配</div>
        </div>
        <div class="run-actions">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" style="width:260px" />
          <el-button type="primary" :loading="running" @click="runBacktest"><el-icon><VideoPlay /></el-icon> 运行回测</el-button>
        </div>
      </div>
      <div class="method-note">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ summary.matchMethod || '按规则 + 点位 + 污染物 + 日期分组计数匹配；TP=min(标签数, 命中数)，FP=多出的命中，FN=漏掉的标签。' }}</span>
      </div>
    </div>

    <div class="metric-grid" style="margin-top:18px">
      <div class="metric-card"><span>真值标签</span><b>{{ overall.labels || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>规则命中</span><b>{{ overall.detected || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>TP 正确命中</span><b class="green">{{ overall.tp || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>FP 误报</span><b class="red">{{ overall.fp || 0 }}</b><small>条</small></div>
      <div class="metric-card"><span>FN 漏报</span><b class="amber">{{ overall.fn || 0 }}</b><small>条</small></div>
      <div class="metric-card highlight"><span>Precision</span><b>{{ pct(overall.precision) }}</b><small>精确率</small></div>
      <div class="metric-card highlight"><span>Recall</span><b>{{ pct(overall.recall) }}</b><small>召回率</small></div>
      <div class="metric-card highlight"><span>F1</span><b>{{ pct(overall.f1) }}</b><small>综合指标</small></div>
    </div>

    <div class="grid-2 backtest-split" style="margin-top:18px">
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 分规则指标</h3><span class="soft-pill">R001—R005</span></div>
        <EChart :option="ruleOption" height="300px" />
      </div>
      <div class="card card-pad">
        <div class="card-head"><h3 class="card-title"><span class="dot"></span> 指标明细</h3><span class="soft-pill">{{ byRule.length }} 条规则</span></div>
        <el-table :data="byRule" style="width:100%">
          <el-table-column label="规则" min-width="130">
            <template #default="{ row }"><b class="rule-name">{{ row.ruleCode }}</b><span class="rule-sub">{{ row.ruleName }}</span></template>
          </el-table-column>
          <el-table-column label="标签" width="64" align="center" prop="labels" />
          <el-table-column label="命中" width="64" align="center" prop="detected" />
          <el-table-column label="P" width="70" align="center"><template #default="{ row }">{{ pct(row.precision) }}</template></el-table-column>
          <el-table-column label="R" width="70" align="center"><template #default="{ row }">{{ pct(row.recall) }}</template></el-table-column>
          <el-table-column label="F1" width="70" align="center"><template #default="{ row }"><b>{{ pct(row.f1) }}</b></template></el-table-column>
        </el-table>
      </div>
    </div>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 回测记录</h3><span class="soft-pill">保存批次可追溯</span></div>
      <el-table :data="runs" style="width:100%" @row-click="viewRun">
        <el-table-column label="批次" min-width="190">
          <template #default="{ row }"><span class="mono">{{ row.runNo }}</span><div class="run-sub">{{ row.createdBy }} · {{ row.createdAt }}</div></template>
        </el-table-column>
        <el-table-column label="日期范围" width="190">
          <template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="标签 / 命中" width="120" align="center">
          <template #default="{ row }">{{ row.totalLabels }} / {{ row.detectedCount }}</template>
        </el-table-column>
        <el-table-column label="TP / FP / FN" width="120" align="center">
          <template #default="{ row }"><span class="green">{{ row.truePositive }}</span> / <span class="red">{{ row.falsePositive }}</span> / <span class="amber">{{ row.falseNegative }}</span></template>
        </el-table-column>
        <el-table-column label="P / R / F1" width="150" align="center">
          <template #default="{ row }">{{ pct(row.precision) }} / {{ pct(row.recall) }} / <b>{{ pct(row.f1) }}</b></template>
        </el-table-column>
        <el-table-column label="" width="70" align="right"><template #default><el-button link type="primary">载入</el-button></template></el-table-column>
      </el-table>
    </div>

    <div class="card card-pad tip-card" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 指标解释与局限</h3></div>
      <div class="tip-grid">
        <div><b>Precision 精确率</b><span>命中的线索里，有多少是真值标签对应的异常。误报越多，精确率越低。</span></div>
        <div><b>Recall 召回率</b><span>真值标签里，有多少被规则命中。漏报越多，召回率越低。</span></div>
        <div><b>F1</b><span>精确率和召回率的综合指标，适合对比不同规则版本。</span></div>
        <div><b>当前局限</b><span>按“规则+点位+污染物+日期”分组计数匹配，暂未做秒级时间边界匹配；后续可加时间重叠和状态排除的细粒度评估。</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, InfoFilled, Refresh, VideoPlay } from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'

const loading = ref(false)
const running = ref(false)
const summary = ref({})
const runs = ref([])
const range = ref(defaultRange())

const overall = computed(() => summary.value.overall || {})
const byRule = computed(() => summary.value.byRule || [])

const ruleOption = computed(() => {
  const rows = byRule.value
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#70829a', fontSize: 11 } },
    grid: { left: 32, right: 12, top: 36, bottom: 24 },
    xAxis: { type: 'category', data: rows.map((x) => x.ruleCode), axisTick: { show: false }, axisLine: { lineStyle: { color: '#dfe8f3' } }, axisLabel: { color: '#6a7d90', fontSize: 10 } },
    yAxis: { type: 'value', max: 1, axisLabel: { color: '#9aa8bb', fontSize: 10, formatter: (v) => `${Math.round(v * 100)}%` }, splitLine: { lineStyle: { color: '#eef4fa', type: 'dashed' } } },
    series: [
      { name: 'Precision', type: 'bar', data: rows.map((x) => x.precision), barWidth: 14, itemStyle: { color: '#0fb9a6', borderRadius: [5, 5, 0, 0] } },
      { name: 'Recall', type: 'bar', data: rows.map((x) => x.recall), barWidth: 14, itemStyle: { color: '#3b82f6', borderRadius: [5, 5, 0, 0] } },
      { name: 'F1', type: 'line', data: rows.map((x) => x.f1), smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { color: '#f59e0b', width: 2 }, itemStyle: { color: '#f59e0b' } }
    ]
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

function pct(value) {
  const n = Number(value || 0)
  return `${(n * 100).toFixed(1)}%`
}

async function loadSummary() {
  const { data } = await api.get('/test-data/summary', { params: { startDate: range.value[0], endDate: range.value[1] } })
  summary.value = data
}

async function loadRuns() {
  const { data } = await api.get('/backtests', { params: { limit: 20 } })
  runs.value = data
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadSummary(), loadRuns()])
  } finally {
    loading.value = false
  }
}

async function runBacktest() {
  running.value = true
  try {
    const { data } = await api.post('/backtests', { startDate: range.value[0], endDate: range.value[1] })
    ElMessage.success(`回测完成：F1 ${pct(data.overall?.f1)}`)
    await loadRuns()
    const { data: fresh } = await api.get('/test-data/summary', { params: { startDate: range.value[0], endDate: range.value[1] } })
    summary.value = fresh
  } finally {
    running.value = false
  }
}

async function viewRun(row) {
  const { data } = await api.get(`/backtests/${row.id}`)
  summary.value = data.metrics || {}
  ElMessage.success(`已载入回测 ${data.runNo}`)
}

onMounted(loadAll)
</script>

<style scoped>
.run-bar { display: flex; justify-content: space-between; align-items: center; gap: 18px; flex-wrap: wrap; }
.run-title { font-size: 14px; font-weight: 700; color: var(--ink); }
.run-sub { margin-top: 4px; font-size: 10px; color: var(--muted); }
.run-actions { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.method-note { margin-top: 16px; padding: 10px 12px; border-radius: 10px; background: #f3fbf9; color: #4c7b74; font-size: 10px; line-height: 1.7; display: flex; gap: 6px; align-items: flex-start; }
.metric-card b.green, .green { color: #16a34a; }
.metric-card b.red, .red { color: #dc2626; }
.metric-card b.amber, .amber { color: #d97706; }
.rule-name { display: block; font-size: 11px; color: var(--ink); }
.rule-sub { display: block; margin-top: 3px; font-size: 9px; color: var(--muted); }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.tip-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 11px; }
.tip-grid div { padding: 12px; background: #f9fcfe; border: 1px solid #eef5fa; border-radius: 11px; }
.tip-grid b { display: block; font-size: 11px; color: var(--ink); margin-bottom: 5px; }
.tip-grid span { font-size: 10px; line-height: 1.7; color: var(--muted); }
@media (max-width: 1500px) { .backtest-split { grid-template-columns: 1fr; } }
@media (max-width: 1280px) { .tip-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 700px) { .tip-grid { grid-template-columns: 1fr; } }
:deep(.el-table__row) { cursor: pointer; }
</style>
