<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">集成对账</h1>
        <p class="page-sub">任务下发消息、回执、重试与每日对账记录</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :loading="reconciling" @click="reconcile"><el-icon><Connection /></el-icon> 执行对账</el-button>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card"><div class="stat-label">下发消息</div><div class="stat-value">{{ summary.messages || 0 }}<small>条</small></div><div class="stat-hint">outbox 记录</div></div>
      <div class="stat-card"><div class="stat-label">已发送</div><div class="stat-value" style="color:#16a34a">{{ summary.sent || 0 }}<small>条</small></div><div class="stat-hint">状态 SENT</div></div>
      <div class="stat-card"><div class="stat-label">失败</div><div class="stat-value" style="color:#dc2626">{{ summary.failed || 0 }}<small>条</small></div><div class="stat-hint">可人工重发</div></div>
      <div class="stat-card"><div class="stat-label">已重试</div><div class="stat-value" style="color:#d97706">{{ summary.retried || 0 }}<small>条</small></div><div class="stat-hint">retry_count > 0</div></div>
      <div class="stat-card"><div class="stat-label">进行中任务</div><div class="stat-value">{{ summary.openTasks || 0 }}<small>件</small></div><div class="stat-hint">未关闭</div></div>
      <div class="stat-card"><div class="stat-label">超时任务</div><div class="stat-value" style="color:#dc2626">{{ summary.overdueTasks || 0 }}<small>件</small></div><div class="stat-hint">按 SLA 判定</div></div>
    </div>

    <div v-if="reconcileResult" class="card card-pad reconcile-card" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 最近一次对账结果</h3><span class="soft-pill green">{{ reconcileResult.checkedAt }}</span></div>
      <div class="reconcile-grid">
        <div><span>进行中任务</span><b>{{ reconcileResult.openTasks }}</b></div>
        <div><span>超时任务</span><b class="red">{{ reconcileResult.overdueTasks }}</b></div>
        <div><span>已下发待回执</span><b class="amber">{{ reconcileResult.waitingReceipt }}</b></div>
        <div><span>失败消息</span><b class="red">{{ reconcileResult.failedOutbox }}</b></div>
        <div><span>待关闭反馈</span><b>{{ reconcileResult.feedbackTasks }}</b></div>
        <div><span>对账差异</span><b :class="reconcileResult.diff ? 'red' : 'green'">{{ reconcileResult.diff }}</b></div>
      </div>
      <div class="reconcile-note">对账会检查已下发未回执、超时任务和 outbox 差异；差异不等于失败，需要人工核实后重发或关闭。</div>
    </div>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head"><h3 class="card-title"><span class="dot"></span> 下发消息（outbox）</h3><span class="soft-pill">{{ outbox.length }} 条</span></div>
      <el-table :data="outbox" style="width:100%">
        <el-table-column label="消息" min-width="190">
          <template #default="{ row }"><span class="mono">{{ row.messageNo }}</span><div class="sub">{{ row.createdAt }}</div></template>
        </el-table-column>
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }"><b class="strong">{{ row.taskNo }}</b><div class="sub">{{ row.title || '—' }}</div></template>
        </el-table-column>
        <el-table-column label="目标" width="100" prop="target" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag size="small" :type="row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="重试" width="70" align="center" prop="retryCount" />
        <el-table-column label="更新时间" width="160" prop="updatedAt" />
        <el-table-column label="操作" width="90" align="right">
          <template #default="{ row }"><el-button size="small" :disabled="row.status === 'SENT' && row.retryCount > 0" @click="retry(row)">重发</el-button></template>
        </el-table-column>
      </el-table>
      <div v-if="!outbox.length" class="empty-block"><el-icon><Connection /></el-icon><span>暂无下发消息</span></div>
    </div>

    <div class="grid-2 integration-insights">
      <section class="card card-pad">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 任务下发与回执链路</h3>
          <span class="soft-pill green">outbox + receipt</span>
        </div>
        <div class="process-strip">
          <div class="process-step">
            <span class="process-index">01</span>
            <b>线索确认</b>
            <span>审核人员确认线索，生成可下发任务</span>
          </div>
          <div class="process-step">
            <span class="process-index">02</span>
            <b>任务生成</b>
            <span>任务号、执行人、SLA 截止时间固化</span>
          </div>
          <div class="process-step">
            <span class="process-index">03</span>
            <b>Outbox 下发</b>
            <span>写入消息、外部单号、目标移动端</span>
          </div>
          <div class="process-step">
            <span class="process-index">04</span>
            <b>现场回执</b>
            <span>移动端接收、取证、反馈并回写任务</span>
          </div>
          <div class="process-step">
            <span class="process-index">05</span>
            <b>审核关闭</b>
            <span>核对反馈后关闭任务，刷新画像</span>
          </div>
        </div>
        <div class="integration-note">
          <el-icon><InfoFilled /></el-icon>
          <span>原型中的 outbox 和回执均为本地模拟；生产环境应使用消息队列、签名回执和幂等消费。</span>
        </div>
      </section>

      <section class="card card-pad">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 对账检查项</h3>
          <span class="soft-pill">每日可执行</span>
        </div>
        <div class="check-list">
          <div class="check-item"><i class="check-dot"></i><div><b>已下发未回执</b><span>超过 1 小时未接收或未反馈的任务</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>SLA 超时</b><span>按任务 due_at 判断超时并触发督办</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>Outbox 差异</b><span>任务已下发但无 SENT 消息，需要补发或核实</span></div></div>
          <div class="check-item"><i class="check-dot"></i><div><b>待关闭反馈</b><span>现场已提交反馈，等待审核人员关闭</span></div></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, InfoFilled, Refresh } from '@element-plus/icons-vue'
