<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">采集批次</h1>
        <p class="page-sub">每日调用模拟数据源接口，记录水位、应收实收和失败明细</p>
      </div>
      <div class="page-actions">
        <router-link to="/data-quality"><el-button><el-icon><CircleCheck /></el-icon> 数据质量</el-button></router-link>
        <el-button type="primary" :loading="collecting" @click="runCollect"><el-icon><VideoPlay /></el-icon> 运行今日采集</el-button>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="card in cards" :key="card.label" class="stat-card">
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        <div class="stat-hint">{{ card.hint }}</div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <el-table :data="batches" v-loading="loading" style="width:100%">
        <el-table-column label="批次" min-width="210">
          <template #default="{ row }">
            <div class="batch-no">{{ row.batchNo }}</div>
            <div class="batch-source">{{ row.source }} · {{ row.bizDate }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : row.status === 'RUNNING' ? '运行中' : row.status === 'PARTIAL' ? '部分成功' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应收 / 实收" width="135">
          <template #default="{ row }"><span class="mono">{{ row.expectedCount }} / {{ row.receivedCount }}</span></template>
        </el-table-column>
        <el-table-column label="成功 / 失败" width="125">
          <template #default="{ row }"><span class="mono success">{{ row.successCount }}</span> / <span class="mono fail">{{ row.failedCount }}</span></template>
        </el-table-column>
        <el-table-column label="开始时间" width="160" prop="startedAt" />
        <el-table-column label="结束时间" width="160" prop="finishedAt" />
        <el-table-column label="说明" min-width="260" prop="message" show-overflow-tooltip />
      </el-table>
    </div>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head">
        <h3 class="card-title"><span class="dot"></span> 数据源配置</h3>
        <el-button v-if="isAdmin" link type="primary" @click="openSource"><el-icon><Edit /></el-icon> 编辑</el-button>
      </div>
      <div v-for="source in dataSources" :key="source.id" class="source-row">
        <div class="source-icon">{{ source.sourceType }}</div>
        <div class="source-main">
          <b>{{ source.name }}</b>
          <span>{{ source.baseUrl }}</span>
          <small>鉴权 {{ source.authType }} · 版本 {{ source.version }} · {{ source.description }}</small>
        </div>
        <el-tag :type="source.enabled ? 'success' : 'info'" size="small">{{ source.enabled ? '启用' : '停用' }}</el-tag>
      </div>
      <div v-if="!dataSources.length" class="muted tiny">尚未配置数据源</div>
    </div>

    <el-dialog v-model="sourceVisible" title="编辑数据源" width="520px" destroy-on-close>
      <el-form v-if="editingSource" label-position="top">
        <el-form-item label="数据源名称"><el-input v-model="editingSource.name" disabled /></el-form-item>
        <el-form-item label="接口地址"><el-input v-model="editingSource.baseUrl" /></el-form-item>
        <el-form-item label="鉴权方式"><el-input v-model="editingSource.authType" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="editingSource.version" style="width:180px" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="editingSource.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingSource.enabled" active-color="#0fb9a6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sourceVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSource" @click="saveSource">保存</el-button>
      </template>
    </el-dialog>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head">
        <h3 class="card-title"><span class="dot"></span> 模拟数据源契约</h3>
        <span class="soft-pill green">MOCK-SOURCE · v1</span>
      </div>
      <div class="contract">
        <div class="contract-item"><span>基础资料</span><code>GET /mock-source/v1/base-data</code><small>企业、排口、点位、污染物</small></div>
        <div class="contract-item"><span>小时数据</span><code>GET /mock-source/v1/monitoring?bizDate=YYYY-MM-DD</code><small>按日期分页返回，含 6 类异常剧本</small></div>
        <div class="contract-item"><span>采集策略</span><code>每日 02:00 拉取 T-1</code><small>批次幂等、重跑、补采和失败重试</small></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Edit, Refresh, VideoPlay } from '@element-plus/icons-vue'
import api from '../api'
import { hasRole } from '../store'

const loading = ref(false)
const collecting = ref(false)
const savingSource = ref(false)
const batches = ref([])
const dataSources = ref([])
const sourceVisible = ref(false)
const editingSource = ref(null)
const isAdmin = hasRole('admin')

