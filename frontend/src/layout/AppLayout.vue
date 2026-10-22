<template>
  <div class="app-shell" :class="{ 'screen-shell': route.path === '/dashboard' }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">净</div>
        <div>
          <div class="brand-name">净源智研</div>
          <div class="brand-sub">FIXED SOURCE INTELLIGENCE</div>
        </div>
      </div>

      <div class="nav-caption">监管工作台</div>
      <nav class="nav-list">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path || (item.path !== '/dashboard' && route.path.startsWith(item.path)) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
        </router-link>
      </nav>

      <div class="sidebar-note">
        <div class="note-title"><span class="pulse"></span> DEMO ENV</div>
        <div class="note-text">模拟数据 · 独立原型<br />不接入真实企业生产数据</div>
        <div class="note-line"></div>
        <div class="note-small">每日 02:00 自动采集</div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <div class="crumb">{{ currentTitle }}</div>
          <div class="top-sub">固定污染源企业画像与 AI 辅助研判系统</div>
        </div>
        <div class="topbar-right">
          <div class="today">{{ todayText }}</div>
          <el-tooltip content="打开移动核查端" placement="bottom">
            <el-button class="icon-btn" circle @click="openMobile">
              <el-icon><Iphone /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <div class="avatar">{{ auth.user?.name?.slice(0, 1) || 'U' }}</div>
              <div class="user-meta">
                <b>{{ auth.user?.name || '未登录' }}</b>
                <span>{{ auth.user?.roleLabel || '—' }}</span>
              </div>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="mobile">
                  <el-icon><Iphone /></el-icon> 进入移动 H5 核查端
                </el-dropdown-item>
                <el-dropdown-item command="android">
                  <el-icon><Download /></el-icon> 下载 Android 现场端 APK
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, CircleCheck, Connection, DataAnalysis, DataLine, Document, Download, Iphone, List, MapLocation, OfficeBuilding, Setting, Share, SwitchButton, TrendCharts, Warning } from '@element-plus/icons-vue'
import { auth, clearAuth } from '../store'

const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/dashboard', label: '监管大屏', icon: DataLine },
  { path: '/cockpit', label: '区域态势', icon: MapLocation },
  { path: '/enterprises', label: '企业画像', icon: OfficeBuilding },
  { path: '/events', label: '事件工作台', icon: Warning },
  { path: '/tasks', label: '核查任务', icon: List },
  { path: '/collect', label: '采集批次', icon: Download },
  { path: '/monitoring', label: '监测数据', icon: TrendCharts },
  { path: '/data-quality', label: '数据质量', icon: CircleCheck },
  { path: '/rules', label: '规则配置', icon: Setting },
  { path: '/backtest', label: '规则回测', icon: TrendCharts },
  { path: '/test-data', label: '测试数据', icon: DataAnalysis },
  { path: '/integration', label: '集成对账', icon: Connection },
  { path: '/data-hub', label: '数据共享', icon: Share },
  { path: '/reports', label: '核查报告', icon: Document }
]

const titles = {
  '/dashboard': '监管大屏',
  '/cockpit': '区域态势',
  '/enterprises': '企业画像',
  '/events': '事件工作台',
  '/tasks': '核查任务',
  '/collect': '采集批次',
  '/monitoring': '监测数据分析',
  '/data-quality': '数据质量',
  '/rules': '规则配置',
  '/backtest': '规则回测',
  '/test-data': '测试数据',
  '/integration': '集成对账',
  '/data-hub': '国家数据共享平台',
  '/reports': '核查报告'
}

const currentTitle = computed(() => {
  if (route.path.startsWith('/enterprises/')) return '企业画像详情'
  return titles[route.path] || '监管工作台'
})

const todayText = computed(() => {
  const now = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()]
  return `${now.getFullYear()} / ${String(now.getMonth() + 1).padStart(2, '0')} / ${String(now.getDate()).padStart(2, '0')} 周${week}`
})

function openMobile() {
  window.open('/m/tasks', '_blank')
}

