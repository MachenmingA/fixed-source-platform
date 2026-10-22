<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">规则配置</h1>
        <p class="page-sub">规则版本、窗口、阈值和启停管理；首期实现 R001—R005</p>
      </div>
      <div class="page-actions">
        <div class="soft-pill green">{{ enabledCount }} 条已启用</div>
        <router-link to="/backtest"><el-button type="primary" plain><el-icon><TrendCharts /></el-icon> 规则回测</el-button></router-link>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="rule-grid">
      <div v-for="rule in rules" :key="rule.ruleCode" class="card rule-card" :class="{ disabled: !rule.enabled }">
        <div class="rule-head">
          <div>
            <div class="rule-code">{{ rule.ruleCode }}<span class="version">{{ rule.version }}</span></div>
            <h3>{{ rule.name }}</h3>
          </div>
          <div class="rule-actions">
            <el-switch
              :model-value="rule.enabled"
              :disabled="!isAdmin"
              active-color="#0fb9a6"
              @change="(value) => toggle(rule, value)"
            />
            <el-button v-if="isAdmin" link type="primary" @click="openEdit(rule)"><el-icon><Edit /></el-icon> 编辑</el-button>
          </div>
        </div>
        <div class="rule-desc">{{ rule.description }}</div>
        <div class="rule-meta">
          <span><b>类别</b>{{ rule.category }}</span>
          <span><b>粒度</b>{{ rule.granularity }}</span>
          <span><b>窗口</b>{{ rule.windowHours }}h</span>
        </div>
        <div class="rule-expr"><span>判定</span><code>{{ rule.thresholdExpr }}</code></div>
      </div>
    </div>

    <el-dialog v-model="editVisible" title="编辑规则" width="520px" destroy-on-close>
      <el-form v-if="editing" label-position="top">
        <el-form-item label="规则">
          <el-input :model-value="`${editing.ruleCode} ${editing.name}`" disabled />
        </el-form-item>
        <el-form-item label="窗口（小时）">
          <el-input-number v-model="editing.windowHours" :min="1" :max="168" style="width:180px" />
        </el-form-item>
        <el-form-item label="判定表达式">
          <el-input v-model="editing.thresholdExpr" placeholder="例如 value > limit" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="editing.version" style="width:180px" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editing.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editing.enabled" active-color="#0fb9a6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <div class="card card-pad" style="margin-top:18px">
      <div class="card-head">
        <h3 class="card-title"><span class="dot"></span> 规则设计与证据口径</h3>
        <span class="soft-pill">规则版本可追溯</span>
      </div>
      <div class="design-grid">
        <div><b>先聚合状态，再连接监测值</b><span>同一小时多条状态记录先按业务键聚合，避免重复放大结果。</span></div>
        <div><b>异常时段与回看窗口分离</b><span>回看窗口用于发现，异常持续时间单独记录，不用基准窗口代替。</span></div>
        <div><b>精确状态匹配</b><span>停运、故障、校准等状态按精确值匹配，不做模糊包含或近义词扩展。</span></div>
        <div><b>回测与版本</b><span>P1 支持历史数据回放和阈值/版本对比，服务规则调整与论文实验。</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Refresh, TrendCharts } from '@element-plus/icons-vue'
import api from '../api'
import { hasRole } from '../store'

const loading = ref(false)
const saving = ref(false)
const rules = ref([])
const editVisible = ref(false)
const editing = ref(null)
const isAdmin = computed(() => hasRole('admin'))
const enabledCount = computed(() => rules.value.filter((x) => x.enabled).length)

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/rules')
    rules.value = data
  } finally {
    loading.value = false
  }
}

async function toggle(rule, value) {
  await api.put(`/rules/${rule.ruleCode}`, { enabled: value })
  rule.enabled = value
  ElMessage.success(value ? `已启用 ${rule.name}` : `已停用 ${rule.name}`)
}

function openEdit(rule) {
  editing.value = { ...rule }
  editVisible.value = true
}

async function saveRule() {
  if (!editing.value) return
  saving.value = true
  try {
    await api.put(`/rules/${editing.value.ruleCode}`, {
      windowHours: editing.value.windowHours,
      thresholdExpr: editing.value.thresholdExpr,
      version: editing.value.version,
      description: editing.value.description,
      enabled: editing.value.enabled
    })
    ElMessage.success('规则配置已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.rule-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.rule-card { padding: 18px; transition: opacity 0.2s ease; }
.rule-card.disabled { opacity: 0.58; }
.rule-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.rule-actions { display: flex; align-items: center; gap: 10px; }
.rule-code { font-size: 10px; color: var(--teal-dark); font-weight: 700; letter-spacing: 0.8px; display: flex; align-items: center; gap: 6px; }
.version { font-size: 9px; color: var(--muted); background: #f2f6fb; border-radius: 999px; padding: 1px 6px; font-weight: 500; letter-spacing: 0; }
.rule-head h3 { margin: 6px 0 0; font-size: 16px; letter-spacing: -0.2px; }
.rule-desc { margin-top: 11px; min-height: 40px; font-size: 11px; line-height: 1.75; color: var(--muted); }
.rule-meta { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line); }
.rule-meta span { display: flex; flex-direction: column; gap: 3px; font-size: 10px; color: var(--muted); }
.rule-meta b { font-size: 9px; color: var(--muted-2); font-weight: 500; }
.rule-expr { margin-top: 11px; }
.rule-expr span { font-size: 9px; color: var(--muted); display: block; margin-bottom: 5px; }
.rule-expr code { display: block; background: #f4fbfa; border: 1px solid #e4f5f2; border-radius: 7px; padding: 7px 8px; font-size: 10px; color: #23756d; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.design-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.design-grid div { padding: 12px 14px; border-radius: 11px; background: #f9fcfe; border: 1px solid #eef5fa; }
.design-grid b { display: block; font-size: 12px; color: var(--ink); margin-bottom: 5px; }
.design-grid span { font-size: 10px; line-height: 1.7; color: var(--muted); }
@media (max-width: 1100px) {
  .rule-grid { grid-template-columns: 1fr 1fr; }
  .design-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .rule-grid { grid-template-columns: 1fr; }
}
</style>
