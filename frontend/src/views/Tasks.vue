<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">核查任务</h1>
        <p class="page-sub">任务下发、现场反馈、SLA 督办和闭环审核</p>
      </div>
      <div class="page-actions">
        <div v-if="stats.overdue" class="soft-pill red">超时 {{ stats.overdue }}</div>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="task-flow">
      <div class="flow-head">
        <div>
          <span class="flow-kicker">FIELD CLOSURE</span>
          <b>核查任务闭环链路</b>
        </div>
        <span class="flow-note">下发 → 接收 → 现场取证 → 反馈 → 审核关闭</span>
      </div>
      <div class="flow-steps">
        <div v-for="(step, index) in flowSteps" :key="step.label" class="flow-step" :class="`tone-${step.tone}`">
          <span class="flow-no">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="flow-icon"><el-icon><component :is="step.icon" /></el-icon></span>
          <div class="flow-copy">
            <span>{{ step.label }}</span>
            <b>{{ step.value }}<small>件</small></b>
            <em>{{ step.hint }}</em>
          </div>
        </div>
      </div>
    </div>

    <div class="card card-pad" style="margin-bottom:18px">
      <div class="filter-bar" style="margin-bottom:0">
        <el-input v-model="filters.keyword" placeholder="搜索任务号 / 企业 / 标题" clearable :prefix-icon="Search" @keyup.enter="load" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable>
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="grow"></div>
        <span class="tiny muted">移动端任务由现场核查人员接收</span>
      </div>
    </div>

    <div class="card">
      <el-table :data="tasks" v-loading="loading" style="width:100%">
        <el-table-column label="任务" min-width="280">
          <template #default="{ row }">
            <div class="task-cell">
              <div class="task-icon" :class="statusClass(row.status)"><el-icon><Tickets /></el-icon></div>
              <div>
                <div class="task-title">{{ row.title }}</div>
                <div class="task-sub"><span class="mono">{{ row.taskNo }}</span> · {{ row.enterpriseName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="执行人" width="110" prop="assignee" />
        <el-table-column label="截止时间" width="150">
          <template #default="{ row }">
            <div class="due" :class="{ overdue: row.slaStatus === 'OVERDUE' }">
              {{ row.dueAt || '—' }}
              <span v-if="row.slaStatus === 'OVERDUE'">已超时</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="督办" width="70" align="center">
          <template #default="{ row }"><span class="urge">{{ row.urgeCount || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING_DISPATCH'" size="small" type="success" @click="dispatchTask(row)">下发</el-button>
            <el-button v-if="['DISPATCHED','IN_PROGRESS'].includes(row.status)" size="small" @click="urgeTask(row)">督办</el-button>
            <el-button v-if="['DISPATCHED','IN_PROGRESS','FEEDBACK'].includes(row.status)" size="small" type="primary" plain @click="openFeedback(row)">反馈</el-button>
            <el-button v-if="row.status === 'FEEDBACK'" size="small" type="success" plain @click="closeTask(row)">关闭</el-button>
            <el-button size="small" link @click="openFeedback(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !tasks.length" class="empty-block"><el-icon><Tickets /></el-icon><span>暂无核查任务</span></div>
    </div>

    <div class="task-secondary">
      <section class="card card-pad mobile-bridge-card">
        <div class="bridge-copy">
          <span class="bridge-kicker">FIELD EXECUTION</span>
          <h3>现场执行端</h3>
          <p>现场核查人员可通过移动 H5 或 Android 现场端接收任务、拍摄带时间地点水印的照片，并将处置结果回写闭环。</p>
          <div class="bridge-stats">
            <div><b>{{ stats.open }}</b><span>现场执行</span></div>
            <div><b>{{ stats.feedback }}</b><span>待审核</span></div>
            <div><b :class="{ danger: stats.overdue }">{{ stats.overdue }}</b><span>超时</span></div>
          </div>
          <div class="bridge-actions">
            <el-button type="primary" @click="openMobile"><el-icon><Iphone /></el-icon> 打开移动 H5</el-button>
            <a class="apk-link" href="/downloads/fixed-source-mobile-debug.apk" target="_blank">下载 Android APK</a>
          </div>
        </div>
        <div class="phone-mock">
          <div class="phone-screen">
            <span class="phone-notch"></span>
            <div class="phone-brand">净源智研 <span>现场核查</span></div>
            <div v-for="task in tasks.slice(0, 2)" :key="task.id" class="phone-task">
              <b>{{ task.title }}</b>
              <span>{{ task.enterpriseName }}</span>
              <em>{{ statusMeta(task.status).label }} · {{ task.taskNo }}</em>
            </div>
            <div v-if="!tasks.length" class="phone-empty">暂无任务</div>
          </div>
        </div>
      </section>

      <section class="card card-pad closure-overview">
        <div class="card-head">
          <h3 class="card-title"><span class="dot"></span> 任务闭环质量</h3>
          <span class="soft-pill green">{{ stats.closed }} 件已关闭</span>
        </div>
        <div class="closure-main">
          <div class="closure-ring" :style="closureRingStyle">
            <div><strong>{{ closureRate }}<small>%</small></strong><span>闭环率</span></div>
          </div>
          <div class="closure-stats">
            <div v-for="step in flowSteps" :key="step.label">
              <span>{{ step.label }}</span>
              <b>{{ step.value }}</b>
              <i><em :style="{ width: `${taskCount ? Math.round(step.value * 100 / taskCount) : 0}%` }"></em></i>
            </div>
          </div>
        </div>
        <p class="closure-note">闭环率按“已关闭任务 / 全部任务”计算；超时任务应优先督办，现场反馈后需及时审核关闭。</p>
      </section>
    </div>

    <el-dialog v-model="feedbackVisible" title="现场核查反馈" width="560px" destroy-on-close>
      <div v-if="activeTask" class="feedback-head">
        <div>
          <div class="feedback-title">{{ activeTask.title }}</div>
          <div class="feedback-sub">{{ activeTask.taskNo }} · {{ activeTask.enterpriseName }}</div>
        </div>
        <el-tag :type="statusMeta(activeTask.status).type">{{ statusMeta(activeTask.status).label }}</el-tag>
      </div>
      <el-form label-position="top">
        <el-form-item label="核查结果">
          <el-radio-group v-model="feedback.result">
            <el-radio-button value="NORMAL">未发现问题</el-radio-button>
            <el-radio-button value="ISSUE">发现问题</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发现问题">
          <el-input v-model="feedback.problem" type="textarea" :rows="3" placeholder="描述现场发现的问题、异常设备或记录缺失等" />
        </el-form-item>
        <el-form-item label="处置情况">
          <el-input v-model="feedback.disposal" type="textarea" :rows="3" placeholder="已采取的措施、整改要求或下一步安排" />
        </el-form-item>
        <el-form-item label="现场照片 / 附件">
          <el-upload
            action="/api/files"
            :headers="uploadHeaders"
            :show-file-list="true"
            :limit="1"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
          >
            <el-button size="small"><el-icon><Upload /></el-icon> 上传附件</el-button>
            <template #tip><div class="el-upload__tip">支持 jpg/png/webp/pdf，附件会记录文件标识与校验信息</div></template>
          </el-upload>
          <div v-if="feedback.attachmentUrl" class="uploaded">已上传：{{ feedback.attachmentUrl }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button v-if="activeTask?.status !== 'CLOSED'" type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatLineRound, CircleCheck, Iphone, Refresh, Search, Tickets, Upload, VideoPlay } from '@element-plus/icons-vue'
import api from '../api'
import { auth, hasRole } from '../store'
import { statusMeta } from '../utils/labels'

const loading = ref(false)
const tasks = ref([])
const feedbackVisible = ref(false)
const submitting = ref(false)
const activeTask = ref(null)
const filters = reactive({ keyword: '', status: '' })
const feedback = reactive({ result: 'NORMAL', problem: '', disposal: '', attachmentUrl: '', watermark: '' })
const uploadHeaders = computed(() => ({ 'X-Token': auth.token }))

const statusOptions = [
  { label: '待下发', value: 'PENDING_DISPATCH' },
  { label: '已下发', value: 'DISPATCHED' },
  { label: '执行中', value: 'IN_PROGRESS' },
  { label: '已反馈', value: 'FEEDBACK' },
  { label: '已关闭', value: 'CLOSED' }
]

const stats = computed(() => ({
  pending: tasks.value.filter((x) => x.status === 'PENDING_DISPATCH').length,
  open: tasks.value.filter((x) => ['DISPATCHED', 'IN_PROGRESS'].includes(x.status)).length,
  feedback: tasks.value.filter((x) => x.status === 'FEEDBACK').length,
  closed: tasks.value.filter((x) => x.status === 'CLOSED').length,
  overdue: tasks.value.filter((x) => x.slaStatus === 'OVERDUE').length
}))

const flowSteps = computed(() => [
  { label: '待下发', value: stats.value.pending, hint: '已完成研判，等待派单', icon: Tickets, tone: 'amber' },
  { label: '现场执行', value: stats.value.open, hint: '已下发 / 现场接收', icon: VideoPlay, tone: 'blue' },
  { label: '待审核反馈', value: stats.value.feedback, hint: '现场已回传，等待关闭', icon: ChatLineRound, tone: 'green' },
  { label: '已闭环', value: stats.value.closed, hint: '审核关闭并回写画像', icon: CircleCheck, tone: 'navy' }
])

const taskCount = computed(() => tasks.value.length)
const closureRate = computed(() => taskCount.value ? Math.round(stats.value.closed * 100 / taskCount.value) : 0)
const closureRingStyle = computed(() => ({
  background: `conic-gradient(#22a98e ${closureRate.value}%, #eaf2f4 ${closureRate.value}% 100%)`
}))

function openMobile() {
  window.open('/m/tasks', '_blank')
}

function statusClass(status) {
  if (status === 'FEEDBACK') return 'green'
  if (status === 'PENDING_DISPATCH') return 'amber'
  if (status === 'CLOSED') return 'gray'
  return ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/inspection-tasks', { params: filters })
    tasks.value = data
  } finally {
    loading.value = false
  }
}

async function dispatchTask(row) {
  await api.post(`/inspection-tasks/${row.id}/dispatch`)
  ElMessage.success('任务已下发到移动端')
  await load()
}

async function urgeTask(row) {
  await api.post(`/inspection-tasks/${row.id}/urge`)
  ElMessage.success('督办已发送')
  await load()
}

async function openFeedback(row) {
  const { data } = await api.get(`/inspection-tasks/${row.id}`)
  activeTask.value = data
  const existing = data.feedback || {}
  feedback.result = existing.result || 'NORMAL'
  feedback.problem = existing.problem || ''
  feedback.disposal = existing.disposal || ''
  feedback.attachmentUrl = existing.attachmentUrl || ''
  feedbackVisible.value = true
}

function onUploadSuccess(response) {
  feedback.attachmentUrl = response.url
  ElMessage.success('附件上传成功')
}

function onUploadError() {
  ElMessage.error('附件上传失败')
}

async function submitFeedback() {
  if (!activeTask.value) return
  submitting.value = true
  try {
    const now = new Date()
    feedback.watermark = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')} · 北京市海淀区（模拟定位）`
    await api.post(`/inspection-tasks/${activeTask.value.id}/feedback`, feedback)
    ElMessage.success('反馈已提交')
    feedbackVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function closeTask(row) {
  await ElMessageBox.confirm('确认关闭该核查任务？关闭后企业画像与闭环统计会同步更新。', '关闭任务', { type: 'warning' })
  await api.post(`/inspection-tasks/${row.id}/close`)
  ElMessage.success('任务已关闭')
  await load()
}

onMounted(load)
</script>

<style scoped>
.task-cell { display: flex; align-items: center; gap: 11px; }
.task-icon { width: 36px; height: 36px; border-radius: 10px; background: #edf5ff; color: #3b82f6; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.task-icon.green { background: #eafaf5; color: #0f9f8a; }
.task-icon.amber { background: #fff7e8; color: #d97706; }
.task-icon.gray { background: #f2f5f9; color: #8494a8; }
.task-title { font-size: 12px; font-weight: 700; color: var(--ink); }
.task-sub { margin-top: 4px; font-size: 10px; color: var(--muted); }
.due { font-size: 11px; color: var(--ink-2); display: flex; flex-direction: column; gap: 2px; }
.due.overdue { color: #dc2626; font-weight: 700; }
.due span { font-size: 9px; color: #dc2626; }
.urge { font-size: 12px; color: var(--muted); }
.feedback-head { display: flex; justify-content: space-between; gap: 10px; align-items: flex-start; margin-bottom: 16px; padding: 12px; background: #f8fbfe; border-radius: 10px; }
.feedback-title { font-size: 13px; font-weight: 700; }
.feedback-sub { margin-top: 4px; font-size: 10px; color: var(--muted); }
.uploaded { margin-top: 5px; font-size: 10px; color: var(--teal-dark); word-break: break-all; }

.task-flow {
  position: relative;
  margin-bottom: 16px;
  padding: 16px 20px 17px;
  border: 1px solid #e2ecf1;
  border-radius: 17px;
  background:
    radial-gradient(circle at 100% 0, rgba(15, 185, 166, 0.06), transparent 36%),
    linear-gradient(180deg, #ffffff, #f8fcfd);
  box-shadow: 0 8px 22px rgba(15, 33, 55, 0.04);
}
.flow-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.flow-kicker { display: block; color: #96a9b5; font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; letter-spacing: 1.3px; }
.flow-head b { display: block; margin-top: 3px; color: var(--ink); font-size: 13px; }
.flow-note { color: #8fa4af; font-size: 9px; }
.flow-steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.flow-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 64px;
  padding: 10px 11px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #edf3f6;
}
.flow-step + .flow-step::before {
  content: "";
  position: absolute;
  left: -9px;
  top: 50%;
  width: 8px;
  height: 1px;
  background: #cddfe5;
}
.flow-step + .flow-step::after {
  content: "";
  position: absolute;
  left: -6px;
  top: calc(50% - 2px);
  width: 4px;
  height: 4px;
  border-right: 1px solid #9eb8c2;
  border-top: 1px solid #9eb8c2;
  transform: rotate(45deg);
}
.flow-no { color: #b4c2cb; font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; position: absolute; right: 8px; top: 7px; }
.flow-icon { width: 28px; height: 28px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 13px; flex-shrink: 0; }
.flow-step.tone-amber .flow-icon { background: linear-gradient(135deg, #f0c66a, #d79d35); }
.flow-step.tone-blue .flow-icon { background: linear-gradient(135deg, #6ba5db, #3379b7); }
.flow-step.tone-green .flow-icon { background: linear-gradient(135deg, #61c393, #2f9469); }
.flow-step.tone-navy .flow-icon { background: linear-gradient(135deg, #507e9a, #1f4c67); }
.flow-copy { min-width: 0; }
.flow-copy span { display: block; color: #8298a5; font-size: 8px; }
.flow-copy b { display: block; margin-top: 1px; color: var(--ink); font-size: 17px; line-height: 1.1; }
.flow-copy b small { margin-left: 1px; font-size: 8px; color: #94a7b2; font-weight: 400; }
.flow-copy em { display: block; margin-top: 3px; color: #a0b0b9; font-size: 7.5px; font-style: normal; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 900px) {
  .flow-steps { grid-template-columns: 1fr 1fr; }
  .flow-step + .flow-step::before, .flow-step + .flow-step::after { display: none; }
}

.task-secondary {
  display: grid;
  grid-template-columns: 1.28fr 0.72fr;
  gap: 16px;
  margin-top: 16px;
}
.mobile-bridge-card { display: flex; gap: 22px; align-items: center; overflow: hidden; }
.bridge-copy { flex: 1; min-width: 0; }
.bridge-kicker { display: block; color: #96a9b5; font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; letter-spacing: 1.4px; }
.bridge-copy h3 { margin: 5px 0 7px; color: var(--ink); font-size: 18px; }
.bridge-copy p { margin: 0; max-width: 510px; color: #7f95a2; font-size: 10px; line-height: 1.8; }
.bridge-stats { display: flex; gap: 22px; margin-top: 14px; }
.bridge-stats div { display: flex; flex-direction: column; gap: 2px; }
.bridge-stats b { color: var(--ink); font-size: 20px; line-height: 1; }
.bridge-stats b.danger { color: #d85a55; }
.bridge-stats span { color: #94a7b2; font-size: 8px; }
.bridge-actions { display: flex; align-items: center; gap: 12px; margin-top: 15px; flex-wrap: wrap; }
.apk-link { color: #168f7d; font-size: 10px; font-weight: 600; }
.apk-link:hover { text-decoration: underline; }
.phone-mock {
  width: 176px;
  height: 236px;
  padding: 8px;
  border-radius: 24px;
  background: linear-gradient(160deg, #102d3f, #071a28);
  box-shadow: 0 16px 30px rgba(11, 32, 47, 0.2);
  transform: rotate(2deg);
  flex-shrink: 0;
}
.phone-screen {
  position: relative;
  height: 100%;
  border-radius: 18px;
  background: linear-gradient(180deg, #edf8f6, #f8fbfc 40%);
  padding: 27px 10px 10px;
  overflow: hidden;
}
.phone-notch {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: 50px;
  height: 5px;
  border-radius: 5px;
  background: #17384d;
  opacity: 0.7;
}
.phone-brand { display: flex; align-items: center; justify-content: space-between; color: #17384d; font-size: 8px; font-weight: 700; padding: 0 2px 8px; }
.phone-brand span { color: #2b9b88; font-size: 7px; font-weight: 500; }
.phone-task { margin-bottom: 6px; padding: 7px; border-radius: 8px; background: #fff; border: 1px solid #e4f0f0; box-shadow: 0 3px 8px rgba(15, 33, 55, 0.04); }
.phone-task b { display: block; color: #28495e; font-size: 7px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.phone-task span { display: block; margin-top: 2px; color: #8ba0ad; font-size: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.phone-task em { display: block; margin-top: 3px; color: #168f7d; font-size: 6px; font-style: normal; }
.phone-empty { padding: 22px 0; text-align: center; color: #9bafb9; font-size: 8px; }
.closure-overview { min-width: 0; }
.closure-main { display: flex; align-items: center; gap: 20px; padding: 4px 0 2px; }
.closure-ring {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  padding: 9px;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(226, 237, 242, 0.9), 0 10px 22px rgba(15, 33, 55, 0.07);
}
.closure-ring > div { width: 100%; height: 100%; border-radius: 50%; background: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.closure-ring strong { color: var(--ink); font-size: 25px; line-height: 1; }
.closure-ring strong small { color: #8097a4; font-size: 8px; }
.closure-ring span { margin-top: 4px; color: #94a7b2; font-size: 8px; }
.closure-stats { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.closure-stats > div { display: grid; grid-template-columns: 64px 20px 1fr; align-items: center; gap: 6px; }
.closure-stats span { color: #8298a5; font-size: 8px; }
.closure-stats b { color: var(--ink-2); font-size: 10px; text-align: right; }
.closure-stats i { display: block; height: 4px; border-radius: 3px; background: #eef4f6; overflow: hidden; }
.closure-stats em { display: block; height: 100%; border-radius: 3px; background: linear-gradient(90deg, #9ce0d5, #168f7d); }
.closure-note { margin: 12px 0 0; padding-top: 10px; border-top: 1px dashed #edf3f6; color: #9aadb7; font-size: 8px; line-height: 1.65; }
@media (max-width: 1100px) {
  .task-secondary { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .mobile-bridge-card { align-items: flex-start; flex-direction: column; }
  .phone-mock { display: none; }
}
</style>
