<template>
  <div class="risk-map-wrap">
    <div ref="el" class="risk-map"></div>
    <div class="marker-layer">
      <button
        v-for="marker in markers"
        :key="marker.id"
        class="enterprise-marker"
        :class="{ selected: String(marker.id) === String(selectedEnterpriseId) }"
        :style="{ left: `${marker.x}px`, top: `${marker.y}px`, '--marker-color': marker.color }"
        :aria-label="`${marker.name}，${marker.riskLevel} 级风险`"
        @click.stop="emit('select-enterprise', marker.raw)"
      >
        <span class="marker-halo"></span>
        <span class="marker-core"></span>
        <span v-if="showLabel(marker)" class="marker-label">{{ marker.shortName }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BEIJING_GEO, BEIJING_MAP_NAME, ensureBeijingMap } from '../utils/beijingMap'

const props = defineProps({
  enterprises: { type: Array, default: () => [] },
  regions: { type: Array, default: () => [] },
  layer: { type: String, default: 'risk' },
  variant: { type: String, default: 'dark' },
  selectedRegion: { type: String, default: '' },
  selectedEnterpriseId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['select-region', 'select-enterprise'])

const el = ref(null)
const markers = ref([])
let chart = null
let observer = null
let zoomLevel = 1.02

const RISK_COLORS = { A: '#34d399', B: '#60a5fa', C: '#fbbf24', D: '#f87171' }

function mixColor(from, to, ratio) {
  const a = from.replace('#', '')
  const b = to.replace('#', '')
  const t = Math.max(0, Math.min(1, ratio))
  const channels = [0, 2, 4].map((i) => {
    const x = parseInt(a.slice(i, i + 2), 16)
    const y = parseInt(b.slice(i, i + 2), 16)
    return Math.round(x + (y - x) * t).toString(16).padStart(2, '0')
  })
  return `#${channels.join('')}`
}

function gradient(stops, ratio) {
  const t = Math.max(0, Math.min(1, ratio))
  const scaled = t * (stops.length - 1)
  const index = Math.min(stops.length - 2, Math.floor(scaled))
  return mixColor(stops[index], stops[index + 1], scaled - index)
}

function metricOf(stat) {
  if (!stat) return 0
  if (props.layer === 'density') return Number(stat.count || 0)
  if (props.layer === 'event') return Number(stat.eventCount || 0)
  return Number(stat.avgRisk || 0)
}

function buildOption() {
  ensureBeijingMap()
  const regionMap = new Map(props.regions.map((item) => [item.region, item]))
  const maxValue = Math.max(1, ...props.regions.map((item) => metricOf(item)))
  const light = props.variant === 'light'
  const layerStops = light
    ? props.layer === 'event'
      ? ['#e8eff3', '#f3d9a4', '#e9977e', '#d95752']
      : ['#e8eff3', '#cfe7e3', '#98d6ce', '#2fa796']
    : props.layer === 'risk'
      ? ['#0b2731', '#155e63', '#2a9d8f', '#e9c46a', '#f97316']
      : props.layer === 'density'
        ? ['#0b2731', '#114c58', '#0f9e96', '#5eead4']
        : ['#0b2731', '#4c3a1b', '#d99a2b', '#ef4444']

  const mapData = (BEIJING_GEO.features || []).map((feature) => {
    const name = feature.properties?.name
    const stat = regionMap.get(name)
    const value = metricOf(stat)
    const selected = props.selectedRegion === name
    const areaColor = stat ? gradient(layerStops, value / maxValue) : (light ? '#eef4f6' : '#0a222c')
    return {
      name,
      value,
      stat,
      itemStyle: {
        areaColor: selected ? mixColor(areaColor, light ? '#5ed3c2' : '#7ef9e8', 0.32) : areaColor,
        borderColor: selected ? (light ? '#168f7d' : '#b6fffa') : (light ? 'rgba(140, 176, 185, 0.42)' : 'rgba(77, 143, 150, 0.42)'),
        borderWidth: selected ? 2 : 1,
        shadowBlur: selected ? 16 : 0,
        shadowColor: 'rgba(94, 234, 212, 0.3)'
      },
      label: {
        color: selected ? (light ? '#0f6f61' : '#f0fffe') : (light ? 'rgba(110, 140, 150, 0.85)' : 'rgba(190, 226, 228, 0.72)'),
        fontWeight: selected ? 700 : 500
      }
    }
  })

  return {
    backgroundColor: 'transparent',
    animationDuration: 700,
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: 'rgba(6, 23, 31, 0.96)',
      borderColor: 'rgba(64, 160, 165, 0.55)',
      borderWidth: 1,
      padding: [10, 12],
      textStyle: { color: '#dcf7f4', fontSize: 11, fontFamily: 'Microsoft YaHei' },
      extraCssText: 'border-radius:10px;box-shadow:0 14px 34px rgba(0,0,0,.34);',
      formatter: (params) => {
        const stat = params.data?.stat
        if (!stat) {
          return `<b>${params.name}</b><br/><span style="color:#7fa1a6">暂无监管企业</span>`
        }
        return `<div style="font-size:12px;font-weight:700;margin-bottom:5px">${params.name}</div>
          <div style="color:#9fc4c7;line-height:1.9">
            在管企业 <b style="color:#e9fffd">${stat.count ?? 0}</b> 家 · 高风险 <b style="color:#fbbf24">${stat.highRisk ?? 0}</b> 家<br/>
            未闭环事件 <b style="color:#f87171">${stat.eventCount ?? 0}</b> 件 · 风险均分 <b>${stat.avgRisk ?? 0}</b>
          </div>`
      }
    },
    series: [
      {
        name: '区域态势',
        type: 'map',
        map: BEIJING_MAP_NAME,
        layoutCenter: ['50%', '51%'],
        layoutSize: '96%',
        zoom: zoomLevel,
        roam: true,
        scaleLimit: { min: 1, max: 4 },
        selectedMode: false,
        data: mapData,
        label: {
          show: true,
          fontSize: 8,
          color: 'rgba(190, 226, 228, 0.72)',
          formatter: (params) => String(params.name || '').replace('区', '')
        },
        labelLayout: { hideOverlap: true },
        itemStyle: {
          areaColor: light ? '#eef4f6' : '#0b2731',
          borderColor: light ? 'rgba(140, 176, 185, 0.42)' : 'rgba(77, 143, 150, 0.42)',
          borderWidth: 1
        },
        emphasis: {
          label: { show: true, color: light ? '#0f6f61' : '#ffffff', fontSize: 10, fontWeight: 700 },
          itemStyle: {
            areaColor: light ? '#d9f2ec' : '#17525b',
            borderColor: light ? '#168f7d' : '#7ef9e8',
            borderWidth: 1.5
          }
        }
      }
    ]
  }
}

