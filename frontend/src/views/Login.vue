<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-top">
        <div class="mark">净</div>
        <div>
          <div class="name">净源智研</div>
          <div class="en">FIXED SOURCE INTELLIGENCE</div>
        </div>
      </div>

      <div class="brand-copy">
        <div class="eyebrow"><span></span> 固定污染源 · 智能监管原型</div>
        <h1>让每一条监测异常<br /><em>都有证据、有依据、有闭环</em></h1>
        <p>每日采集、规则识别、事件聚合、AI 辅助研判，再把核查任务送到手机端执行。不是一张报表，而是一条可追溯的监管链路。</p>
        <div class="feature-list">
          <div class="feature">
            <div class="feature-icon">01</div>
            <div><b>事件化研判</b><span>同源线索聚合，风险分级与可信度排序</span></div>
          </div>
          <div class="feature">
            <div class="feature-icon">02</div>
            <div><b>条款级引用</b><span>AI 结论必须落到法规条款，无依据即拒答</span></div>
          </div>
          <div class="feature">
            <div class="feature-icon">03</div>
            <div><b>移动端闭环</b><span>任务下发、现场取证、反馈回写与督办</span></div>
          </div>
        </div>
      </div>

      <div class="brand-foot">
        <span>模拟数据演示环境</span>
        <span>Spring Boot · Vue 3 · Docker</span>
      </div>
    </div>

    <div class="login-panel">
      <div class="login-card">
        <div class="login-mobile-brand">
          <div class="mark">净</div>
          <div>
            <div class="name">净源智研</div>
            <div class="en">FIXED SOURCE INTELLIGENCE</div>
          </div>
          <span class="mobile-env">DEMO</span>
        </div>
        <div class="login-head">
          <div class="login-kicker">监管工作台登录</div>
          <h2>欢迎回来</h2>
          <p>选择演示角色，或使用账号密码登录</p>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" size="large" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="演示密码：123456" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
            进入监管工作台
          </el-button>
        </el-form>

        <div class="divider"><span>快速演示角色</span></div>
        <div class="role-grid">
          <button v-for="role in roles" :key="role.username" class="role-card" @click="quickLogin(role)">
            <div class="role-avatar" :style="{ background: role.color }">{{ role.short }}</div>
            <div class="role-copy">
              <b>{{ role.label }}</b>
              <span>{{ role.desc }}</span>
            </div>
          </button>
        </div>

        <button class="mobile-entry" @click="mobileLogin">
          <el-icon><Iphone /></el-icon>
          <span>进入移动 H5 核查端</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </button>

        <a class="mobile-entry android-entry" href="/downloads/fixed-source-mobile-debug.apk" download>
          <el-icon><Download /></el-icon>
          <span>下载 Android 现场核查端 APK</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </a>

        <div class="login-tip">
          <el-icon><InfoFilled /></el-icon>
          本原型只使用合成数据和公开法规，不接入真实企业生产数据。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Download, Iphone, Lock, User, InfoFilled } from '@element-plus/icons-vue'
import api from '../api'
import { setAuth } from '../store'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'analyst', password: '123456' })

const roles = [
  { username: 'analyst', password: '123456', label: '监测分析人员', desc: '画像 · 证据 · AI 研判', short: '析', color: 'linear-gradient(135deg,#2563eb,#60a5fa)' },
  { username: 'reviewer', password: '123456', label: '审核人员', desc: '确认线索 · 下发 · 闭环', short: '审', color: 'linear-gradient(135deg,#0f8f84,#2dd4bf)' },
  { username: 'inspector', password: '123456', label: '现场核查人员', desc: '接单 · 取证 · 反馈', short: '查', color: 'linear-gradient(135deg,#b7791f,#f6c453)' },
  { username: 'admin', password: '123456', label: '系统管理员', desc: '规则 · 数据源 · 权限', short: '管', color: 'linear-gradient(135deg,#475569,#94a3b8)' }
]

async function doLogin(username, password, target) {
  loading.value = true
  try {
    const { data } = await api.post('/auth/login', { username, password })
    setAuth(data)
    ElMessage.success(`欢迎，${data.name}`)
    router.replace(target || route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}

function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  doLogin(form.username, form.password)
}

function quickLogin(role) {
  form.username = role.username
  form.password = role.password
  doLogin(role.username, role.password, role.username === 'inspector' ? '/m/tasks' : '/dashboard')
}

function mobileLogin() {
  doLogin('inspector', '123456', '/m/tasks')
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(440px, 0.92fr);
  background: #f4f8fc;
}

.login-brand {
  position: relative;
  overflow: hidden;
  padding: 40px 60px;
  color: #fff;
  background:
    radial-gradient(circle at 75% 15%, rgba(45, 212, 191, 0.24), transparent 30%),
    radial-gradient(circle at 5% 85%, rgba(59, 130, 246, 0.18), transparent 35%),
    linear-gradient(135deg, #081726 0%, #0d2b43 56%, #0a4d4b 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.login-brand::before {
  content: "";
  position: absolute;
  width: 540px;
  height: 540px;
  border: 80px solid rgba(45, 212, 191, 0.08);
  border-radius: 50%;
  right: -260px;
  top: -250px;
}

.login-brand::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.55), transparent 75%);
}

