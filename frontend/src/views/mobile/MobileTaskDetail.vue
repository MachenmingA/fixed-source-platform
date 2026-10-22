<template>
  <div class="mobile-body">
    <div class="mobile-shell">
      <header class="mobile-top detail-top">
        <div class="detail-nav">
          <button class="back" @click="router.push('/m/tasks')"><el-icon><ArrowLeft /></el-icon></button>
          <span>核查任务详情</span>
          <el-tag v-if="task" :type="statusMeta(task.status).type" size="small">{{ statusMeta(task.status).label }}</el-tag>
        </div>
        <div v-if="task" class="detail-hero">
          <div class="detail-kicker">{{ task.taskNo }}</div>
          <h2>{{ task.title }}</h2>
          <div class="detail-place"><el-icon><Location /></el-icon>{{ task.enterpriseName }} · {{ task.clue?.pointName || '监测点' }}</div>
          <div class="detail-meta">
            <span>截止 {{ task.dueAt }}</span>
            <span>督办 {{ task.urgeCount || 0 }} 次</span>
            <span v-if="task.externalNo">外部单号 {{ task.externalNo }}</span>
          </div>
        </div>
      </header>

      <main class="mobile-content" v-if="task">
        <div class="mobile-flow">
          <div
            v-for="(step, index) in flowSteps"
            :key="step.key"
            class="mobile-flow-step"
            :class="{ done: index < flowIndex, active: index === flowIndex }"
          >
            <span class="flow-dot">{{ index < flowIndex ? '✓' : index + 1 }}</span>
            <b>{{ step.label }}</b>
          </div>
        </div>

        <div class="phone-panel">
          <h4>规则证据</h4>
          <div class="clue-line">
            <el-tag :type="severityMeta(task.clue?.severity).type" size="small">{{ task.clue?.ruleCode }} {{ task.clue?.ruleName }}</el-tag>
            <span class="clue-time">{{ task.clue?.startTime }} — {{ task.clue?.endTime }}</span>
          </div>
          <div class="metric-row">
            <div><span>触发值</span><b>{{ fmtNum(task.clue?.triggerValue) }}</b></div>
            <div><span>阈值</span><b>{{ fmtNum(task.clue?.thresholdValue) }}</b></div>
            <div><span>可信度</span><b>{{ task.clue?.credibility }}</b></div>
          </div>
          <ul class="evidence-list">
            <li v-for="v in (task.clue?.evidence?.values || [])" :key="v">{{ v }}</li>
          </ul>
        </div>

        <div class="phone-panel ai-panel" :class="{ refusal: task.clue?.aiContent?.refusal }">
          <h4><el-icon><MagicStick /></el-icon> AI 辅助研判</h4>
          <template v-if="task.clue?.aiContent">
            <div v-if="task.clue.aiContent.refusal" class="refusal-banner">
              <el-icon><CircleClose /></el-icon> 未检索到可引用条款，AI 已拒答
            </div>
            <p class="ai-text">{{ task.clue.aiContent.explanation }}</p>
            <div class="citations">
              <div v-for="c in task.clue.aiCitations || []" :key="c.id" class="citation">
                <b>{{ c.law }} {{ c.clause }}</b>
                <span>{{ c.title }} · 生效 {{ c.effectiveDate }}</span>
              </div>
            </div>
            <div v-if="(task.clue.aiContent.suggestions || []).length" class="suggestions">
              <b>建议核查</b>
              <ul><li v-for="s in task.clue.aiContent.suggestions" :key="s">{{ s }}</li></ul>
            </div>
            <div class="uncertainty">{{ task.clue.aiContent.uncertainty }}</div>
          </template>
          <div v-else class="muted tiny">暂无 AI 研判</div>
        </div>

        <div v-if="task.status === 'DISPATCHED'" class="phone-panel accept-panel">
          <h4>任务接收</h4>
          <p class="accept-text">接收任务后，请到现场核实治理设施、监测设备和生产工况，并上传带时间与点位水印的照片。</p>
          <el-button type="primary" size="large" class="full-btn" :loading="acting" @click="acceptTask">接收任务并开始核查</el-button>
        </div>

        <div v-if="task.status === 'IN_PROGRESS'" class="phone-panel">
          <h4>提交现场反馈</h4>
          <div class="field-label">核查结果</div>
          <el-radio-group v-model="form.result">
            <el-radio-button value="NORMAL">未发现问题</el-radio-button>
            <el-radio-button value="ISSUE">发现问题</el-radio-button>
          </el-radio-group>
          <div class="field-label">发现问题</div>
          <el-input v-model="form.problem" type="textarea" :rows="3" placeholder="描述现场发现的问题" />
          <div class="field-label">处置情况</div>
          <el-input v-model="form.disposal" type="textarea" :rows="3" placeholder="已采取的措施或整改要求" />
          <div class="field-label">现场取证照片</div>
          <label class="photo-upload">
            <input type="file" accept="image/*" capture="environment" @change="onPhoto" />
            <el-icon><Camera /></el-icon>
            <span>{{ uploading ? '正在上传…' : '拍照 / 选择照片' }}</span>
          </label>
          <div v-if="photoPreview" class="photo-preview">
            <img :src="photoPreview" alt="现场取证" />
            <div class="watermark-note">{{ watermarkText }}</div>
          </div>
          <el-button type="primary" size="large" class="full-btn" :loading="submitting" :disabled="uploading" @click="submitFeedback">
            提交反馈并回写闭环
          </el-button>
        </div>

        <div v-if="task.feedback" class="phone-panel">
          <h4>已提交反馈</h4>
          <div class="feedback-result">
            <el-tag :type="task.feedback.result === 'ISSUE' ? 'danger' : 'success'">{{ statusMeta(task.feedback.result).label }}</el-tag>
            <span>{{ task.feedback.submittedAt }}</span>
          </div>
          <div class="feedback-row"><b>发现问题</b><span>{{ task.feedback.problem || '—' }}</span></div>
          <div class="feedback-row"><b>处置情况</b><span>{{ task.feedback.disposal || '—' }}</span></div>
          <div v-if="task.feedback.attachmentUrl" class="feedback-row"><b>取证附件</b><a :href="task.feedback.attachmentUrl" target="_blank">查看附件</a></div>
          <div v-if="task.feedback.watermark" class="watermark-note">{{ task.feedback.watermark }}</div>
        </div>
      </main>

      <div v-else class="mobile-loading">正在加载任务…</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Camera, CircleClose, Location, MagicStick } from '@element-plus/icons-vue'