function onCommand(command) {
  if (command === 'logout') {
    clearAuth()
    router.push('/login')
  } else if (command === 'mobile') {
    openMobile()
  } else if (command === 'android') {
    window.open('/downloads/fixed-source-mobile-debug.apk', '_blank')
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
}

.sidebar {
  width: 238px;
  min-width: 238px;
  min-height: 100vh;
  padding: 26px 18px 22px;
  color: #eaf4ff;
  background:
    radial-gradient(circle at 0% 0%, rgba(15, 185, 166, 0.16), transparent 32%),
    linear-gradient(180deg, #0b1b2e 0%, #0c2238 100%);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 6px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #19c7b3, #0b8b81);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px rgba(15, 185, 166, 0.28);
}

.brand-name {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 1px;
}

.brand-sub {
  margin-top: 3px;
  font-size: 8px;
  letter-spacing: 1.1px;
  color: rgba(207, 228, 246, 0.5);
}

.nav-caption {
  margin: 26px 10px 8px;
  font-size: 10px;
  letter-spacing: 1.6px;
  color: rgba(184, 207, 230, 0.45);
  text-transform: uppercase;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow-y: auto;
  min-height: 0;
}

.nav-list::-webkit-scrollbar { width: 0; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 12px;
  border-radius: 11px;
  color: rgba(220, 235, 250, 0.72);
  font-size: 13px;
  transition: all 0.2s ease;
  position: relative;
}

.nav-item .el-icon {
  font-size: 17px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.055);
  color: #fff;
}

.nav-item.active {
  color: #eafffc;
  background: linear-gradient(90deg, rgba(15, 185, 166, 0.22), rgba(15, 185, 166, 0.08));
  box-shadow: inset 3px 0 0 #1dd3bf;
}

.nav-badge {
  margin-left: auto;
  font-size: 10px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  padding: 1px 7px;
}

.sidebar-note {
  margin-top: auto;
  border: 1px solid rgba(150, 200, 225, 0.14);
  background: rgba(255, 255, 255, 0.035);
  border-radius: 14px;
  padding: 14px;
}

.note-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
  letter-spacing: 1.2px;
  color: #8ef0e3;
  font-weight: 700;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1dd3bf;
  box-shadow: 0 0 0 4px rgba(29, 211, 191, 0.16);
}

.note-text {
  margin-top: 9px;
  font-size: 11px;
  line-height: 1.7;
  color: rgba(211, 231, 249, 0.6);
}

.note-line {
  height: 1px;
  margin: 11px 0;
  background: rgba(255, 255, 255, 0.08);
}

.note-small {
  font-size: 10px;
  color: rgba(211, 231, 249, 0.42);
}

.workspace {
  flex: 1;
  min-width: 0;
}

.topbar {
  height: 78px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.92), rgba(248, 252, 253, 0.84));
  border-bottom: 1px solid rgba(222, 235, 241, 0.9);
  backdrop-filter: blur(14px);
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: linear-gradient(90deg, rgba(15, 185, 166, 0.32), transparent 34%, transparent 72%, rgba(59, 130, 246, 0.12));
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
}

.crumb::before {
  content: "";
  width: 4px;
  height: 15px;
  border-radius: 3px;
  background: linear-gradient(180deg, #1ad0bd, #2b7fc7);
}

.top-sub {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted-2);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.today {
  font-size: 12px;
  color: var(--muted);
  padding-right: 4px;
}

.icon-btn {
  border: 1px solid #dfeaf0;
  color: #3a5a70;
  background: #fbfdfe;
  box-shadow: 0 3px 10px rgba(17, 52, 73, 0.04);
}

.icon-btn:hover {
  border-color: #b7ddd8;
  color: var(--teal-dark);
  background: #f5fcfa;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 9px 5px 5px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-chip:hover {
  background: #f2f8fc;
  border-color: #dfeaf0;
}

.avatar {
  width: 33px;
  height: 33px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0f2137, #1c5a6d);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-meta b {
  font-size: 12px;
  font-weight: 700;
}

.user-meta span {
  font-size: 10px;
  color: var(--muted);
}

.arrow {
  font-size: 12px;
  color: var(--muted);
}

.content {
  padding: 24px 28px 40px;
  max-width: 1720px;
  margin: 0 auto;
  width: 100%;
}

.screen-shell {
  background: #04121b;
}

.screen-shell .topbar {
  background: rgba(4, 18, 27, 0.9);
  border-bottom-color: rgba(56, 119, 129, 0.24);
}

.screen-shell .crumb { color: #e7fbfa; }
.screen-shell .top-sub { color: #6b8b90; }
.screen-shell .today { color: #79999e; }
.screen-shell .icon-btn {
  background: #0c2630;
  border-color: #1c444e;
  color: #bcdadc;
}
.screen-shell .user-chip:hover { background: rgba(47, 227, 200, 0.06); }
.screen-shell .user-meta b { color: #e3f7f6; }
.screen-shell .user-meta span,
.screen-shell .arrow { color: #77979c; }
.screen-shell .content {
  padding: 0;
  max-width: none;
  background: #04121b;
}
.screen-shell .sidebar {
  border-right: 1px solid rgba(56, 119, 129, 0.18);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1100px) {
  .sidebar { width: 210px; min-width: 210px; }
  .today { display: none; }
  .content { padding: 22px 20px 32px; }
  .topbar { padding: 0 20px; }
}

@media (max-width: 820px) {
  .sidebar { display: none; }
  .topbar { padding: 0 16px; }
  .top-sub { display: none; }
  .content { padding: 18px 14px 28px; }
  .user-meta { display: none; }
}
</style>
