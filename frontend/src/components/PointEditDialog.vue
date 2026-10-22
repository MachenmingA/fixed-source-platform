<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '维护排口与监测点' : '新增排口与监测点'"
    width="560px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="point-form-grid">
        <el-form-item label="排口名称" prop="outletName" class="span-2">
          <el-input v-model="form.outletName" maxlength="64" placeholder="例如：1号废气排口" />
        </el-form-item>
        <el-form-item label="监测点名称" prop="pointName" class="span-2">
          <el-input v-model="form.pointName" maxlength="64" placeholder="例如：1号机组废气监测点" />
        </el-form-item>
        <el-form-item label="点位类型" prop="pointType">
          <el-select v-model="form.pointType" style="width:100%">
            <el-option v-for="item in pointTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="运行状态" prop="status">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="停用" value="STOPPED" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <div class="point-note">
      新增点位只维护监测对象台账，不会自动生成历史监测数据；后续采集批次按数据源实际返回关联。停用点位会保留历史记录。
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        {{ isEdit ? '保存修改' : '新增点位' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { POINT_TYPES } from '../utils/dicts'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  enterpriseId: { type: String, default: '' },
  point: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const saving = ref(false)
const pointTypes = POINT_TYPES
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const isEdit = computed(() => !!props.point?.id)

const emptyForm = () => ({
  outletName: '',
  pointName: '',
  pointType: '废气',
  status: 'NORMAL'
})
const form = reactive(emptyForm())
const rules = {
  outletName: [{ required: true, message: '请输入排口名称', trigger: 'blur' }],
  pointName: [{ required: true, message: '请输入监测点名称', trigger: 'blur' }],
  pointType: [{ required: true, message: '请选择点位类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择运行状态', trigger: 'change' }]
}

function fillForm() {
  Object.assign(form, emptyForm(), props.point || {})
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) fillForm()
  }
)
watch(() => props.point, () => {
  if (props.modelValue) fillForm()
}, { deep: true })

async function save() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await api.put(`/monitor-points/${props.point.id}`, { ...form })
      ElMessage.success('监测点已更新')
    } else {
      await api.post(`/enterprises/${props.enterpriseId}/points`, { ...form })
      ElMessage.success('监测点已新增')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.point-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.span-2 { grid-column: span 2; }
.point-form-grid :deep(.el-form-item__label) { padding-bottom: 5px; font-size: 11px; color: var(--ink-2); }
.point-note {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f3fbfa;
  color: #4d7a76;
  font-size: 10px;
  line-height: 1.7;
}
</style>