import api from '../../api'
import { auth } from '../../store'
import { fmtNum, severityMeta, statusMeta } from '../../utils/labels'

const route = useRoute()
const router = useRouter()
const task = ref(null)
const acting = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const photoPreview = ref('')
const watermarkText = ref('')
const flowSteps = [
  { key: 'dispatch', label: '任务下发' },
  { key: 'accept', label: '现场接收' },
  { key: 'check', label: '核查取证' },
  { key: 'close', label: '反馈回写' }
]
const flowIndex = computed(() => {
  const map = { DISPATCHED: 1, IN_PROGRESS: 2, FEEDBACK: 3, CLOSED: 4 }
  return map[task.value?.status] ?? 0
})
const form = reactive({ result: 'NORMAL', problem: '', disposal: '', attachmentUrl: '', watermark: '' })

async function load() {
  const { data } = await api.get(`/mobile/tasks/${route.params.taskNo}`)
  task.value = data
  if (data.feedback) {
    form.result = data.feedback.result || 'NORMAL'
    form.problem = data.feedback.problem || ''
    form.disposal = data.feedback.disposal || ''
    form.attachmentUrl = data.feedback.attachmentUrl || ''
    watermarkText.value = data.feedback.watermark || ''
  }
}

async function acceptTask() {
  acting.value = true
  try {
    const { data } = await api.post(`/mobile/tasks/${route.params.taskNo}/accept`)
    task.value = data
    ElMessage.success('任务已接收，请开始现场核查')
  } finally {
    acting.value = false
  }
}