import api from '../api'

const loading = ref(false)
const reconciling = ref(false)
const summary = ref({})
const outbox = ref([])
const reconcileResult = ref(null)

async function load() {
  loading.value = true
  try {
    const [s, o] = await Promise.all([api.get('/integration/summary'), api.get('/integration/outbox', { params: { limit: 100 } })])
    summary.value = s.data
    outbox.value = o.data
  } finally {
    loading.value = false
  }
}

async function reconcile() {
  reconciling.value = true
  try {
    const { data } = await api.post('/integration/reconcile')
    reconcileResult.value = data
    ElMessage.success('对账完成')
    await load()
  } finally {
    reconciling.value = false
  }
}

async function retry(row) {
  await api.post(`/integration/outbox/${row.id}/retry`)
  ElMessage.success('已重发并更新重试次数')
  await load()
}

onMounted(load)
</script>

<style scoped>
.strong { font-size: 11px; color: var(--ink); }
.sub { margin-top: 3px; font-size: 9px; color: var(--muted); }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.reconcile-card { background: radial-gradient(circle at 100% 0%, rgba(15,185,166,0.08), transparent 42%), #fff; }
.reconcile-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; }
.reconcile-grid div { background: #f9fcfe; border: 1px solid #eef5fa; border-radius: 10px; padding: 11px; }
.reconcile-grid span { display: block; font-size: 10px; color: var(--muted); }
.reconcile-grid b { display: inline-block; margin-top: 5px; font-size: 22px; line-height: 1; color: var(--ink); }
.reconcile-grid b.red { color: #dc2626; }
.reconcile-grid b.green { color: #16a34a; }
.reconcile-grid b.amber { color: #d97706; }
.reconcile-note { margin-top: 11px; padding: 9px 11px; border-radius: 9px; background: #f3fbf9; font-size: 10px; line-height: 1.7; color: #4c7b74; }
.integration-insights { margin-top: 18px; }
.integration-note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 12px;
  padding: 9px 10px;
  border-radius: 8px;
  background: #f5fafb;
  color: #859ba7;
  font-size: 9px;
  line-height: 1.65;
}
.integration-note .el-icon { color: #24a996; margin-top: 1px; }
@media (max-width: 1100px) { .reconcile-grid { grid-template-columns: repeat(3, 1fr); } }
</style>
