export const BEIJING_REGIONS = [
  '东城区', '西城区', '朝阳区', '丰台区', '石景山区', '海淀区', '门头沟区', '房山区',
  '通州区', '顺义区', '昌平区', '大兴区', '怀柔区', '平谷区', '密云区', '延庆区'
]

export const INDUSTRY_OPTIONS = [
  '电力热力', '化工', '医药制造', '造纸', '食品加工', '表面涂装', '建材',
  '污水处理', '印刷', '机械制造', '电子制造', '其他'
]

export const PRODUCTION_STATUS = {
  NORMAL: { label: '正常生产', type: 'success', color: '#16a34a' },
  LIMITED: { label: '限产', type: 'warning', color: '#d97706' },
  STOPPED: { label: '停产', type: 'info', color: '#64748b' },
  MAINTENANCE: { label: '检修', type: 'warning', color: '#0d9488' }
}

export const ENTERPRISE_STATUS = {
  ACTIVE: { label: '在管', type: 'success' },
  INACTIVE: { label: '停用', type: 'info' }
}

export const POINT_STATUS = {
  NORMAL: { label: '正常', type: 'success' },
  MAINTENANCE: { label: '维护中', type: 'warning' },
  STOPPED: { label: '停用', type: 'info' }
}

export const POINT_TYPES = ['废气', '废水', '无组织', '其他']

export const RISK_META = {
  A: { label: 'A 级 · 低风险', color: '#22c55e', soft: '#dcfce7' },
  B: { label: 'B 级 · 一般风险', color: '#3b82f6', soft: '#dbeafe' },
  C: { label: 'C 级 · 较高风险', color: '#f59e0b', soft: '#fef3c7' },
  D: { label: 'D 级 · 高风险', color: '#ef4444', soft: '#fee2e2' }
}

export function productionMeta(value) {
  return PRODUCTION_STATUS[value] || { label: value || '未设置', type: 'info', color: '#64748b' }
}

export function enterpriseStatusMeta(value) {
  return ENTERPRISE_STATUS[value] || { label: value || '未设置', type: 'info' }
}

export function pointStatusMeta(value) {
  return POINT_STATUS[value] || { label: value || '未设置', type: 'info' }
}

export function energyColor(value, max = 100) {
  const ratio = Math.max(0, Math.min(1, Number(value || 0) / max))
  if (ratio >= 0.75) return '#ef4444'
  if (ratio >= 0.5) return '#f59e0b'
  if (ratio >= 0.25) return '#3b82f6'
  return '#22c55e'
}
