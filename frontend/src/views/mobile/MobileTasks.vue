<template>
  <div class="mobile-body">
    <div class="mobile-shell">
      <header class="mobile-top">
        <div class="mobile-top-row">
          <div>
            <div class="mobile-brand">净源智研 · 现场核查</div>
            <div class="mobile-hello">{{ auth.user?.name || '现场人员' }}，今天有 {{ tasks.length }} 个任务</div>
          </div>
          <div class="mobile-avatar">{{ auth.user?.name?.slice(0, 1) || '现' }}</div>
        </div>
        <div class="mobile-stats">
          <div><b>{{ pending }}</b><span>待接收</span></div>
          <div><b>{{ doing }}</b><span>执行中</span></div>
          <div><b>{{ feedback }}</b><span>已反馈</span></div>
        </div>
      </header>

      <main class="mobile-content">
        <div class="mobile-section-head">
          <h3>我的核查任务</h3>
          <span class="soft-pill green">SLA 24h</span>
        </div>

        <div v-if="loading" class="mobile-loading">正在加载任务…</div>
        <div v-else-if="!tasks.length" class="empty-block">
          <el-icon><Tickets /></el-icon><span>暂无待执行任务</span>
        </div>

        <div v-for="task in tasks" :key="task.id" class="task-card" :class="`sev-${String(task.severity).toLowerCase()}`" @click="open(task)">
          <div class="task-top">
            <el-tag :type="statusMeta(task.status).type" size="small">{{ statusMeta(task.status).label }}</el-tag>
            <span class="task-no">{{ task.taskNo }}</span>
            <el-tag v-if="task.slaStatus === 'OVERDUE'" type="danger" size="small" effect="dark">超时</el-tag>
          </div>
          <h4>{{ task.title }}</h4>
          <div class="task-place"><el-icon><Location /></el-icon>{{ task.enterpriseName }} · {{ task.pointName }}</div>
          <div class="task-tags">
            <span class="mini-tag" :class="task.severity === 'HIGH' ? 'red' : task.severity === 'MEDIUM' ? 'amber' : ''">
              {{ severityMeta(task.severity).label }}风险
            </span>
            <span class="mini-tag">{{ task.ruleName }}</span>
            <span class="mini-tag">可信度 {{ task.credibility }}</span>
          </div>
          <div class="task-meta">
            <span><el-icon><Clock /></el-icon> 截止 {{ task.dueAt }}</span>
            <span v-if="task.urgeCount"><el-icon><Bell /></el-icon> 督办 {{ task.urgeCount }} 次</span>
            <span class="go">进入 <el-icon><ArrowRight /></el-icon></span>
          </div>
        </div>

        <div class="mobile-note">
          <el-icon><InfoFilled /></el-icon>
          <span>移动 H5 模拟执行端；Android 现场端已复用同一套接口，可在登录页下载 APK。</span>
        </div>

        <div class="mobile-guide">
          <div class="guide-head">
            <b>现场核查提示</b>
            <span>FIELD GUIDE</span>
          </div>
          <div class="guide-item">
            <i>01</i>
            <div><b>到达点位再拍照</b><span>照片自动叠加时间、企业与点位水印，作为反馈附件。</span></div>
          </div>
          <div class="guide-item">
            <i>02</i>
            <div><b>先核实设备与工况</b><span>对照规则证据检查设备量程、校准记录和生产负荷。</span></div>
          </div>
          <div class="guide-item">
            <i>03</i>
            <div><b>发现问题同步处置</b><span>填写问题与整改要求，提交后回写 PC 端形成闭环。</span></div>
          </div>
        </div>
      </main>

      <nav class="mobile-nav">
        <router-link to="/m/tasks" class="active"><el-icon><Tickets /></el-icon><span>任务</span></router-link>
        <a href="/dashboard"><el-icon><Monitor /></el-icon><span>返回 PC</span></a>
        <a href="/login"><el-icon><SwitchButton /></el-icon><span>切换账号</span></a>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, Clock, InfoFilled, Location, Monitor, SwitchButton, Tickets } from '@element-plus/icons-vue'
