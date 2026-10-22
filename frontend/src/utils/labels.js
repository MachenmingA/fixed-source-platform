export const statusMap = {
  PENDING: { label: '待复核', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'primary' },
  PENDING_DISPATCH: { label: '待下发', type: 'warning' },
  DISPATCHED: { label: '已下发', type: 'info' },
  IN_PROGRESS: { label: '执行中', type: 'warning' },
  FEEDBACK: { label: '已反馈', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' },
  REVOKED: { label: '已撤销', type: 'info' },
  DISPATCH_FAILED: { label: '下发失败', type: 'danger' },
  NORMAL: { label: '正常', type: 'success' },
  ISSUE: { label: '发现问题', type: 'danger' },
  OVERDUE: { label: '已超时', type: 'danger' },
  DONE: { label: '已研判', type: 'success' },
  NONE: { label: '未研判', type: 'info' }
}

export const priorityMap = {
  P1: { label: 'P1 紧急', type: 'danger' },
  P2: { label: 'P2 关注', type: 'warning' },
  P3: { label: 'P3 常规', type: 'info' }
}

export const severityMap = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
}

export const riskMap = {
  A: { label: 'A 低风险', type: 'success', color: '#16a34a' },
  B: { label: 'B 一般', type: 'primary', color: '#2563eb' },
  C: { label: 'C 较高', type: 'warning', color: '#f59e0b' },
  D: { label: 'D 高风险', type: 'danger', color: '#ef4444' }
}

export function statusMeta(value) {
  return statusMap[value] || { label: value || '未知', type: 'info' }
}

export function priorityMeta(value) {
  return priorityMap[value] || { label: value || '—', type: 'info' }
}

export function severityMeta(value) {
  return severityMap[value] || { label: value || '—', type: 'info' }
}

export function riskMeta(value) {
  return riskMap[value] || { label: value || '—', type: 'info', color: '#64748b' }
}

export function fmtTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

export function fmtPercent(value) {
  const number = Number(value || 0)
  return `${number.toFixed(number % 1 === 0 ? 0 : 1)}%`
}


export function fmtNum(value) {
  if (value === null || value === undefined || value === '') return '—'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return number.toFixed(2)
}