async function onPhoto(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const now = new Date()
  const stamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  watermarkText.value = `${stamp} 北京市海淀区（模拟定位） ${task.value?.enterpriseName || ''} ${task.value?.clue?.pointName || ''}`
  let uploadFile = file
  if (file.type.startsWith('image/')) {
    try {
      const bitmap = await createImageBitmap(file)
      const maxWidth = 1280
      const scale = Math.min(1, maxWidth / bitmap.width)
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(bitmap.width * scale)
      canvas.height = Math.round(bitmap.height * scale)
      const ctx = canvas.getContext('2d')
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
      const barHeight = Math.max(66, canvas.height * 0.12)
      const gradient = ctx.createLinearGradient(0, canvas.height - barHeight, 0, canvas.height)
      gradient.addColorStop(0, 'rgba(6, 18, 30, 0)')
      gradient.addColorStop(0.35, 'rgba(6, 18, 30, 0.62)')
      gradient.addColorStop(1, 'rgba(6, 18, 30, 0.82)')
      ctx.fillStyle = gradient
      ctx.fillRect(0, canvas.height - barHeight, canvas.width, barHeight)
      ctx.fillStyle = 'rgba(255,255,255,0.92)'
      ctx.font = `${Math.max(15, Math.round(canvas.width / 64))}px "Microsoft YaHei"`
      ctx.fillText(watermarkText.value, 18, canvas.height - barHeight + 28, canvas.width - 36)
      ctx.fillStyle = 'rgba(127, 240, 226, 0.95)'
      ctx.font = `${Math.max(12, Math.round(canvas.width / 82))}px "Microsoft YaHei"`
      ctx.fillText('净源智研 · 现场取证水印', 18, canvas.height - 18, canvas.width - 36)
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
      photoPreview.value = URL.createObjectURL(blob)
      uploadFile = new File([blob], (file.name || 'photo').replace(/\.[^.]+$/, '') + '_watermark.jpg', { type: 'image/jpeg' })
    } catch (e) {
      photoPreview.value = URL.createObjectURL(file)
    }
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadFile)
    const { data } = await api.post('/files', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    form.attachmentUrl = data.url
    ElMessage.success('取证照片已上传')
  } finally {
    uploading.value = false
  }
}

