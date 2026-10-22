<template>
  <el-dialog
    v-model="visible"
    class="enterprise-edit-dialog"
    :title="isEdit ? '维护企业台账' : '新增监管企业'"
    width="880px"
    top="4vh"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-section">
        <span class="section-index">01</span>
        <div>
          <b>主体信息</b>
          <p>名称、行业、监管类别与排污许可信息，修改后会同步到企业画像和首页地图。</p>
        </div>
      </div>

      <div class="form-grid">
        <el-form-item label="企业名称" prop="name" class="span-2">
          <el-input v-model="form.name" maxlength="128" placeholder="请输入企业全称" />
        </el-form-item>
        <el-form-item label="统一社会信用代码" prop="creditCode">
          <el-input v-model="form.creditCode" maxlength="18" placeholder="15—18 位代码" />
        </el-form-item>
        <el-form-item label="所属区域" prop="region">
          <el-select v-model="form.region" filterable style="width:100%" placeholder="请选择行政区">
            <el-option v-for="region in regions" :key="region" :label="region" :value="region" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属行业" prop="industry">
          <el-select v-model="form.industry" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="item in industries" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="管理类别" prop="managementLevel">
          <el-radio-group v-model="form.managementLevel">
            <el-radio-button value="重点">重点监管</el-radio-button>
            <el-radio-button value="一般">一般监管</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生产状态" prop="productionStatus">
          <el-select v-model="form.productionStatus" style="width:100%">
            <el-option v-for="item in productionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="台账状态" prop="status">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="在管" value="ACTIVE" />
            <el-option label="停用（保留历史数据）" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="排污许可证号" prop="permitNo" class="span-2">
          <el-input v-model="form.permitNo" maxlength="64" placeholder="排污许可证编号，可后续补录" />
        </el-form-item>
      </div>

      <div class="form-section section-gap">
        <span class="section-index">02</span>
        <div>
          <b>位置与联络</b>
          <p>坐标用于首页风险地图撒点，可直接点击地图拾取；联络信息用于现场核查派单。</p>
        </div>
      </div>

      <div class="form-grid">
        <el-form-item label="企业地址" prop="address" class="span-2">
          <el-input v-model="form.address" maxlength="255" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="地图定位" class="span-2 coordinate-item">
          <LocationPicker
            :longitude="form.longitude"
            :latitude="form.latitude"
            :region="form.region"
            @pick="onPick"
          />
          <div class="coordinate-row">
            <el-form-item label="经度" prop="longitude">
              <el-input-number v-model="form.longitude" :precision="6" :step="0.0001" :controls="false"
                placeholder="116.300000" style="width:100%" />
            </el-form-item>
            <el-form-item label="纬度" prop="latitude">
              <el-input-number v-model="form.latitude" :precision="6" :step="0.0001" :controls="false"
                placeholder="39.950000" style="width:100%" />
            </el-form-item>
            <div class="coordinate-tip">
              北京辖区范围：经度 115.0—117.6，纬度 39.3—41.2
            </div>
          </div>
        </el-form-item>
        <el-form-item label="法定代表人" prop="legalRepresentative">
          <el-input v-model="form.legalRepresentative" maxlength="64" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="环保联系人" prop="contactName">
          <el-input v-model="form.contactName" maxlength="64" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="32" placeholder="手机或座机" />
        </el-form-item>
        <el-form-item label="备注" prop="remark" class="span-2">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" show-word-limit
            placeholder="补充说明，如历史整改、停产计划等" />
        </el-form-item>
      </div>
    </el-form>

    <div class="form-note">
      <span class="note-mark">i</span>
      维护记录会写入审计日志。风险分和风险等级仍由规则命中、事件聚合与历史反馈自动计算，不在此处直接修改。
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        {{ isEdit ? '保存修改' : '创建企业' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import LocationPicker from './LocationPicker.vue'
import { BEIJING_DISTRICT_NAMES } from '../utils/beijingMap'
import { INDUSTRY_OPTIONS, PRODUCTION_STATUS } from '../utils/dicts'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  enterprise: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)
const regions = BEIJING_DISTRICT_NAMES
const industries = INDUSTRY_OPTIONS
const productionOptions = Object.entries(PRODUCTION_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label
}))

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const isEdit = computed(() => !!props.enterprise?.id)

