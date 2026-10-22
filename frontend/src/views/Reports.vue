<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">核查报告</h1>
        <p class="page-sub">由事件证据、AI 研判和现场反馈生成的报告草稿</p>
      </div>
      <div class="page-actions">
        <router-link to="/events"><el-button type="primary"><el-icon><Warning /></el-icon> 去事件工作台</el-button></router-link>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="page-kpis">
      <div v-for="metric in reportStats" :key="metric.label" class="page-kpi" :class="`tone-${metric.tone}`">
        <span class="page-kpi-icon"><el-icon><component :is="metric.icon" /></el-icon></span>
        <div class="page-kpi-copy">
          <span>{{ metric.label }}</span>
          <b>{{ metric.value }}<small>{{ metric.unit }}</small></b>
          <em>{{ metric.hint }}</em>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table :data="reports" style="width:100%" @row-click="openReport">
        <el-table-column label="报告" min-width="260">
          <template #default="{ row }">
            <div class="report-no">{{ row.reportNo }}</div>
            <div class="report-title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="事件" width="160">
          <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span><div class="sub">{{ row.enterpriseName }}</div></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" type="warning" effect="plain">草稿</el-tag></template>
        </el-table-column>
        <el-table-column label="生成人" width="100" prop="createdBy" />
        <el-table-column label="生成时间" width="160" prop="createdAt" />
        <el-table-column label="" width="90" align="right">
          <template #default><el-button link type="primary">打开 <el-icon><ArrowRight /></el-icon></el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="!reports.length" class="empty-block"><el-icon><Document /></el-icon><span>还没有报告，去事件工作台生成第一份核查报告</span></div>
    </div>

    <div class="grid-2 report-insights">
      <section class="card card-pad">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 报告生成链路</h3>
          <span class="soft-pill green">人工审核后使用</span>
        </div>
        <div class="process-strip">
          <div class="process-step">
            <span class="process-index">01</span>
            <b>事件归集</b>
            <span>事件、企业、点位、污染物</span>
          </div>
          <div class="process-step">
            <span class="process-index">02</span>
            <b>规则证据</b>
            <span>触发值、阈值与逐时快照</span>
          </div>
          <div class="process-step">
            <span class="process-index">03</span>
            <b>AI 引用</b>
            <span>条款级引用与核查建议</span>
          </div>
          <div class="process-step">
            <span class="process-index">04</span>
            <b>现场反馈</b>
            <span>照片、水印和处置情况</span>
          </div>
          <div class="process-step">
            <span class="process-index">05</span>
            <b>人工确认</b>
            <span>打印或导出 PDF 后归档</span>
          </div>
        </div>
        <div class="outline-caption">
          <b>报告固定章节</b>
          <span>证据、研判与现场结果同页归档</span>
        </div>
        <div class="report-outline">
          <div class="outline-item"><span>01</span><div><b>企业基本情况</b><em>主体、行业、区域与风险等级</em></div></div>
          <div class="outline-item"><span>02</span><div><b>异常事件</b><em>事件编号、时段、优先级与可信度</em></div></div>
          <div class="outline-item"><span>03</span><div><b>逐时证据链</b><em>触发值、阈值与规则版本快照</em></div></div>
          <div class="outline-item"><span>04</span><div><b>AI 研判与引用</b><em>条款级依据、拒答与不确定性说明</em></div></div>
          <div class="outline-item"><span>05</span><div><b>建议核查内容</b><em>按规则类型生成现场核查提示</em></div></div>
          <div class="outline-item"><span>06</span><div><b>任务与现场反馈</b><em>处置情况、取证照片与闭环时间</em></div></div>
        </div>
        <p class="outline-note">报告按事件证据自动拼装，未产生数据的章节会明确标注，不生成推测性内容。</p>
      </section>

      <section class="card card-pad">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 报告内容与边界</h3>
          <span class="soft-pill amber">辅助生成</span>
        </div>
        <div class="check-list">
          <div class="check-item"><i class="check-dot"></i><div><b>事件摘要与证据链</b><span>保留规则版本、时间区间和逐时证据</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>条款级 AI 引用</b><span>无依据时明确拒答，不生成确定性结论</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>现场取证反馈</b><span>照片附件、水印、发现问题和处置情况</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>人工审核标记</b><span>报告仅作辅助，需工作人员复核后使用</span></div></div>
        </div>
        <p class="report-tip">打开报告后可打印，或在浏览器打印面板中选择“另存为 PDF”。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Clock, Document, Refresh, User, Warning } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const reports = ref([])

const reportStats = computed(() => {
  const reportCount = reports.value.length
  const eventCount = new Set(reports.value.map((item) => item.eventId).filter(Boolean)).size
  const creatorCount = new Set(reports.value.map((item) => item.createdBy).filter(Boolean)).size
  const latest = reports.value[0]?.createdAt || '—'
  return [
    { label: '报告总数', value: reportCount, unit: '份', hint: '事件证据生成', icon: Document, tone: 'navy' },
    { label: '关联事件', value: eventCount, unit: '件', hint: '覆盖异常事件', icon: Warning, tone: 'amber' },
    { label: '生成人', value: creatorCount, unit: '人', hint: '按生成账号统计', icon: User, tone: 'blue' },
    { label: '最近生成', value: String(latest).slice(5, 16) || '—', unit: '', hint: '可打印 / 导出 PDF', icon: Clock, tone: 'green' }
  ]
})

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/reports')
    reports.value = data
  } finally {
    loading.value = false
  }
}

function openReport(row) {
  window.open(`/reports/${row.id}`, '_blank')
}

onMounted(load)
</script>

<style scoped>
.report-no { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; color: var(--teal-dark); }
.report-title { margin-top: 4px; font-size: 12px; font-weight: 700; color: var(--ink); }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.sub { margin-top: 3px; font-size: 9px; color: var(--muted); }
.report-tip { margin: 12px 0 0; font-size: 10px; line-height: 1.75; color: var(--muted); }
.report-insights { margin-top: 18px; }
.report-insights .process-strip { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.outline-caption { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin: 18px 0 9px; }
.outline-caption b { font-size: 11px; color: #34546a; }
.outline-caption span { font-size: 9px; color: var(--muted-2); }
.report-outline { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.outline-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 10px;
  border: 1px solid #edf4f8;
  border-radius: 10px;
  background: #f9fcfe;
}
.outline-item > span {
  width: 23px;
  height: 23px;
  flex-shrink: 0;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eaf9f6;
  color: var(--teal-dark);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  font-weight: 700;
}
.outline-item b { display: block; font-size: 10px; color: #355368; }
.outline-item em { display: block; margin-top: 2px; font-size: 8px; font-style: normal; line-height: 1.5; color: var(--muted); }
.outline-note { margin: 11px 0 0; padding-top: 10px; border-top: 1px dashed #edf3f8; font-size: 9px; line-height: 1.65; color: var(--muted-2); }
@media (max-width: 1100px) {
  .report-insights .process-strip { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 720px) {
  .report-outline { grid-template-columns: 1fr; }
}
:deep(.el-table__row) { cursor: pointer; }
</style>