import api from '../../api'
import { auth } from '../../store'
import { severityMeta, statusMeta } from '../../utils/labels'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])

const pending = computed(() => tasks.value.filter((x) => x.status === 'DISPATCHED').length)
const doing = computed(() => tasks.value.filter((x) => x.status === 'IN_PROGRESS').length)
const feedback = computed(() => tasks.value.filter((x) => x.status === 'FEEDBACK').length)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/mobile/tasks')
    tasks.value = data
  } finally {
    loading.value = false
  }
}

function open(task) {
  router.push(`/m/tasks/${task.taskNo}`)
}

onMounted(load)
</script>

<style scoped>
.mobile-top-row { display: flex; align-items: center; justify-content: space-between; }
.mobile-brand { font-size: 11px; letter-spacing: 1px; color: #8ef0e3; font-weight: 700; }
.mobile-hello { margin-top: 8px; font-size: 21px; font-weight: 800; letter-spacing: -0.4px; }
.mobile-avatar { width: 42px; height: 42px; border-radius: 13px; background: rgba(255,255,255,0.13); display: flex; align-items: center; justify-content: center; font-size: 16px; font-weight: 700; border: 1px solid rgba(255,255,255,0.12); }
.mobile-stats { display: flex; gap: 26px; margin-top: 20px; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.09); }
.mobile-stats div { display: flex; flex-direction: column; gap: 3px; }
.mobile-stats b { font-size: 22px; line-height: 1; }
.mobile-stats span { font-size: 9px; color: rgba(222,238,250,0.55); }
.mobile-section-head { display: flex; justify-content: space-between; align-items: center; margin: 2px 2px 12px; }
.mobile-section-head h3 { margin: 0; font-size: 15px; }
.mobile-loading { text-align: center; color: var(--muted); font-size: 12px; padding: 36px 0; }
.task-top { display: flex; align-items: center; gap: 7px; }
.task-no { font-size: 9px; color: var(--muted); font-family: "Cascadia Code", Consolas, monospace; flex: 1; overflow: hidden; text-overflow: ellipsis; }
.task-place { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 10px; margin-top: 2px; }
.task-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.mini-tag { font-size: 9px; padding: 3px 7px; border-radius: 999px; background: #f3f7fb; color: #70829a; }
.mini-tag.red { background: #fff0f0; color: #c53030; }
.mini-tag.amber { background: #fff7e8; color: #b7791f; }
.task-meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 12px; padding-top: 10px; border-top: 1px dashed #edf3f9; color: var(--muted); font-size: 9px; }
.task-meta span { display: inline-flex; align-items: center; gap: 4px; }
.task-meta .go { margin-left: auto; color: var(--teal-dark); font-weight: 600; }
.mobile-note { display: flex; gap: 7px; align-items: flex-start; padding: 12px 13px; border-radius: 12px; background: #edf9f6; color: #4c7b74; font-size: 10px; line-height: 1.7; margin-top: 6px; }
.mobile-note .el-icon { margin-top: 2px; }
.mobile-guide {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.82);
}
.guide-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.guide-head b { font-size: 12px; color: var(--ink); }
.guide-head span { font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; letter-spacing: 1.3px; color: var(--muted-2); }
.guide-item { display: flex; align-items: flex-start; gap: 9px; padding: 8px 0; border-top: 1px dashed #eef4f8; }
.guide-item i {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eefaf7;
  color: var(--teal-dark);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  font-style: normal;
  font-weight: 700;
}
.guide-item b { display: block; font-size: 10px; color: #355368; }
.guide-item span { display: block; margin-top: 2px; font-size: 9px; line-height: 1.6; color: var(--muted); }
</style>