function updateMarkers() {
  if (!chart) {
    markers.value = []
    return
  }
  const next = []
  for (const item of props.enterprises) {
    const lng = Number(item.longitude)
    const lat = Number(item.latitude)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) continue
    let pixel
    try {
      pixel = chart.convertToPixel({ seriesIndex: 0 }, [lng, lat])
    } catch {
      pixel = null
    }
    if (!pixel || !Number.isFinite(pixel[0]) || !Number.isFinite(pixel[1])) continue
    next.push({
      id: item.id,
      name: item.name,
      shortName: String(item.name || '').replace(/有限公司|股份有限公司|有限责任公司/g, '').slice(0, 6),
      riskLevel: item.riskLevel,
      riskScore: item.riskScore,
      color: RISK_COLORS[item.riskLevel] || '#94a3b8',
      x: pixel[0],
      y: pixel[1],
      raw: item
    })
  }
  const resolved = []
  for (const item of next) {
    let x = item.x
    let y = item.y
    for (let attempt = 0; attempt < 14; attempt++) {
      const overlaps = resolved.some((other) => Math.hypot(other.x - x, other.y - y) < 22)
      if (!overlaps) break
      const angle = (resolved.length + attempt) * 2.35
      const radius = 13 + attempt * 4
      x = item.x + Math.cos(angle) * radius
      y = item.y + Math.sin(angle) * radius
    }
    resolved.push({ ...item, x, y })
  }
  markers.value = resolved
}

function showLabel(marker) {
  return String(marker.id) === String(props.selectedEnterpriseId)
    || ['C', 'D'].includes(marker.riskLevel)
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value, null, { renderer: 'canvas' })
    chart.on('click', (params) => {
      if (params.name) emit('select-region', params.name)
    })
    chart.on('finished', updateMarkers)
    chart.on('georoam', () => window.requestAnimationFrame(updateMarkers))
  }
  chart.setOption(buildOption(), true)
  window.requestAnimationFrame(updateMarkers)
}

function reset() {
  zoomLevel = 1.02
  render()
}

function zoomIn() {
  zoomLevel = Math.min(4, zoomLevel + 0.35)
  render()
}

function zoomOut() {
  zoomLevel = Math.max(1.02, zoomLevel - 0.35)
  render()
}

defineExpose({ reset, zoomIn, zoomOut })

onMounted(() => {
  render()
  observer = new ResizeObserver(() => {
    chart?.resize()
    window.setTimeout(updateMarkers, 30)
  })
  observer.observe(el.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

watch(
  () => [props.enterprises, props.regions, props.layer, props.selectedRegion, props.selectedEnterpriseId],
  render,
  { deep: true }
)
</script>

<style scoped>
.risk-map-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
}
.risk-map {
  width: 100%;
  height: 100%;
  min-height: 320px;
}
.marker-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.enterprise-marker:hover { z-index: 9; }
.enterprise-marker {
  position: absolute;
  width: 18px;
  height: 18px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  transform: translate(-50%, -50%);
  pointer-events: auto;
  cursor: pointer;
  z-index: 2;
}
.marker-core {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--marker-color);
  border: 1.5px solid rgba(255, 255, 255, 0.7);
  transform: translate(-50%, -50%);
  box-shadow: 0 0 10px var(--marker-color);
  transition: width 0.2s ease, height 0.2s ease;
}
.marker-halo {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid var(--marker-color);
  transform: translate(-50%, -50%);
  opacity: 0;
  animation: marker-pulse 2.8s ease-out infinite;
}
.enterprise-marker:hover .marker-core,
.enterprise-marker.selected .marker-core {
  width: 11px;
  height: 11px;
}
.enterprise-marker.selected .marker-halo {
  width: 30px;
  height: 30px;
  opacity: 1;
  border-width: 1.5px;
  box-shadow: 0 0 20px var(--marker-color);
  animation-duration: 2s;
}
.marker-label {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  white-space: nowrap;
  padding: 2px 5px;
  border-radius: 4px;
  background: rgba(4, 23, 31, 0.84);
  border: 1px solid rgba(111, 217, 211, 0.18);
  color: #c9f0ec;
  font-size: 8px;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}
.enterprise-marker.selected .marker-label {
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}
@keyframes marker-pulse {
  0% { opacity: 0.65; transform: translate(-50%, -50%) scale(0.5); }
  70% { opacity: 0; transform: translate(-50%, -50%) scale(2.2); }
  100% { opacity: 0; transform: translate(-50%, -50%) scale(2.2); }
}
</style>