.brand-top,
.brand-copy,
.brand-foot {
  position: relative;
  z-index: 1;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #23d3be, #0b8b81);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(15, 185, 166, 0.3);
}

.name {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 1.6px;
}

.en {
  margin-top: 3px;
  font-size: 8px;
  letter-spacing: 1.8px;
  color: rgba(209, 232, 248, 0.48);
}

.brand-copy {
  max-width: 600px;
  padding: 38px 0;
}

.eyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
  letter-spacing: 1.3px;
  color: #8ef0e3;
  font-weight: 700;
}

.eyebrow span {
  width: 22px;
  height: 1px;
  background: #2dd4bf;
}

h1 {
  margin: 20px 0 18px;
  font-size: clamp(34px, 3.2vw, 50px);
  line-height: 1.28;
  letter-spacing: -1.4px;
  font-weight: 800;
}

h1 em {
  font-style: normal;
  color: #61e7d7;
  text-shadow: 0 0 28px rgba(45, 212, 191, 0.25);
}

.brand-copy > p {
  max-width: 520px;
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: rgba(219, 235, 248, 0.68);
}

.feature-list {
  display: grid;
  gap: 12px;
  margin-top: 36px;
  max-width: 540px;
}

.feature {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(153, 211, 231, 0.14);
  background: rgba(255, 255, 255, 0.035);
  border-radius: 12px;
  backdrop-filter: blur(6px);
}

.feature-icon {
  font-size: 10px;
  font-weight: 800;
  color: #6ce8da;
  border: 1px solid rgba(108, 232, 218, 0.3);
  width: 28px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature b {
  display: block;
  font-size: 12px;
  margin-bottom: 3px;
}

.feature span {
  font-size: 11px;
  color: rgba(209, 232, 248, 0.55);
}

.brand-foot {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: rgba(209, 232, 248, 0.38);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding-top: 18px;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 38px;
  position: relative;
}

.login-panel::before {
  content: "";
  position: absolute;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(15, 185, 166, 0.12), transparent 68%);
  top: 6%;
  right: 8%;
}

.login-card {
  width: min(440px, 100%);
  min-width: 0;
  position: relative;
  z-index: 1;
}

.login-mobile-brand {
  display: none;
  align-items: center;
  gap: 11px;
  margin-bottom: 24px;
}

.login-mobile-brand .mark {
  width: 41px;
  height: 41px;
  border-radius: 13px;
  font-size: 21px;
}

.login-mobile-brand .name {
  color: var(--ink);
  font-size: 18px;
  letter-spacing: 1.2px;
}

.login-mobile-brand .en {
  color: var(--muted-2);
  font-size: 7px;
}

.mobile-env {
  margin-left: auto;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid #d5eee9;
  background: #f0fbf8;
  color: var(--teal-dark);
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.6px;
}

.login-kicker {
  color: var(--teal-dark);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.login-head h2 {
  margin: 9px 0 6px;
  font-size: 30px;
  letter-spacing: -0.8px;
}

.login-head p {
  margin: 0 0 26px;
  font-size: 13px;
  color: var(--muted);
}

.login-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 10px 22px rgba(15, 185, 166, 0.22);
}

.divider {
  position: relative;
  text-align: center;
  margin: 26px 0 18px;
}

.divider::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  background: var(--line);
}

.divider span {
  position: relative;
  padding: 0 12px;
  background: #f4f8fc;
  color: var(--muted-2);
  font-size: 11px;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.role-card {
  display: flex;
  align-items: center;
  gap: 9px;
  text-align: left;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.role-card:hover {
  border-color: rgba(15, 185, 166, 0.55);
  box-shadow: 0 8px 18px rgba(15, 185, 166, 0.1);
  transform: translateY(-1px);
}

.role-avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.role-copy b {
  font-size: 12px;
  color: var(--ink);
}

.role-copy span {
  font-size: 9px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-entry {
  width: 100%;
  margin-top: 14px;
  border: 1px dashed rgba(15, 185, 166, 0.45);
  background: rgba(230, 250, 247, 0.55);
  color: var(--teal-dark);
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.mobile-entry .arrow {
  margin-left: auto;
}

.android-entry {
  margin-top: 8px;
  border-style: solid;
  border-color: rgba(59, 130, 246, 0.28);
  background: rgba(239, 246, 255, 0.62);
  color: #2f6fb5;
}

.login-tip {
  margin-top: 22px;
  display: flex;
  gap: 7px;
  align-items: flex-start;
  font-size: 10px;
  line-height: 1.7;
  color: var(--muted-2);
}

.login-tip .el-icon {
  margin-top: 2px;
  color: var(--teal);
}

@media (max-width: 980px) {
  .login-page { grid-template-columns: 1fr; width: 100%; overflow-x: hidden; }
  .login-brand { display: none; }
  .login-panel { padding: 26px 20px; min-height: 100vh; width: 100%; min-width: 0; }
  .login-mobile-brand { display: flex; }
  .login-head h2 { font-size: 27px; }
  .login-head p { margin-bottom: 22px; }
}
</style>
