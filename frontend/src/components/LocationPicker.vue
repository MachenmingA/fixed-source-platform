<template>
  <div class="location-picker">
    <div ref="el" class="picker-canvas"></div>
    <div class="picker-note">
      <span>点击地图拾取企业坐标</span>
      <b v-if="hasPoint">{{ longitude.toFixed(5) }}, {{ latitude.toFixed(5) }}</b>
      <b v-else>尚未定位</b>
    </div>
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BEIJING_GEO, BEIJING_MAP_NAME, ensureBeijingMap } from '../utils/beijingMap'

const props = defineProps({
  longitude: { type: [Number, String], default: null },
  latitude: { type: [Number, String], default: null },
  region: { type: String, default: '' }
})
const emit = defineEmits(['pick'])

const el = ref(null)
let chart = null
let observer = null

const hasPoint = computed(() => {
  const lng = Number(props.longitude)
  const lat = Number(props.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat) && lng !== 0 && lat !== 0
})

function regionAreas() {
  return (BEIJING_GEO.features || []).map((feature) => {
    const name = feature.properties?.name
    const active = props.region === name
    return {
      name,
      itemStyle: {
        areaColor: active ? '#c8f5ee' : '#eef4f5',
        borderColor: active ? '#0fb9a6' : '#d4e2e5',
        borderWidth: active ? 1.4 : 0.8
      },
      label: { color: active ? '#0a7a71' : '#9db1b6' }
    }
  })
}

function buildOption() {
  ensureBeijingMap()
  const lng = Number(props.longitude)
  const lat = Number(props.latitude)
  const point = hasPoint.value ? [lng, lat] : null
  return {
    animationDuration: 500,
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}${point ? '<br/><span style="color:#8a99ad">点击地图可修改坐标</span>' : ''}`
    },
    geo: {
      map: BEIJING_MAP_NAME,
      roam: false,
      zoom: 1.02,
      layoutCenter: ['50%', '50%'],
      layoutSize: '96%',
      itemStyle: { areaColor: '#eef4f5', borderColor: '#d4e2e5' },
      emphasis: {
        label: { show: false },
        itemStyle: { areaColor: '#d9f4ef', borderColor: '#0fb9a6' }
      },
      regions: regionAreas()
    },
    series: point ? [
      {
        type: 'effectScatter',
        coordinateSystem: 'geo',
        zlevel: 2,
        rippleEffect: { scale: 2.6, brushType: 'stroke' },
        symbolSize: 11,
        data: [{ value: point }],
        itemStyle: {
          color: '#0fb9a6',
          borderColor: '#ffffff',
          borderWidth: 2,
          shadowBlur: 16,
          shadowColor: 'rgba(15,185,166,0.5)'
        }
      }
    ] : []
  }
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
    chart.getZr().on('click', (event) => {
      const point = chart.convertFromPixel({ geoIndex: 0 }, [event.offsetX, event.offsetY])
      if (!point || point.length < 2) return
      const [lng, lat] = point.map(Number)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
      if (lng < 115.0 || lng > 117.6 || lat < 39.3 || lat > 41.2) {
        ElMessage.warning('请点击北京市辖区范围内的位置')
        return
      }
      emit('pick', {
        longitude: Number(lng.toFixed(6)),
        latitude: Number(lat.toFixed(6))
      })
    })
  }
  chart.setOption(buildOption(), true)
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

watch(() => [props.longitude, props.latitude, props.region], render, { deep: true })
</script>

<style scoped>
.location-picker {
  width: 100%;
  flex: 1 0 100%;
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
  background: #f8fbfc;
}
.picker-canvas {
  width: 100%;
  height: 250px;
}
.picker-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-top: 1px solid var(--line);
  font-size: 10px;
  color: var(--muted);
}
.picker-note b {
  font-family: "Cascadia Code", Consolas, monospace;
  font-weight: 600;
  color: var(--teal-dark);
}
</style>
