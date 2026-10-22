<template>
  <div class="report-page" v-loading="loading">
    <div class="report-toolbar">
      <el-button @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <div class="toolbar-title">{{ report?.title || '核查报告' }}</div>
      <div class="toolbar-actions">
        <el-tag type="warning" effect="plain">辅助生成 · 需人工审核</el-tag>
        <el-button type="primary" @click="printReport"><el-icon><Printer /></el-icon> 打印 / 导出 PDF</el-button>
      </div>
    </div>
    <div v-if="report" class="report-canvas">
      <div class="report-meta">
        <span>{{ report.reportNo }}</span>
        <span>{{ report.createdBy }} · {{ report.createdAt }}</span>
      </div>
      <div class="report-doc" v-html="report.contentHtml"></div>
    </div>
    <div v-else-if="!loading" class="empty-block"><el-icon><Document /></el-icon><span>报告不存在</span></div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document, Printer } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const report = ref(null)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/reports/${route.params.id}`)
    report.value = data
  } finally {
    loading.value = false
  }
}

function printReport() {
  window.print()
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/reports')
}

onMounted(load)
</script>

<style scoped>
.report-page { min-height: 100vh; background: #eef3f8; padding: 0 0 40px; }
.report-toolbar {
  position: sticky; top: 0; z-index: 10; height: 64px; padding: 0 24px;
  display: flex; align-items: center; gap: 12px; background: rgba(255,255,255,0.96);
  border-bottom: 1px solid #e5edf6; backdrop-filter: blur(10px);
}
.toolbar-title { flex: 1; text-align: center; font-size: 13px; font-weight: 700; color: #13253d; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.toolbar-actions { display: flex; align-items: center; gap: 9px; }
.report-canvas { width: min(900px, calc(100% - 40px)); margin: 24px auto 0; background: #fff; border-radius: 14px; box-shadow: 0 18px 50px rgba(15,33,55,0.12); padding: 24px 34px 40px; }
.report-meta { display: flex; justify-content: space-between; gap: 12px; color: #94a3b8; font-size: 10px; margin-bottom: 18px; }
@media print {
  .report-toolbar { display: none; }
  .report-page { background: #fff; padding: 0; }
  .report-canvas { width: 100%; margin: 0; box-shadow: none; border-radius: 0; padding: 0; }
  .report-meta { display: none; }
}
@media (max-width: 700px) {
  .report-toolbar { padding: 0 12px; }
  .toolbar-title { display: none; }
  .toolbar-actions .el-tag { display: none; }
  .report-canvas { width: calc(100% - 20px); padding: 18px 16px 30px; margin-top: 12px; }
}
</style>