const emptyForm = () => ({
  name: '',
  creditCode: '',
  region: '海淀区',
  industry: '其他',
  managementLevel: '一般',
  productionStatus: 'NORMAL',
  status: 'ACTIVE',
  permitNo: '',
  address: '',
  legalRepresentative: '',
  contactName: '',
  contactPhone: '',
  longitude: null,
  latitude: null,
  remark: ''
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  creditCode: [
    { required: true, message: '请输入统一社会信用代码', trigger: 'blur' },
    { min: 15, max: 18, message: '统一社会信用代码应为 15—18 位', trigger: 'blur' }
  ],
  region: [{ required: true, message: '请选择所属区域', trigger: 'change' }],
  industry: [{ required: true, message: '请选择所属行业', trigger: 'change' }],
  managementLevel: [{ required: true, message: '请选择管理类别', trigger: 'change' }],
  productionStatus: [{ required: true, message: '请选择生产状态', trigger: 'change' }],
  status: [{ required: true, message: '请选择台账状态', trigger: 'change' }]
}

function fillForm() {
  Object.assign(form, emptyForm())
  if (props.enterprise) {
    Object.keys(form).forEach((key) => {
      if (props.enterprise[key] !== undefined && props.enterprise[key] !== null) {
        form[key] = props.enterprise[key]
      }
    })
    form.longitude = toNumber(props.enterprise.longitude)
    form.latitude = toNumber(props.enterprise.latitude)
  }
  formRef.value?.clearValidate()
}

function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function onPick({ longitude, latitude }) {
  form.longitude = longitude
  form.latitude = latitude
  formRef.value?.validateField(['longitude', 'latitude']).catch(() => {})
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) fillForm()
  }
)
watch(
  () => props.enterprise,
  () => {
    if (props.modelValue) fillForm()
  },
  { deep: true }
)

async function save() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (form.longitude == null || form.latitude == null) {
    ElMessage.warning('请在地图上拾取企业坐标，或手动填写经纬度')
    return
  }
  if (form.longitude < 115 || form.longitude > 117.6 || form.latitude < 39.3 || form.latitude > 41.2) {
    ElMessage.warning('坐标超出北京辖区范围')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (isEdit.value) {
      await api.put(`/enterprises/${props.enterprise.id}`, payload)
      ElMessage.success('企业台账已更新')
    } else {
      await api.post('/enterprises', payload)
      ElMessage.success('企业已创建，可在画像中继续维护排口')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-section {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}
.section-gap { margin-top: 8px; }
.section-index {
  width: 27px;
  height: 27px;
  border-radius: 8px;
  background: var(--teal-soft);
  color: var(--teal-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}
.form-section b { font-size: 14px; color: var(--ink); }
.form-section p { margin: 4px 0 0; font-size: 11px; line-height: 1.6; color: var(--muted); }
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
.form-grid .span-2 { grid-column: span 2; }
.form-grid :deep(.el-form-item) { margin-bottom: 15px; }
.form-grid :deep(.el-form-item__label) { padding-bottom: 5px; font-size: 11px; color: var(--ink-2); }
.coordinate-item { display: block; width: 100%; }
.coordinate-item :deep(.el-form-item__content) { display: block; width: 100%; }
.coordinate-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
.coordinate-tip {
  grid-column: span 2;
  margin-top: -6px;
  font-size: 10px;
  color: var(--muted);
}
.coordinate-row :deep(.el-form-item) { margin-bottom: 6px; }
.form-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f3fbfa;
  color: #4d7a76;
  font-size: 10px;
  line-height: 1.7;
}
.note-mark {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #d8f3ee;
  color: #0a9487;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 1px;
}
:global(.enterprise-edit-dialog .el-dialog__body) {
  max-height: calc(100vh - 250px);
  overflow-y: auto;
  padding-right: 8px;
}
</style>