async function submitFeedback() {
  submitting.value = true
  try {
    const body = {
      inspector: auth.user?.name || '现场核查人员',
      result: form.result,
      problem: form.problem,
      disposal: form.disposal,
      attachmentUrl: form.attachmentUrl,
      watermark: watermarkText.value,
      latitude: 39.983,
      longitude: 116.317
    }
    const { data } = await api.post(`/mobile/tasks/${route.params.taskNo}/feedback`, body)
    task.value = data
    ElMessage.success('反馈已提交，任务已完成回写')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-top { padding-bottom: 20px; }
.mobile-flow {
  display: flex;
  padding: 12px 10px;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 33, 55, 0.035);
}
.mobile-flow-step {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  color: #a0afbc;
  font-size: 8px;
  text-align: center;
}
.mobile-flow-step:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 9px;
  left: calc(50% + 13px);
  right: calc(-50% + 13px);
  height: 1px;
  background: #e5edf3;
}
.mobile-flow-step.done:not(:last-child)::after { background: #a9e4dc; }
.mobile-flow-step b { font-weight: 500; white-space: nowrap; }
.flow-dot {
  width: 19px;
  height: 19px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f5f8;
  color: #94a5b3;
  font-size: 8px;
  font-weight: 700;
  position: relative;
  z-index: 1;
}
.mobile-flow-step.done .flow-dot { background: #e4f8f4; color: #0f8f84; }
.mobile-flow-step.active .flow-dot {
  background: linear-gradient(135deg, #23cdbb, #0a9487);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(15, 185, 166, 0.11);
}
.mobile-flow-step.active { color: var(--teal-dark); font-weight: 700; }
.detail-nav { display: flex; align-items: center; gap: 9px; font-size: 12px; color: rgba(255,255,255,0.75); }
.detail-nav .back { width: 30px; height: 30px; border-radius: 9px; border: 1px solid rgba(255,255,255,0.14); background: rgba(255,255,255,0.06); color: #fff; display: flex; align-items: center; justify-content: center; }
.detail-nav .el-tag { margin-left: auto; }
.detail-hero { margin-top: 18px; }
.detail-kicker { font-size: 9px; letter-spacing: 0.6px; color: #7de8dc; font-family: "Cascadia Code", Consolas, monospace; }
.detail-hero h2 { margin: 8px 0 7px; font-size: 22px; line-height: 1.35; letter-spacing: -0.5px; }
.detail-place { display: flex; align-items: center; gap: 5px; color: rgba(220,238,250,0.62); font-size: 11px; }
.detail-meta { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 13px; padding-top: 10px; border-top: 1px solid rgba(255,255,255,0.08); font-size: 9px; color: rgba(220,238,250,0.45); }
.clue-line { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.clue-time { font-size: 9px; color: var(--muted); }
.metric-row { display: flex; gap: 20px; margin-top: 13px; }
.metric-row div { display: flex; flex-direction: column; gap: 4px; }
.metric-row span { font-size: 9px; color: var(--muted); }
.metric-row b { font-size: 18px; color: var(--ink); }
.evidence-list { margin: 12px 0 0; padding-left: 16px; font-size: 10px; line-height: 1.85; color: #5c6f84; max-height: 170px; overflow: auto; }
.ai-panel h4 { display: flex; align-items: center; gap: 5px; }
.ai-panel.refusal { background: #fffbf5; border-color: #fde9c8; }
.ai-text { margin: 9px 0 0; font-size: 11px; line-height: 1.8; color: #496174; }
.refusal-banner { display: flex; align-items: center; gap: 5px; padding: 8px 9px; border-radius: 8px; background: #fff0dc; color: #b45309; font-size: 10px; margin-top: 8px; }
.citations { margin-top: 10px; display: flex; flex-direction: column; gap: 7px; }
.citation { border-left: 2px solid #0fb9a6; padding-left: 8px; display: flex; flex-direction: column; gap: 3px; }
.citation b { font-size: 10px; color: #24465b; }
.citation span { font-size: 8px; color: #8a99ad; }
.suggestions { margin-top: 10px; }
.suggestions b { font-size: 10px; color: #2c5364; }
.suggestions ul { margin: 4px 0 0; padding-left: 15px; font-size: 9px; line-height: 1.8; color: #5c7286; }
.uncertainty { margin-top: 9px; padding: 6px 8px; border-radius: 6px; background: #fffaf0; font-size: 9px; line-height: 1.6; color: #8b7d61; }
.accept-text { font-size: 11px; line-height: 1.8; color: var(--muted); }
.full-btn { width: 100%; margin-top: 14px; height: 44px; font-weight: 700; }
.photo-upload { display: flex; align-items: center; justify-content: center; gap: 7px; border: 1px dashed rgba(15,185,166,0.45); background: #f2fbf9; color: var(--teal-dark); border-radius: 11px; padding: 14px; font-size: 12px; cursor: pointer; }
.photo-upload input { display: none; }
.photo-preview { margin-top: 10px; border-radius: 11px; overflow: hidden; position: relative; }
.photo-preview img { width: 100%; display: block; }
.watermark-note { font-size: 9px; color: #7d8da2; line-height: 1.6; margin-top: 6px; }
.feedback-result { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.feedback-result span { font-size: 9px; color: var(--muted); }
.feedback-row { display: flex; gap: 10px; padding: 7px 0; border-bottom: 1px dashed #edf3f9; font-size: 10px; line-height: 1.7; }
.feedback-row b { width: 58px; flex-shrink: 0; color: var(--muted); font-weight: 500; }
.feedback-row span, .feedback-row a { color: var(--ink-2); }
</style>
