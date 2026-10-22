import * as echarts from 'echarts'
import geoJson from '../assets/beijing.json'

export const BEIJING_MAP_NAME = 'beijing-fixed-source'
let registered = false

export function ensureBeijingMap() {
  if (registered) return
  echarts.registerMap(BEIJING_MAP_NAME, geoJson)
  registered = true
}

export const BEIJING_GEO = geoJson

export const BEIJING_DISTRICT_NAMES = (geoJson.features || [])
  .map((feature) => feature.properties?.name)
  .filter(Boolean)