const latest = computed(() => batches.value[0] || {})
const cards = computed(() => [
  { label: '最近批次', value: latest.value.batchNo?.slice(-8) || '—', hint: latest.value.bizDate || '暂无采集', color: '#13253d' },
  { label: '应收数据', value: latest.value.expectedCount || 0, hint: '按日点位 × 污染物 × 24h', color: '#3b82f6' },
  { label: '实收数据', value: latest.value.receivedCount || 0, hint: `成功 ${latest.value.successCount || 0} 条`, color: '#0fb9a6' },
  { label: '失败数据', value: latest.value.failedCount || 0, hint: '失败明细会保留并支持补采', color: '#ef4444' }
])

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/collect-batches', { params: { limit: 40 } })
    batches.value = data
  } finally {
    loading.value = false
  }
}

async function loadSources() {
  const { data } = await api.get('/data-sources')
  dataSources.value = data
}

function openSource() {
  const source = dataSources.value[0]
  if (!source) return
  editingSource.value = { ...source }
  sourceVisible.value = true
}

async function saveSource() {
  if (!editingSource.value) return
  savingSource.value = true
  try {
    await api.put(`/data-sources/${editingSource.value.id}`, {
      name: editingSource.value.name,
      baseUrl: editingSource.value.baseUrl,
      authType: editingSource.value.authType,
      version: editingSource.value.version,
      description: editingSource.value.description,
      enabled: editingSource.value.enabled
    })
    ElMessage.success('数据源配置已保存')
    sourceVisible.value = false
    await loadSources()
  } finally {
    savingSource.value = false
  }
}

async function runCollect() {
  collecting.value = true
  try {
    const d = new Date()
    const bizDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const { data } = await api.post('/collect-batches', { bizDate })
    ElMessage.success(`批次 ${data.batchNo} 完成：接收 ${data.receivedCount} 条，新增线索 ${data.clues ?? 0} 条`)
    await load()
  } finally {
    collecting.value = false
  }
}

onMounted(() => { load(); loadSources() })
</script>

<style scoped>
.batch-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.batch-card { padding: 18px 20px; }
.batch-label { font-size: 11px; color: var(--muted); }
.batch-value { margin-top: 9px; font-size: 30px; font-weight: 800; line-height: 1; letter-spacing: -0.8px; }
.batch-hint { margin-top: 8px; font-size: 10px; color: var(--muted-2); }
.batch-no { font-size: 12px; font-weight: 700; color: var(--ink); font-family: "Cascadia Code", Consolas, monospace; }
.batch-source { margin-top: 4px; font-size: 10px; color: var(--muted); }
.mono { font-family: "Cascadia Code", Consolas, monospace; font-size: 11px; }
.success { color: #16a34a; }
.fail { color: #dc2626; }
.source-row { display: flex; align-items: center; gap: 11px; padding: 11px 12px; border-radius: 11px; background: #f9fcfe; border: 1px solid #eef5fa; margin-bottom: 8px; }
.source-icon { width: 40px; height: 30px; border-radius: 8px; background: #e8f8f5; color: #0a9487; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 800; flex-shrink: 0; }
.source-main { flex: 1; min-width: 0; }
.source-main b { display: block; font-size: 11px; color: var(--ink); }
.source-main span { display: block; margin-top: 3px; font-size: 10px; color: var(--teal-dark); font-family: "Cascadia Code", Consolas, monospace; }
.source-main small { display: block; margin-top: 3px; font-size: 9px; color: var(--muted); }
.contract { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.contract-item { border: 1px solid var(--line); background: #f9fcfe; border-radius: 12px; padding: 13px; display: flex; flex-direction: column; gap: 6px; }
.contract-item span { font-size: 10px; color: var(--muted); }
.contract-item code { font-size: 11px; color: #1c5364; background: #eef8f6; border-radius: 6px; padding: 6px 8px; word-break: break-all; }
.contract-item small { font-size: 10px; color: var(--muted-2); }
@media (max-width: 1100px) {
  .batch-grid { grid-template-columns: 1fr 1fr; }
  .contract { grid-template-columns: 1fr; }
}
</style>
