<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">企业画像与台账</h1>
        <p class="page-sub">统一维护企业主体、生产状态、地图坐标、联络信息与排口监测点</p>
      </div>
      <div class="page-actions">
        <div class="soft-pill green">{{ list.length }} 家监管企业</div>
        <div class="soft-pill amber">{{ highRiskCount }} 家高风险</div>
        <el-button @click="openCreate"><el-icon><Plus /></el-icon> 新增企业</el-button>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card card-pad" style="margin-bottom:18px">
      <div class="filter-bar" style="margin-bottom:0">
        <el-input v-model="filters.keyword" placeholder="搜索企业名称 / 行业 / 信用代码" clearable
          :prefix-icon="Search" @keyup.enter="load" />
        <el-select v-model="filters.region" placeholder="全部区域" clearable filterable>
          <el-option v-for="r in regions" :key="r" :label="r" :value="r" />
        </el-select>
        <el-select v-model="filters.riskLevel" placeholder="全部风险等级" clearable>
          <el-option v-for="r in ['A','B','C','D']" :key="r" :label="`${r} 级`" :value="r" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部台账状态" clearable>
          <el-option label="在管" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <div class="grow"></div>
        <span class="tiny muted">风险分由规则与事件自动计算，台账字段可直接维护</span>
      </div>
    </div>

    <div class="card">
      <el-table :data="list" v-loading="loading" style="width:100%" :row-class-name="rowClass" @row-click="openProfile">
        <el-table-column label="企业主体" min-width="290">
          <template #default="{ row }">
            <div class="company-cell">
              <div class="company-avatar" :style="{ background: avatarColor(row.riskLevel) }">{{ row.name.slice(0, 1) }}</div>
              <div class="company-copy">
                <div class="company-name">
                  {{ row.name }}
                  <span v-if="row.status === 'INACTIVE'" class="row-flag">停用</span>
                  <span v-else-if="!hasCoordinate(row)" class="row-flag warn">未定位</span>
                </div>
                <div class="company-sub">{{ row.creditCode }} · {{ row.region }}</div>
                <div class="company-address">
                  <el-icon><Location /></el-icon>{{ row.address || '未维护企业地址' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="行业 / 类别" min-width="150">
          <template #default="{ row }">
            <div class="industry">{{ row.industry || '未分类' }}</div>
            <el-tag size="small" :type="row.managementLevel === '重点' ? 'warning' : 'info'" effect="plain" style="margin-top:5px">
              {{ row.managementLevel }}监管
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="生产状态" width="118">
          <template #default="{ row }">
            <el-tag size="small" :type="productionMeta(row.productionStatus).type" effect="plain">
              {{ productionMeta(row.productionStatus).label }}
            </el-tag>
            <div class="state-sub">许可 {{ row.permitNo ? '已登记' : '待补录' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="风险画像" width="196">
          <template #default="{ row }">
            <div class="risk-score-cell">
              <el-progress :percentage="row.riskScore || 0" :show-text="false" :stroke-width="7" :color="riskMeta(row.riskLevel).color" />
              <b :style="{ color: riskMeta(row.riskLevel).color }">{{ row.riskScore }}</b>
              <el-tag :type="riskMeta(row.riskLevel).type" size="small" effect="plain" round>{{ row.riskLevel }} 级</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="点位 / 事件" width="112" align="center">
          <template #default="{ row }">
            <div class="mini-metrics">
              <div><b>{{ row.pointCount }}</b><span>点位</span></div>
              <div><b :class="{ hot: row.pendingEvents > 0 }">{{ row.pendingEvents }}</b><span>事件</span></div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="联络信息" min-width="190">
          <template #default="{ row }">
            <div class="contact-name">{{ row.contactName || '未维护' }}<span v-if="row.contactPhone"> · {{ row.contactPhone }}</span></div>
            <div class="contact-sub">法人 {{ row.legalRepresentative || '—' }}</div>
            <div class="contact-sub">更新 {{ row.updatedAt || '—' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="188" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openProfile(row)">画像</el-button>
            <el-button link @click.stop="openEdit(row)"><el-icon><EditPen /></el-icon> 编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <EnterpriseEditDialog v-model="editVisible" :enterprise="editing" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, EditPen, Location, Plus, Refresh, Search } from '@element-plus/icons-vue'
import api from '../api'
import EnterpriseEditDialog from '../components/EnterpriseEditDialog.vue'
import { riskMeta } from '../utils/labels'
import { productionMeta } from '../utils/dicts'
import { BEIJING_DISTRICT_NAMES } from '../utils/beijingMap'
import { hasRole } from '../store'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const list = ref([])
const editVisible = ref(false)
const editing = ref(null)
const filters = reactive({
  keyword: '',
  region: route.query.region || '',
  riskLevel: '',
  status: ''
})
const regions = BEIJING_DISTRICT_NAMES

const highRiskCount = computed(() => list.value.filter((x) => ['C', 'D'].includes(x.riskLevel)).length)

function avatarColor(level) {
  const map = {
    A: 'linear-gradient(135deg,#15803d,#4ade80)',
    B: 'linear-gradient(135deg,#1d4ed8,#60a5fa)',
    C: 'linear-gradient(135deg,#b45309,#fbbf24)',
    D: 'linear-gradient(135deg,#b91c1c,#f87171)'
  }
  return map[level] || 'linear-gradient(135deg,#0f2137,#5b7a99)'
}

function hasCoordinate(row) {
  return Number.isFinite(Number(row.longitude)) && Number.isFinite(Number(row.latitude))
}

function rowClass({ row }) {
  return [row.status === 'INACTIVE' ? 'inactive-row' : '', !hasCoordinate(row) ? 'unlocated-row' : ''].join(' ')
}

function ensureMaintainer() {
  if (hasRole('reviewer', 'admin')) return true
  ElMessage.warning('当前账号只读，请使用审核人员或管理员账号维护企业台账')
  return false
}

function openCreate() {
  if (!ensureMaintainer()) return
  editing.value = null
  editVisible.value = true
}

function openEdit(row) {
  if (!ensureMaintainer()) return
  editing.value = { ...row }
  editVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/enterprises', { params: filters })
    list.value = data
  } finally {
    loading.value = false
  }
}

function openProfile(row) {
  router.push(`/enterprises/${row.id}`)
}

onMounted(load)
</script>

<style scoped>
.company-cell { display: flex; align-items: flex-start; gap: 11px; }
.company-avatar {
  width: 38px; height: 38px; border-radius: 11px; color: #fff; font-weight: 800;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  box-shadow: 0 7px 16px rgba(15, 33, 55, 0.14);
}
.company-copy { min-width: 0; }
.company-name { font-size: 13px; font-weight: 700; color: var(--ink); display: flex; align-items: center; gap: 6px; }
.company-sub { margin-top: 4px; font-size: 10px; color: var(--muted); }
.company-address {
  margin-top: 4px; max-width: 340px; display: flex; align-items: center; gap: 3px;
  font-size: 9px; color: var(--muted-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.row-flag {
  padding: 1px 5px; border-radius: 4px; background: #f1f5f9; color: #64748b;
  font-size: 8px; font-weight: 500;
}
.row-flag.warn { background: #fff7e8; color: #b45309; }
.industry { font-size: 12px; color: var(--ink-2); }
.state-sub { margin-top: 5px; font-size: 9px; color: var(--muted-2); }
.risk-score-cell { display: flex; align-items: center; gap: 8px; }
.risk-score-cell .el-progress { flex: 1; }
.risk-score-cell b { width: 28px; text-align: right; font-size: 15px; }
.mini-metrics { display: flex; justify-content: center; gap: 15px; }
.mini-metrics div { text-align: center; }
.mini-metrics b { display: block; font-size: 15px; color: var(--ink); line-height: 1.1; }
.mini-metrics b.hot { color: #ea580c; }
.mini-metrics span { display: block; margin-top: 3px; font-size: 8px; color: var(--muted); }
.contact-name { font-size: 11px; color: var(--ink-2); }
.contact-sub { margin-top: 3px; font-size: 9px; color: var(--muted-2); }
:deep(.el-table__row) { cursor: pointer; }
:deep(.el-table__row.inactive-row) { opacity: 0.55; }
:deep(.el-table__row.unlocated-row td:first-child) { box-shadow: inset 3px 0 0 #f0b657; }
</style>
