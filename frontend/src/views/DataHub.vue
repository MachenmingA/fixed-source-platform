<template>
  <div class="hub-page" v-loading="loading">
    <section class="hub-hero">
      <div class="hero-grid"></div>
      <div class="hero-brand">
        <div class="hero-seal">国</div>
        <div class="hero-copy">
          <div class="hero-eyebrow">NATIONAL ECO-ENVIRONMENT PUBLIC DATA REPOSITORY</div>
          <h1>国家生态环境公共数据信息库</h1>
          <p>基础数据发送与共享平台 · 企业基础信息 / 排口点位 / 污染物标准 / 监测指标 / 公开参考数据</p>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-live"><i></i> 共享节点运行中</div>
        <div class="hero-updated">最近发布 {{ overview.latestPublishedAt || '—' }}</div>
        <div class="hero-actions">
          <button class="hub-btn ghost" @click="load"><el-icon><Refresh /></el-icon>刷新</button>
          <button class="hub-btn gold" @click="copyOpenExample"><el-icon><CopyDocument /></el-icon>复制拉取示例</button>
          <button class="hub-btn" @click="openPublish()"><el-icon><UploadFilled /></el-icon>发布数据包</button>
          <button class="hub-btn primary" @click="publishAll"><el-icon><Promotion /></el-icon>一键发布全部</button>
        </div>
      </div>
    </section>

    <div class="route-bar">
      <div class="route-node">
        <span class="route-index">01</span>
        <div><b>固定污染源业务平台</b><small>数据服务出口 /api/data-service</small></div>
      </div>
      <div class="route-arrow"><span></span><i></i></div>
      <div class="route-node core">
        <span class="route-index">02</span>
        <div><b>国家生态环境公共数据信息库</b><small>编目 · 打包 · 签名 · 分发</small></div>
      </div>
      <div class="route-arrow"><span></span><i></i></div>
      <div class="route-node">
        <span class="route-index">03</span>
        <div><b>订阅平台系统</b><small>{{ overview.enabledSubscriberCount || 0 }} 个在线接收节点</small></div>
      </div>
      <div class="route-note">
        <span class="pulse-dot"></span>
        PUSH HMAC-SHA256 · PULL OpenAPI Key
      </div>
    </div>

    <div class="hub-kpis">
      <div v-for="kpi in kpis" :key="kpi.label" class="hub-kpi" :class="`tone-${kpi.tone}`">
        <div class="hub-kpi-icon"><el-icon><component :is="kpi.icon" /></el-icon></div>
        <div class="hub-kpi-copy">
          <span>{{ kpi.label }}</span>
          <strong>{{ fmt(kpi.value) }}<small>{{ kpi.unit }}</small></strong>
          <em>{{ kpi.hint }}</em>
        </div>
      </div>
    </div>

    <div class="hub-layout">
      <aside class="hub-col left-col">
        <section class="hub-card catalog-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">DATA CATALOG</span>
              <h2>数据目录</h2>
            </div>
            <span class="head-count">{{ catalogs.length }} 类</span>
          </div>
          <div class="catalog-list">
            <article
              v-for="catalog in catalogs"
              :key="catalog.code"
              class="catalog-item"
              :class="{ selected: selectedDataset === catalog.code }"
              @click="selectDataset(catalog.code)"
            >
              <div class="catalog-top">
                <span class="catalog-icon" :class="catalogTone(catalog.code)">
                  <el-icon><component :is="datasetIcon(catalog.code)" /></el-icon>
                </span>
                <div class="catalog-title">
                  <b>{{ catalog.name }}</b>
                  <span>{{ catalog.code }}</span>
                </div>
                <span class="sensitivity" :class="sensitivityMeta(catalog.sensitivity).tone">
                  {{ sensitivityMeta(catalog.sensitivity).label }}
                </span>
              </div>
              <p>{{ catalog.description }}</p>
              <div class="catalog-meta">
                <span><i></i>{{ catalog.packageCount || 0 }} 个数据包</span>
                <span><i></i>{{ catalog.subscriberCount || 0 }} 个订阅方</span>
              </div>
              <div class="catalog-foot">
                <span class="catalog-source"><el-icon><Link /></el-icon>{{ catalog.sourceApi }}</span>
                <button class="mini-action" :disabled="!canMaintain" @click.stop="openPublish(catalog.code)">
                  发布
                </button>
              </div>
            </article>
          </div>
        </section>

        <section class="hub-card open-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">OPEN DATA API</span>
              <h2>开放拉取接口</h2>
            </div>
            <span class="api-badge">PULL</span>
          </div>
          <div class="open-endpoint">
            <span>数据目录</span>
            <code>GET /open-data/v1/catalog</code>
            <button @click="copyText(`${origin}/open-data/v1/catalog`)"><el-icon><CopyDocument /></el-icon></button>
          </div>
          <div class="open-endpoint">
            <span>最新数据包</span>
            <code>GET /open-data/v1/datasets/{code}/latest</code>
            <button @click="copyText(`${origin}/open-data/v1/datasets/ENTERPRISE_MASTER/latest`)"><el-icon><CopyDocument /></el-icon></button>
          </div>
          <div class="open-key">
            <div><span>X-Open-Data-Key</span><code>{{ overview.openDataKey || '—' }}</code></div>
            <button @click="copyText(overview.openDataKey)">复制</button>
          </div>
          <p class="open-note">订阅方可凭 Key 主动拉取最近数据包；推送模式使用 AppKey + HMAC-SHA256 签名，二者共享同一份数据版本。</p>
        </section>
      </aside>

      <main class="hub-col center-col">
        <section class="hub-card flow-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">DATA SHARING PIPELINE</span>
              <h2>数据流转视图</h2>
            </div>
            <div class="flow-head-meta">
              <span class="dataset-focus">{{ selectedCatalog?.name || '请选择数据目录' }}</span>
              <span class="flow-latest">{{ latestPackage?.packageNo || '等待首个数据包' }}</span>
            </div>
          </div>
          <div class="flow-board">
            <div class="flow-source">
              <span class="node-caption">DATA PRODUCER</span>
              <div class="node-emblem"><el-icon><Connection /></el-icon></div>
              <b>固定污染源平台</b>
              <small>监测 · 事件 · 风险 · 任务</small>
              <em>/api/data-service/v1</em>
            </div>
            <div class="flow-line left-line">
              <span>标准导出</span>
              <i class="flow-dot dot-a"></i>
              <i class="flow-dot dot-b"></i>
            </div>
            <div class="hub-orb" :class="{ busy: publishing }">
              <span class="orb-ring ring-one"></span>
              <span class="orb-ring ring-two"></span>
              <span class="orb-ring ring-three"></span>
              <div class="orb-center">
                <small>NATIONAL</small>
                <b>数据仓</b>
                <em>{{ overview.packageCount || 0 }} 个包</em>
              </div>
            </div>
            <div class="flow-line right-line">
              <span>签名推送</span>
              <i class="flow-dot dot-c"></i>
              <i class="flow-dot dot-d"></i>
            </div>
            <div class="flow-targets">
              <button
                v-for="subscriber in onlineSubscribers"
                :key="subscriber.id"
                class="flow-target"
                :class="{ subscribing: supports(subscriber, selectedDataset) }"
                @click="openSubscriber(subscriber)"
              >
                <span class="target-status"></span>
                <div>
                  <b>{{ subscriber.name }}</b>
                  <small>{{ subscriber.systemType }} · {{ splitDatasets(subscriber.datasets).length }} 类订阅</small>
                </div>
                <el-icon><ArrowRight /></el-icon>
              </button>
              <div v-if="!onlineSubscribers.length" class="flow-empty">暂无启用的订阅系统</div>
            </div>
          </div>
          <div class="flow-foot">
            <span><i class="legend-dot push"></i>Push：HMAC-SHA256 验签 + 回执</span>
            <span><i class="legend-dot pull"></i>Pull：OpenAPI Key 拉取</span>
            <span class="flow-foot-right">{{ overview.dispatchCount || 0 }} 次投递 · 成功率 {{ overview.successRate || 0 }}%</span>
          </div>
        </section>

        <section class="hub-card package-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">PUBLISHED PACKAGES</span>
              <h2>最近发布数据包</h2>
            </div>
            <div class="head-actions">
              <span class="head-count">{{ overview.packageCount || 0 }} 个</span>
              <button class="link-action" @click="openPublish()">新建发布</button>
            </div>
          </div>
          <el-table :data="packages" style="width:100%" class="hub-table" @row-click="openPackage">
            <el-table-column label="数据包 / 数据集" min-width="230">
              <template #default="{ row }">
                <div class="package-cell">
                  <span class="package-mark"><el-icon><Box /></el-icon></span>
                  <div>
                    <b>{{ row.datasetName }}</b>
                    <small class="mono">{{ row.packageNo }}</small>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="记录数" width="88" align="right">
              <template #default="{ row }"><span class="record-count">{{ fmt(row.recordCount) }}</span></template>
            </el-table-column>
            <el-table-column label="分发" width="116">
              <template #default="{ row }">
                <div class="dispatch-summary">
                  <span>{{ row.successCount || 0 }}/{{ row.dispatchCount || 0 }}</span>
                  <em v-if="row.failedCount">失败 {{ row.failedCount }}</em>
                  <em v-else class="ok">已送达</em>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="104">
              <template #default="{ row }">
                <span class="status-pill" :class="packageStatus(row.status).tone">{{ packageStatus(row.status).label }}</span>
              </template>
            </el-table-column>
            <el-table-column label="发布时间" width="128">
              <template #default="{ row }"><span class="table-time">{{ row.createdAt }}</span></template>
            </el-table-column>
            <el-table-column label="" width="120" align="right">
              <template #default="{ row }">
                <button class="table-link" @click.stop="openPackage(row)">详情</button>
                <button class="table-link" @click.stop="downloadPackage(row)">下载</button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!packages.length" class="empty-state">尚未发布数据包，点击“发布数据包”开始第一次共享。</div>
        </section>

        <div class="hub-duo">
          <section class="hub-card trend-panel">
            <div class="hub-card-head compact">
              <div>
                <span class="hub-kicker">DELIVERY TREND</span>
                <h2>近 7 日投递</h2>
              </div>
              <span class="head-count">成功 / 失败</span>
            </div>
            <EChart :option="trendOption" height="164px" />
          </section>
          <section class="hub-card quality-panel">
            <div class="hub-card-head compact">
              <div>
                <span class="hub-kicker">DELIVERY QUALITY</span>
                <h2>投递质量</h2>
              </div>
              <span class="head-count">实时</span>
            </div>
            <div class="quality-body">
              <div class="quality-ring" :style="successRingStyle">
                <div><strong>{{ overview.successRate || 0 }}<small>%</small></strong><span>成功率</span></div>
              </div>
              <div class="quality-list">
                <div><span>成功投递</span><b>{{ overview.successCount || 0 }}</b></div>
                <div><span>失败待重试</span><b class="danger">{{ overview.pendingRetry || 0 }}</b></div>
                <div><span>累计数据量</span><b>{{ fmt(overview.totalRecords) }}<small>条</small></b></div>
              </div>
            </div>
            <div class="quality-foot">数据包一经发布，拉取方与推送方读取同一份版本，回执可追踪到订阅系统。</div>
          </section>
        </div>
      </main>

      <aside class="hub-col right-col">
        <section class="hub-card subscriber-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">DATA CONSUMERS</span>
              <h2>订阅系统</h2>
            </div>
            <button class="link-action" :disabled="!canMaintain" @click="openSubscriber()">+ 新增</button>
          </div>
          <div class="subscriber-list">
            <article v-for="subscriber in subscribers" :key="subscriber.id" class="subscriber-item" :class="{ offline: !subscriber.enabled }">
              <div class="subscriber-top">
                <span class="subscriber-dot"></span>
                <div class="subscriber-name">
                  <b>{{ subscriber.name }}</b>
                  <span>{{ subscriber.code }} · {{ subscriber.systemType }}</span>
                </div>
                <span class="online-label">{{ subscriber.enabled ? '在线' : '停用' }}</span>
              </div>
              <div class="subscriber-datasets">
                <span v-for="code in splitDatasets(subscriber.datasets)" :key="code">{{ shortDataset(code) }}</span>
                <span v-if="!splitDatasets(subscriber.datasets).length" class="muted">未订阅目录</span>
              </div>
              <div class="subscriber-endpoint mono">{{ subscriber.endpoint }}</div>
              <div class="subscriber-foot">
                <span>成功 {{ subscriber.successCount || 0 }} / {{ subscriber.dispatchCount || 0 }}</span>
                <span v-if="subscriber.failedCount" class="danger">失败 {{ subscriber.failedCount }}</span>
                <span v-else class="ok">回执正常</span>
                <button class="mini-action" @click="openSubscriber(subscriber)">维护</button>
              </div>
            </article>
          </div>
        </section>

        <section class="hub-card receipt-panel">
          <div class="hub-card-head">
            <div>
              <span class="hub-kicker">DELIVERY RECEIPTS</span>
              <h2>最近投递回执</h2>
            </div>
            <span class="head-count">{{ overview.dispatchCount || 0 }} 次</span>
          </div>
          <div class="receipt-list">
            <article v-for="dispatch in dispatches" :key="dispatch.id" class="receipt-item">
              <span class="receipt-dot" :class="dispatch.status.toLowerCase()"></span>
              <div class="receipt-main">
                <b>{{ dispatch.subscriberName }}</b>
                <span class="mono">{{ dispatch.packageNo }}</span>
              </div>
              <div class="receipt-meta">
                <span :class="dispatch.status === 'SENT' ? 'ok' : 'danger'">
                  HTTP {{ dispatch.httpStatus || '—' }} · {{ dispatch.latencyMs || 0 }}ms
                </span>
                <small>{{ String(dispatch.sentAt || '').slice(5) }}</small>
                <button v-if="dispatch.status === 'FAILED'" class="retry-link" @click="retryDispatch(dispatch)">重试</button>
              </div>
            </article>
            <div v-if="!dispatches.length" class="empty-state">暂无投递记录</div>
          </div>
        </section>
      </aside>
    </div>

    <footer class="hub-footer">
      <span><i class="pulse-dot"></i> 国家生态环境公共数据信息库 · 固定污染源专题共享节点</span>
      <span>数据供给：净源智研固定污染源平台 · 版本 v1.0 · 仅用于毕业设计模拟演示</span>
    </footer>

    <el-dialog v-model="publishVisible" title="发布数据包并分发" width="590px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="选择数据目录">
          <el-select v-model="publishForm.datasetCode" style="width:100%">
            <el-option v-for="catalog in catalogs" :key="catalog.code" :label="`${catalog.name}（${catalog.code}）`" :value="catalog.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="publishForm.datasetCode === 'MONITORING_HOURLY'" label="监测业务日期">
          <el-date-picker v-model="publishForm.bizDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <div v-if="selectedPublishCatalog" class="publish-preview">
          <div><span>数据来源</span><b>{{ selectedPublishCatalog.sourceApi }}</b></div>
          <div><span>敏感级别</span><b>{{ sensitivityMeta(selectedPublishCatalog.sensitivity).label }}</b></div>
          <div><span>自动接收方</span><b>{{ publishTargets.length }} 个</b></div>
        </div>
        <div class="publish-targets">
          <span v-for="sub in publishTargets" :key="sub.id">{{ sub.name }}</span>
          <span v-if="!publishTargets.length" class="muted">当前目录暂无启用订阅方，发布后将保留为可拉取数据包。</span>
        </div>
        <div class="publish-note">
          发布动作会先调用业务平台标准数据接口生成数据包，计算 SHA-256 指纹，并按订阅关系逐个推送；每次推送写入 HMAC 签名、HTTP 状态、延迟和回执。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publishNow">发布并自动分发</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="subscriberVisible" :title="editingSubscriberId ? '维护订阅系统' : '新增订阅系统'" width="690px" destroy-on-close>
      <el-form label-position="top">
        <div class="sub-form-grid">
          <el-form-item label="系统编码">
            <el-input v-model="subscriberForm.code" :disabled="!!editingSubscriberId" placeholder="例如 PROV-ENV" />
          </el-form-item>
          <el-form-item label="系统名称">
            <el-input v-model="subscriberForm.name" placeholder="例如 省级生态环境执法平台" />
          </el-form-item>
          <el-form-item label="系统级别">
            <el-select v-model="subscriberForm.systemType" style="width:100%">
              <el-option v-for="item in ['国家级','省级','市级','区级']" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="接收地址">
            <el-input v-model="subscriberForm.endpoint" placeholder="/mock-consumer/v1/xxx/ingest" />
          </el-form-item>
          <el-form-item label="AppKey">
            <el-input v-model="subscriberForm.appKey" placeholder="接收方应用标识" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input v-model="subscriberForm.appSecret" show-password placeholder="留空则不修改原密钥" />
          </el-form-item>
          <el-form-item label="订阅数据目录" class="span-2">
            <el-select v-model="subscriberForm.datasets" multiple collapse-tags style="width:100%" placeholder="选择需要接收的数据目录">
              <el-option v-for="catalog in catalogs" :key="catalog.code" :label="catalog.name" :value="catalog.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用推送" class="span-2">
            <el-switch v-model="subscriberForm.enabled" active-text="参与自动分发" inactive-text="暂停接收" />
          </el-form-item>
        </div>
        <div class="publish-note">
          订阅关系决定数据目录的自动分发对象。接收端需使用 AppSecret 验证请求签名；生产环境建议每个订阅系统独立密钥并定期轮换。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="subscriberVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSubscriber" @click="saveSubscriber">保存订阅配置</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="packageVisible" size="770px" :with-header="false">
      <div v-if="packageDetail" class="package-drawer">
        <div class="drawer-head">
          <div>
            <span class="hub-kicker">PUBLISHED DATA PACKAGE</span>
            <h2>{{ packageDetail.datasetName }}</h2>
            <span class="mono">{{ packageDetail.packageNo }}</span>
          </div>
          <div class="drawer-head-actions">
            <span class="status-pill" :class="packageStatus(packageDetail.status).tone">{{ packageStatus(packageDetail.status).label }}</span>
            <el-button size="small" @click="downloadPackage(packageDetail)"><el-icon><Download /></el-icon>下载 JSON</el-button>
            <el-button size="small" @click="packageVisible = false">关闭</el-button>
          </div>
        </div>
        <div class="drawer-meta">
          <div><span>数据集编码</span><b class="mono">{{ packageDetail.datasetCode }}</b></div>
          <div><span>记录数</span><b>{{ fmt(packageDetail.recordCount) }} 条</b></div>
          <div><span>业务日期</span><b>{{ packageDetail.bizDate || '快照' }}</b></div>
          <div><span>发布时间</span><b>{{ packageDetail.createdAt }}</b></div>
          <div><span>发布人</span><b>{{ packageDetail.createdBy }}</b></div>
          <div><span>数据体积</span><b>{{ fmtBytes(packageDetail.payloadSize) }}</b></div>
        </div>
        <div class="drawer-hash">
          <span>SHA-256 数据指纹</span>
          <code>{{ packageDetail.payloadHash }}</code>
        </div>
        <div class="drawer-section-title">
          <span>投递回执</span>
          <small>{{ packageDetail.dispatches?.length || 0 }} 个订阅系统</small>
        </div>
        <div class="drawer-dispatch-list">
          <div v-for="dispatch in packageDetail.dispatches || []" :key="dispatch.id" class="drawer-dispatch">
            <span class="receipt-dot" :class="dispatch.status.toLowerCase()"></span>
            <div><b>{{ dispatch.subscriberName }}</b><span class="mono">{{ dispatch.dispatchNo }}</span></div>
            <span :class="dispatch.status === 'SENT' ? 'ok' : 'danger'">{{ dispatch.status === 'SENT' ? '接收成功' : '投递失败' }}</span>
            <span>HTTP {{ dispatch.httpStatus || '—' }} · {{ dispatch.latencyMs || 0 }}ms</span>
            <span v-if="dispatch.receiptNo" class="mono receipt-no">{{ dispatch.receiptNo }}</span>
            <button v-if="dispatch.status === 'FAILED'" class="retry-link" @click="retryDispatch(dispatch)">重试</button>
          </div>
          <div v-if="!(packageDetail.dispatches || []).length" class="empty-state">该数据包未产生投递（无启用订阅方）</div>
        </div>
        <div class="drawer-section-title">
          <span>数据包样本</span>
          <small>完整数据请下载 JSON</small>
        </div>
        <pre class="payload-preview">{{ packageDetail.payloadPreview }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, Box, CircleCheck, Connection, CopyDocument, DataAnalysis, DataLine, Document,
  Download, Link, List, OfficeBuilding, Promotion, Refresh, Share, TrendCharts, UploadFilled, Warning
} from '@element-plus/icons-vue'
import api from '../api'
import EChart from '../components/EChart.vue'
import { hasRole } from '../store'

const overview = ref({})
const loading = ref(false)
const publishing = ref(false)
const publishVisible = ref(false)
const publishForm = reactive({ datasetCode: '', bizDate: today() })
const selectedDataset = ref('')
const packageVisible = ref(false)
const packageDetail = ref(null)
const subscriberVisible = ref(false)
const editingSubscriberId = ref(null)
const savingSubscriber = ref(false)
const origin = typeof window === 'undefined' ? 'http://localhost:8088' : window.location.origin
let timer = null

const canMaintain = computed(() => hasRole('reviewer', 'admin'))
const catalogs = computed(() => overview.value.catalogs || [])
const subscribers = computed(() => overview.value.subscribers || [])
const packages = computed(() => overview.value.packages || [])
const dispatches = computed(() => overview.value.dispatches || [])
const onlineSubscribers = computed(() => subscribers.value.filter((item) => item.enabled))
const latestPackage = computed(() => overview.value.latestPackage || packages.value[0] || null)
const selectedCatalog = computed(() => catalogs.value.find((item) => item.code === selectedDataset.value) || catalogs.value[0] || null)
const selectedPublishCatalog = computed(() => catalogs.value.find((item) => item.code === publishForm.datasetCode) || null)
const publishTargets = computed(() => subscribers.value.filter((item) =>
  item.enabled && splitDatasets(item.datasets).includes(publishForm.datasetCode)
))

const subscriberForm = reactive({
  code: '',
  name: '',
  systemType: '市级',
  endpoint: '',
  appKey: '',
  appSecret: '',
  datasets: [],
  enabled: true
})

const kpis = computed(() => [
  {
    label: '数据目录',
    value: overview.value.catalogCount || 0,
    unit: '类',
    hint: `企业、点位、标准、监测与公开参考`,
    icon: Box,
    tone: 'blue'
  },
  {
    label: '接入订阅系统',
    value: overview.value.enabledSubscriberCount || 0,
    unit: '个',
    hint: `登记 ${overview.value.subscriberCount || 0} 个 · 在线 ${overview.value.enabledSubscriberCount || 0} 个`,
    icon: Share,
    tone: 'teal'
  },
  {
    label: '已发布数据包',
    value: overview.value.packageCount || 0,
    unit: '个',
    hint: `累计 ${fmt(overview.value.totalRecords)} 条记录`,
    icon: Document,
    tone: 'navy'
  },
  {
    label: '累计投递',
    value: overview.value.dispatchCount || 0,
    unit: '次',
    hint: `今日 ${overview.value.todayDispatch || 0} 次`,
    icon: Promotion,
    tone: 'indigo'
  },
  {
    label: '投递成功率',
    value: overview.value.successRate || 0,
    unit: '%',
    hint: `成功 ${overview.value.successCount || 0} 次`,
    icon: CircleCheck,
    tone: 'green'
  },
  {
    label: '待重试',
    value: overview.value.pendingRetry || 0,
    unit: '次',
    hint: overview.value.pendingRetry ? '存在失败回执，可在右侧重试' : '暂无失败投递',
    icon: Warning,
    tone: overview.value.pendingRetry ? 'red' : 'slate'
  }
])

const successRingStyle = computed(() => {
  const percent = Math.max(0, Math.min(100, Number(overview.value.successRate || 0)))
  return { background: `conic-gradient(#168f7d ${percent}%, #e7f0f4 ${percent}% 100%)` }
})

const trendOption = computed(() => {
  const data = overview.value.dispatchTrend || []
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(12, 36, 61, .96)',
      borderColor: 'rgba(22, 143, 125, .45)',
      textStyle: { color: '#e8f7fa', fontSize: 11 }
    },
    legend: { right: 4, top: 0, itemWidth: 9, itemHeight: 9, textStyle: { color: '#7b91a6', fontSize: 10 } },
    grid: { left: 28, right: 10, top: 28, bottom: 20 },
    xAxis: {
      type: 'category',
      data: data.map((item) => String(item.bizDay).slice(5)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dfe9ef' } },
      axisLabel: { color: '#8ba0b4', fontSize: 9 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef4f7', type: 'dashed' } },
      axisLabel: { color: '#8ba0b4', fontSize: 9 }
    },
    series: [
      {
        name: '成功投递',
        type: 'bar',
        barWidth: 14,
        data: data.map((item) => item.success),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: '#1ab7a4' }, { offset: 1, color: '#b8efe9' }]
          }
        }
      },
      {
        name: '失败',
        type: 'line',
        data: data.map((item) => item.failed),
        symbol: 'circle',
        symbolSize: 5,
        smooth: true,
        lineStyle: { color: '#ed7b76', width: 2 },
        itemStyle: { color: '#ed7b76' }
      }
    ]
  }
})

const datasetIconMap = {
  ENTERPRISE_MASTER: OfficeBuilding,
  MONITOR_POINT_MASTER: Connection,
  POLLUTANT_STANDARD: DataAnalysis,
  MONITORING_HOURLY: DataLine,
  MONITORING_DAILY: TrendCharts,
  KEY_DISCHARGE_UNIT: List,
  STANDARD_LIBRARY: Document
}

function datasetIcon(code) {
  return datasetIconMap[code] || Box
}

function catalogTone(code) {
  return {
    ENTERPRISE_MASTER: 'blue',
    MONITOR_POINT_MASTER: 'violet',
    POLLUTANT_STANDARD: 'amber',
    MONITORING_HOURLY: 'teal',
    MONITORING_DAILY: 'green',
    KEY_DISCHARGE_UNIT: 'violet',
    STANDARD_LIBRARY: 'amber'
  }[code] || 'blue'
}

function sensitivityMeta(value) {
  return {
    PUBLIC: { label: '公开', tone: 'public' },
    CONTROLLED: { label: '受控共享', tone: 'controlled' },
    INNER: { label: '内部共享', tone: 'inner' }
  }[value] || { label: value || '内部', tone: 'inner' }
}

function packageStatus(value) {
  return {
    PUBLISHED: { label: '待分发', tone: 'muted' },
    DISTRIBUTED: { label: '已分发', tone: 'success' },
    PARTIAL: { label: '部分成功', tone: 'warning' },
    FAILED: { label: '分发失败', tone: 'danger' }
  }[value] || { label: value || '—', tone: 'muted' }
}

function splitDatasets(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return String(value).split(',').map((item) => item.trim()).filter(Boolean)
}

function shortDataset(code) {
  return {
    ENTERPRISE_MASTER: '企业基础',
    MONITOR_POINT_MASTER: '排口点位',
    POLLUTANT_STANDARD: '污染物标准',
    MONITORING_HOURLY: '小时明细',
    MONITORING_DAILY: '日指标',
    KEY_DISCHARGE_UNIT: '公开名录',
    STANDARD_LIBRARY: '标准依据'
  }[code] || code
}

function supports(subscriber, code) {
  if (!code) return false
  return splitDatasets(subscriber.datasets).includes(code)
}

function fmt(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function fmtBytes(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function selectDataset(code) {
  selectedDataset.value = code
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/data-hub/overview')
    overview.value = data
    if (!selectedDataset.value && (data.catalogs || []).length) {
      selectedDataset.value = data.catalogs[0].code
    }
  } finally {
    loading.value = false
  }
}

function guardMaintainer() {
  if (canMaintain.value) return true
  ElMessage.warning('当前账号只读，请使用审核人员或管理员账号发布数据包')
  return false
}

function openPublish(code) {
  if (!guardMaintainer()) return
  publishForm.datasetCode = code || selectedDataset.value || catalogs.value[0]?.code || ''
  publishForm.bizDate = today()
  publishVisible.value = true
}

async function publishNow() {
  if (!publishForm.datasetCode) {
    ElMessage.warning('请选择数据目录')
    return
  }
  publishing.value = true
  try {
    const { data } = await api.post('/data-hub/packages/publish', {
      datasetCode: publishForm.datasetCode,
      bizDate: publishForm.datasetCode === 'MONITORING_HOURLY' ? publishForm.bizDate : ''
    })
    const sent = (data.dispatches || []).filter((item) => item.status === 'SENT').length
    ElMessage.success(`数据包已发布：${fmt(data.recordCount)} 条记录，${sent}/${(data.dispatches || []).length} 个订阅方接收成功`)
    publishVisible.value = false
    await load()
    await openPackage(data)
  } finally {
    publishing.value = false
  }
}

async function publishAll() {
  if (!guardMaintainer()) return
  try {
    await ElMessageBox.confirm(
      '将依次发布全部 5 类数据目录，并按订阅关系自动推送到在线接收系统。是否继续？',
      '一键发布全部数据包',
      { confirmButtonText: '发布全部', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  publishing.value = true
  try {
    const { data } = await api.post('/data-hub/packages/publish-all', {
      datasetCodes: catalogs.value.map((item) => item.code),
      bizDate: today()
    })
    ElMessage.success(`已发布 ${data.published || 0} 个数据包，失败 ${data.failed || 0} 个`)
    await load()
  } finally {
    publishing.value = false
  }
}

async function openPackage(row) {
  if (!row?.id) return
  const { data } = await api.get(`/data-hub/packages/${row.id}`)
  packageDetail.value = data
  packageVisible.value = true
}

async function downloadPackage(row) {
  const response = await api.get(`/data-hub/packages/${row.id}/payload`, { responseType: 'blob' })
  const url = URL.createObjectURL(new Blob([response.data], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.packageNo || 'data-package'}.json`
  link.click()
  URL.revokeObjectURL(url)
}

async function retryDispatch(dispatch) {
  if (!guardMaintainer()) return
  const { data } = await api.post(`/data-hub/dispatches/${dispatch.id}/retry`)
  if (data.status === 'SENT') ElMessage.success('重试成功，接收方已回执')
  else ElMessage.warning('重试仍未成功，请检查接收端地址与密钥')
  await load()
  if (packageVisible.value && packageDetail.value?.id) await openPackage(packageDetail.value)
}

function openSubscriber(subscriber) {
  if (!guardMaintainer()) return
  editingSubscriberId.value = subscriber?.id || null
  if (subscriber) {
    subscriberForm.code = subscriber.code
    subscriberForm.name = subscriber.name
    subscriberForm.systemType = subscriber.systemType || '市级'
    subscriberForm.endpoint = subscriber.endpoint || ''
    subscriberForm.appKey = subscriber.appKey || ''
    subscriberForm.appSecret = ''
    subscriberForm.datasets = splitDatasets(subscriber.datasets)
    subscriberForm.enabled = !!subscriber.enabled
  } else {
    const suffix = Math.random().toString(36).slice(2, 8).toUpperCase()
    subscriberForm.code = ''
    subscriberForm.name = ''
    subscriberForm.systemType = '市级'
    subscriberForm.endpoint = '/mock-consumer/v1/custom/ingest'
    subscriberForm.appKey = `custom-${suffix.toLowerCase()}-key`
    subscriberForm.appSecret = ''
    subscriberForm.datasets = []
    subscriberForm.enabled = true
  }
  subscriberVisible.value = true
}

async function saveSubscriber() {
  if (!subscriberForm.code || !subscriberForm.name || !subscriberForm.endpoint || !subscriberForm.appKey) {
    ElMessage.warning('请填写系统编码、名称、接收地址和 AppKey')
    return
  }
  if (!editingSubscriberId.value && !subscriberForm.appSecret) {
    ElMessage.warning('新增订阅系统需要设置 AppSecret')
    return
  }
  savingSubscriber.value = true
  try {
    const payload = {
      ...subscriberForm,
      datasets: subscriberForm.datasets
    }
    if (editingSubscriberId.value) {
      await api.put(`/data-hub/subscribers/${editingSubscriberId.value}`, payload)
      ElMessage.success('订阅配置已更新')
    } else {
      await api.post('/data-hub/subscribers', payload)
      ElMessage.success('订阅系统已新增')
    }
    subscriberVisible.value = false
    await load()
  } finally {
    savingSubscriber.value = false
  }
}

async function copyOpenExample() {
  await copyText(`curl -H "X-Open-Data-Key: ${overview.value.openDataKey}" ${origin}/open-data/v1/catalog`)
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.info(text)
  }
}

onMounted(async () => {
  await load()
  timer = window.setInterval(() => {
    if (!publishVisible.value && !subscriberVisible.value && !packageVisible.value) load()
  }, 60000)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
})
</script>

<style scoped>
.hub-page {
  --gov-navy: #0b2f56;
  --gov-blue: #165a9a;
  --gov-teal: #168f7d;
  --gov-gold: #c7a34b;
  position: relative;
  padding-bottom: 8px;
}
.hub-hero {
  position: relative;
  min-height: 158px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 25px 28px;
  overflow: hidden;
  border-radius: 20px;
  color: #f1fbff;
  background:
    radial-gradient(circle at 84% 10%, rgba(48, 182, 187, 0.2), transparent 34%),
    linear-gradient(120deg, #071f3d 0%, #0c3b65 52%, #0b5b67 100%);
  box-shadow: 0 14px 34px rgba(11, 47, 86, 0.18);
}
.hub-hero::after {
  content: "";
  position: absolute;
  right: 17%;
  bottom: -76px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  border: 1px solid rgba(197, 238, 238, 0.12);
  box-shadow: 0 0 0 34px rgba(197, 238, 238, 0.035), 0 0 0 68px rgba(197, 238, 238, 0.025);
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(116, 214, 211, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(116, 214, 211, 0.07) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: linear-gradient(90deg, #000, transparent 78%);
  pointer-events: none;
}
.hero-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 17px;
}
.hero-seal {
  width: 62px;
  height: 62px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #d5b561, #a98136);
  border: 1px solid rgba(255, 247, 211, 0.6);
  color: #102f45;
  font-size: 29px;
  font-weight: 800;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.2);
  flex-shrink: 0;
}
.hero-eyebrow {
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  letter-spacing: 2.2px;
  color: #86d8d0;
}
.hero-copy h1 {
  margin: 7px 0 5px;
  font-size: 26px;
  line-height: 1.25;
  letter-spacing: 1px;
  font-weight: 700;
  color: #f2fdff;
}
.hero-copy p {
  margin: 0;
  color: rgba(218, 243, 246, 0.64);
  font-size: 11px;
}
.hero-side {
  position: relative;
  z-index: 1;
  min-width: 350px;
  text-align: right;
}
.hero-live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid rgba(112, 225, 215, 0.28);
  background: rgba(111, 226, 214, 0.09);
  color: #8ef1e6;
  font-size: 10px;
}
.hero-live i,
.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #59e3d5;
  box-shadow: 0 0 0 4px rgba(89, 227, 213, 0.12);
  animation: hub-pulse 2s ease-in-out infinite;
}
.hero-updated {
  margin-top: 8px;
  color: rgba(206, 238, 241, 0.48);
  font-size: 9px;
}
.hero-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 12px;
}
.hub-btn {
  height: 32px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.17);
  background: rgba(255, 255, 255, 0.08);
  color: #dff7f6;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.hub-btn:hover { background: rgba(255, 255, 255, 0.14); }
.hub-btn.primary {
  border-color: rgba(93, 229, 215, 0.45);
  background: linear-gradient(135deg, #2bc9b8, #168f7d);
  color: #052a2b;
  font-weight: 700;
}
.hub-btn.gold {
  border-color: rgba(237, 205, 121, 0.4);
  color: #f4d98f;
}
.hub-btn.ghost { color: #b4dadd; }

.route-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 13px;
  padding: 11px 17px;
  border: 1px solid #dbe8ef;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 6px 20px rgba(22, 64, 92, 0.035);
}
.route-node { display: flex; align-items: center; gap: 9px; }
.route-index {
  width: 23px;
  height: 23px;
  border-radius: 7px;
  background: #eef5f9;
  color: #6d879a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  font-weight: 700;
}
.route-node.core .route-index { background: #e3f7f2; color: #168f7d; }
.route-node b { display: block; color: #1b3650; font-size: 10px; font-weight: 700; }
.route-node small { display: block; margin-top: 2px; color: #8ba0ad; font-size: 8px; }
.route-arrow { display: flex; flex-direction: column; gap: 3px; width: 54px; align-items: center; position: relative; }
.route-arrow span { display: block; width: 100%; height: 1px; background: linear-gradient(90deg, #c5dbe3, #168f7d20); }
.route-arrow i {
  width: 4px;
  height: 4px;
  border-right: 1px solid #7eb8bd;
  border-top: 1px solid #7eb8bd;
  transform: rotate(45deg);
  position: absolute;
  right: 0;
  top: -2px;
}
.route-note {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #7e98a5;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  letter-spacing: 0.2px;
}

.hub-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 11px;
  margin-top: 13px;
}
.hub-kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 83px;
  padding: 12px 13px;
  border: 1px solid #e1eaf0;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 7px 20px rgba(25, 63, 88, 0.045);
  overflow: hidden;
}
.hub-kpi::after {
  content: "";
  position: absolute;
  right: -22px;
  top: -26px;
  width: 78px;
  height: 78px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.055;
}
.hub-kpi-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: currentColor;
  color: #fff;
  font-size: 16px;
  flex-shrink: 0;
  box-shadow: 0 6px 14px currentColor;
  opacity: 0.92;
}
.hub-kpi-copy { min-width: 0; }
.hub-kpi-copy span { display: block; color: #7d93a3; font-size: 9px; }
.hub-kpi-copy strong {
  display: block;
  margin-top: 2px;
  color: #173a51;
  font-size: 24px;
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.hub-kpi-copy strong small { margin-left: 2px; font-size: 9px; font-weight: 400; color: #91a2af; }
.hub-kpi-copy em { display: block; margin-top: 2px; color: #9aabb7; font-size: 8px; font-style: normal; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tone-blue { color: #367fca; }
.tone-teal { color: #168f7d; }
.tone-navy { color: #1b4c6b; }
.tone-indigo { color: #5f72c7; }
.tone-green { color: #2f976a; }
.tone-red { color: #d96862; }
.tone-slate { color: #899ba8; }
.tone-blue .hub-kpi-icon { background: linear-gradient(135deg, #4a91d8, #2d6da9); box-shadow: 0 7px 14px rgba(54,127,202,.2); }
.tone-teal .hub-kpi-icon { background: linear-gradient(135deg, #2bb5a4, #148675); box-shadow: 0 7px 14px rgba(22,143,125,.2); }
.tone-navy .hub-kpi-icon { background: linear-gradient(135deg, #356985, #1a4660); box-shadow: 0 7px 14px rgba(27,76,107,.2); }
.tone-indigo .hub-kpi-icon { background: linear-gradient(135deg, #7889dc, #5868b5); box-shadow: 0 7px 14px rgba(95,114,199,.2); }
.tone-green .hub-kpi-icon { background: linear-gradient(135deg, #59b88b, #2f9168); box-shadow: 0 7px 14px rgba(47,151,106,.2); }
.tone-red .hub-kpi-icon { background: linear-gradient(135deg, #e48a84, #c45c56); box-shadow: 0 7px 14px rgba(217,104,98,.2); }
.tone-slate .hub-kpi-icon { background: linear-gradient(135deg, #a8b6bf, #7e929e); box-shadow: 0 7px 14px rgba(137,155,168,.16); }

.hub-layout {
  display: grid;
  grid-template-columns: 292px minmax(480px, 1fr) 332px;
  gap: 14px;
  margin-top: 14px;
  align-items: start;
}
.hub-col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.hub-card {
  border: 1px solid #dee9ef;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(25, 63, 88, 0.045);
  overflow: hidden;
}
.hub-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px 11px;
}
.hub-card-head.compact { padding-bottom: 4px; }
.hub-kicker {
  display: block;
  color: #96a9b7;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 7px;
  letter-spacing: 1.4px;
}
.hub-card-head h2 {
  margin: 3px 0 0;
  color: #17384e;
  font-size: 14px;
  font-weight: 700;
}
.head-count { color: #94a6b3; font-size: 9px; white-space: nowrap; }
.head-actions { display: flex; align-items: center; gap: 8px; }
.link-action {
  border: 0;
  background: transparent;
  color: #168f7d;
  font-size: 9px;
  cursor: pointer;
}
.link-action:disabled { color: #b6c4cc; cursor: not-allowed; }

.catalog-list { padding: 0 9px 9px; }
.catalog-item {
  padding: 10px 10px 9px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.catalog-item:hover { background: #f7fbfd; }
.catalog-item.selected {
  border-color: #b8e3db;
  background: linear-gradient(180deg, #f0fbf9, #f8fdfc);
  box-shadow: inset 3px 0 0 #168f7d;
}
.catalog-top { display: flex; align-items: center; gap: 8px; }
.catalog-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  flex-shrink: 0;
}
.catalog-icon.blue { background: linear-gradient(135deg, #3b82d0, #2165a7); }
.catalog-icon.teal { background: linear-gradient(135deg, #34b6a7, #168f7d); }
.catalog-icon.amber { background: linear-gradient(135deg, #ecc566, #d79d35); }
.catalog-icon.violet { background: linear-gradient(135deg, #8a92d9, #6570bd); }
.catalog-icon.green { background: linear-gradient(135deg, #64bb8d, #3b9568); }
.catalog-title { flex: 1; min-width: 0; }
.catalog-title b { display: block; color: #1f3b4f; font-size: 11px; }
.catalog-title span {
  display: block;
  margin-top: 2px;
  color: #9aadb9;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 7px;
}
.sensitivity { padding: 2px 5px; border-radius: 4px; font-size: 7px; white-space: nowrap; }
.sensitivity.controlled { color: #a1641a; background: #fff5e5; }
.sensitivity.inner { color: #6a7e8c; background: #f1f5f8; }
.sensitivity.public { color: #2c8c65; background: #eaf8f1; }
.catalog-item p {
  margin: 7px 0 0;
  color: #8499a6;
  font-size: 9px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.catalog-meta { display: flex; gap: 10px; margin-top: 7px; color: #7d95a2; font-size: 8px; }
.catalog-meta span { display: inline-flex; align-items: center; gap: 4px; }
.catalog-meta i { width: 4px; height: 4px; border-radius: 50%; background: #b7c8d0; }
.catalog-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; padding-top: 7px; border-top: 1px dashed #edf3f6; }
.catalog-source {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 3px;
  color: #a2b2bc;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 7px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mini-action {
  border: 1px solid #d5e6e8;
  border-radius: 5px;
  background: #f7fcfb;
  color: #168f7d;
  padding: 3px 8px;
  font-size: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.mini-action:hover { border-color: #8ecfc7; }
.mini-action:disabled { color: #b8c4ca; border-color: #e7eef2; background: #fafcfd; cursor: not-allowed; }

.open-panel { padding-bottom: 14px; }
.api-badge {
  padding: 3px 7px;
  border: 1px solid #cbe8e3;
  border-radius: 999px;
  color: #168f7d;
  background: #effaf7;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
}
.open-endpoint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 14px 7px;
  padding: 8px;
  border-radius: 7px;
  background: #f7fafc;
  border: 1px solid #edf3f6;
}
.open-endpoint span { width: 47px; color: #849aa7; font-size: 8px; flex-shrink: 0; }
.open-endpoint code {
  flex: 1;
  min-width: 0;
  color: #3c657b;
  font-family: "Cascadia Code", Consolas, monospace;
  font-size: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.open-endpoint button,
.open-key button {
  border: 0;
  background: transparent;
  color: #9cb1bc;
  cursor: pointer;
  padding: 2px;
}
.open-key {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 10px 14px 0;
  padding: 9px 10px;
  border: 1px dashed #d5e7e7;
  border-radius: 7px;
  background: #f7fdfb;
}
.open-key div { min-width: 0; }
.open-key span { display: block; color: #94a9b4; font-size: 7px; }
.open-key code { display: block; margin-top: 3px; color: #168f7d; font-family: "Cascadia Code", Consolas, monospace; font-size: 9px; }
.open-key button { color: #168f7d; font-size: 8px; border: 1px solid #c9e8e2; border-radius: 4px; background: #fff; padding: 3px 7px; }
.open-note { margin: 10px 14px 0; color: #97a9b4; font-size: 8px; line-height: 1.6; }

.flow-panel { min-height: 328px; }
.flow-head-meta { display: flex; align-items: center; gap: 8px; }
.dataset-focus { color: #168f7d; font-size: 9px; font-weight: 600; }
.flow-latest { color: #a0b0ba; font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; }
.flow-board {
  display: grid;
  grid-template-columns: minmax(105px, 0.83fr) 48px 132px 48px minmax(170px, 1.2fr);
  align-items: center;
  gap: 0;
  margin: 4px 15px 0;
  padding: 19px 10px 20px;
  border: 1px solid #e5edf2;
  border-radius: 11px;
  background:
    radial-gradient(circle at 50% 50%, rgba(42, 184, 169, 0.075), transparent 42%),
    linear-gradient(180deg, #fbfdfe, #f7fbfc);
  overflow: hidden;
}
.flow-source {
  text-align: center;
  border: 1px solid #dce9ef;
  border-radius: 10px;
  padding: 12px 8px 10px;
  background: #fff;
  box-shadow: 0 5px 12px rgba(28, 73, 98, 0.04);
}
.node-caption { display: block; margin-bottom: 7px; color: #a2b3bd; font-family: "Cascadia Code", Consolas, monospace; font-size: 6px; letter-spacing: 0.8px; }
.node-emblem {
  width: 31px;
  height: 31px;
  margin: 0 auto 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: #fff;
  background: linear-gradient(135deg, #357fc0, #1d5d91);
  font-size: 15px;
}
.flow-source b { display: block; color: #24445a; font-size: 10px; }
.flow-source small { display: block; margin-top: 3px; color: #8da1ae; font-size: 8px; line-height: 1.45; }
.flow-source em { display: block; margin-top: 6px; color: #a9b8c1; font-family: "Cascadia Code", Consolas, monospace; font-size: 6px; font-style: normal; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.flow-line { position: relative; height: 64px; display: flex; align-items: center; justify-content: center; }
.flow-line::before {
  content: "";
  width: 100%;
  height: 1px;
  background: repeating-linear-gradient(90deg, #b7d5d8 0 8px, transparent 8px 14px);
}
.flow-line span {
  position: absolute;
  top: 6px;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  color: #9eb2ba;
  font-size: 7px;
}
.flow-dot {
  position: absolute;
  top: 31px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #22af9e;
  box-shadow: 0 0 8px rgba(34, 175, 158, 0.7);
  animation: flow-move 2.8s linear infinite;
}
.dot-a { animation-delay: 0.2s; }
.dot-b { animation-delay: 1.5s; }
.dot-c { animation-delay: 0.7s; }
.dot-d { animation-delay: 2.1s; }
.hub-orb {
  position: relative;
  width: 126px;
  height: 126px;
  margin: 0 auto;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(27, 166, 154, 0.15), rgba(17, 72, 87, 0.05) 60%, transparent 70%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.orb-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(25, 174, 156, 0.25);
}
.ring-one { inset: 11px; border-style: dashed; animation: orb-spin 20s linear infinite; }
.ring-two { inset: 24px; border-color: rgba(30, 177, 157, 0.16); }
.ring-three { inset: 0; border-color: rgba(30, 177, 157, 0.09); }
.orb-center {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #0d495f, #0a3449);
  color: #eaffe9;
  box-shadow: 0 0 0 5px rgba(27, 161, 145, 0.1), 0 12px 20px rgba(12, 66, 80, 0.2);
}
.orb-center small { font-family: "Cascadia Code", Consolas, monospace; font-size: 6px; letter-spacing: 1px; color: #80ddd2; }
.orb-center b { margin-top: 2px; font-size: 14px; letter-spacing: 1px; }
.orb-center em { margin-top: 3px; font-style: normal; color: #a4ded9; font-size: 7px; }
.hub-orb.busy .ring-one { animation-duration: 3s; }
.flow-targets { display: grid; gap: 5px; max-height: 170px; overflow: auto; padding-right: 2px; }
.flow-targets::-webkit-scrollbar { width: 3px; }
.flow-targets::-webkit-scrollbar-thumb { background: #d4e4e8; border-radius: 2px; }
.flow-target {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  border: 1px solid #e2edf1;
  border-radius: 7px;
  background: #fff;
  padding: 6px 7px;
  text-align: left;
  cursor: pointer;
  color: inherit;
  transition: all 0.2s ease;
}
.flow-target:hover { border-color: #a8dcd4; background: #f5fcfa; }
.flow-target.subscribing { border-color: #b9e5df; background: #f6fcfa; }
.target-status { width: 6px; height: 6px; border-radius: 50%; background: #2bb89e; box-shadow: 0 0 0 3px rgba(43, 184, 158, 0.12); flex-shrink: 0; }
.flow-target div { flex: 1; min-width: 0; }
.flow-target b { display: block; color: #355267; font-size: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.flow-target small { display: block; margin-top: 2px; color: #99acb7; font-size: 7px; }
.flow-target .el-icon { color: #a6bdc8; font-size: 11px; }
.flow-empty { padding: 12px; color: #9bafb9; font-size: 9px; text-align: center; }
.flow-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 16px;
  padding: 9px 0 11px;
  color: #93a7b2;
  font-size: 8px;
  border-top: 1px dashed #edf3f6;
}
.flow-foot span { display: inline-flex; align-items: center; gap: 4px; }
.legend-dot { width: 5px; height: 5px; border-radius: 50%; }
.legend-dot.push { background: #168f7d; }
.legend-dot.pull { background: #d5ac52; }
.flow-foot-right { margin-left: auto; font-family: "Cascadia Code", Consolas, monospace; color: #a2b4bd; font-size: 7px; }

.package-panel :deep(.el-table) { --el-table-border-color: #edf3f6; --el-table-header-bg-color: #f8fbfc; font-size: 10px; }
.package-panel :deep(.el-table th.el-table__cell) { height: 34px; padding: 0; }
.package-panel :deep(.el-table td.el-table__cell) { padding: 8px 0; }
.package-panel :deep(.el-table__row) { cursor: pointer; }
.package-panel :deep(.el-table__row:hover > td.el-table__cell) { background: #f7fcfb; }
.package-cell { display: flex; align-items: center; gap: 8px; }
.package-mark { width: 26px; height: 26px; border-radius: 7px; display: flex; align-items: center; justify-content: center; color: #168f7d; background: #eaf8f5; font-size: 13px; flex-shrink: 0; }
.package-cell b { display: block; color: #2c485c; font-size: 10px; font-weight: 600; }
.package-cell small { display: block; margin-top: 2px; color: #9aaeba; font-size: 7px; }
.record-count { color: #24445a; font-family: "Cascadia Code", Consolas, monospace; font-size: 10px; }
.dispatch-summary { font-size: 9px; color: #5a7384; }
.dispatch-summary em { display: block; margin-top: 2px; color: #d07b54; font-style: normal; font-size: 7px; }
.dispatch-summary em.ok { color: #41a27e; }
.status-pill { display: inline-flex; align-items: center; justify-content: center; padding: 3px 7px; border-radius: 5px; font-size: 8px; white-space: nowrap; }
.status-pill.success { color: #218966; background: #e9f8f1; }
.status-pill.warning { color: #a5691e; background: #fff5e4; }
.status-pill.danger { color: #bf5c56; background: #fff0ef; }
.status-pill.muted { color: #7e929e; background: #f1f5f7; }
.table-time { color: #8fa2ae; font-size: 8px; }
.table-link { border: 0; background: transparent; color: #168f7d; font-size: 8px; cursor: pointer; padding: 2px 4px; }
.table-link + .table-link { margin-left: 2px; color: #839caa; }
.empty-state { padding: 20px 14px; text-align: center; color: #9bafba; font-size: 9px; }
.head-actions .link-action { font-size: 8px; }

.hub-duo { display: grid; grid-template-columns: 1.3fr 1fr; gap: 14px; }
.trend-panel :deep(.echart) { min-height: 0 !important; }
.quality-body { display: flex; align-items: center; gap: 18px; padding: 5px 16px 0; }
.quality-ring {
  width: 86px;
  height: 86px;
  border-radius: 50%;
  padding: 7px;
  flex-shrink: 0;
}
.quality-ring > div {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.quality-ring strong { color: #173f54; font-size: 19px; line-height: 1; }
.quality-ring strong small { color: #8198a6; font-size: 8px; }
.quality-ring span { margin-top: 3px; color: #99abb6; font-size: 7px; }
.quality-list { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.quality-list div { display: flex; align-items: baseline; justify-content: space-between; font-size: 8px; color: #849ba8; }
.quality-list b { color: #21465a; font-size: 13px; }
.quality-list b.danger { color: #ca625b; }
.quality-list b small { font-size: 7px; font-weight: 400; color: #98acb7; }
.quality-foot { margin: 10px 16px 13px; padding-top: 8px; border-top: 1px dashed #eef4f6; color: #9cafb9; font-size: 8px; line-height: 1.55; }
.danger { color: #c7655f !important; }
.ok { color: #2f9b76 !important; }
.muted { color: #9aacb7; }

.subscriber-list { padding: 0 9px 9px; display: flex; flex-direction: column; gap: 7px; max-height: 438px; overflow: auto; }
.subscriber-list::-webkit-scrollbar { width: 3px; }
.subscriber-list::-webkit-scrollbar-thumb { background: #d7e6ea; border-radius: 3px; }
.subscriber-item { padding: 9px; border: 1px solid #e6eef2; border-radius: 9px; background: #fbfdfe; }
.subscriber-item.offline { opacity: 0.6; background: #fafbfc; }
.subscriber-top { display: flex; align-items: flex-start; gap: 6px; }
.subscriber-dot { width: 6px; height: 6px; margin-top: 3px; border-radius: 50%; background: #2db295; box-shadow: 0 0 0 3px rgba(45, 178, 149, 0.1); flex-shrink: 0; }
.subscriber-item.offline .subscriber-dot { background: #a6b4bc; box-shadow: none; }
.subscriber-name { flex: 1; min-width: 0; }
.subscriber-name b { display: block; color: #2a4659; font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.subscriber-name span { display: block; margin-top: 2px; color: #9aadb8; font-family: "Cascadia Code", Consolas, monospace; font-size: 7px; }
.online-label { color: #3a9b7d; font-size: 7px; }
.subscriber-item.offline .online-label { color: #9aacb6; }
.subscriber-datasets { display: flex; flex-wrap: wrap; gap: 3px; margin-top: 7px; }
.subscriber-datasets span { padding: 2px 4px; border-radius: 3px; background: #eef7f5; color: #518278; font-size: 7px; }
.subscriber-endpoint { margin-top: 6px; color: #9aacb7; font-family: "Cascadia Code", Consolas, monospace; font-size: 6.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.subscriber-foot { display: flex; align-items: center; gap: 7px; margin-top: 7px; padding-top: 6px; border-top: 1px dashed #edf3f6; color: #93a7b2; font-size: 7px; }
.subscriber-foot .mini-action { margin-left: auto; }

.receipt-list { padding: 0 9px 9px; max-height: 390px; overflow: auto; }
.receipt-list::-webkit-scrollbar { width: 3px; }
.receipt-list::-webkit-scrollbar-thumb { background: #d7e6ea; border-radius: 3px; }
.receipt-item { display: flex; align-items: flex-start; gap: 7px; padding: 8px 5px; border-bottom: 1px solid #f0f5f7; }
.receipt-dot { width: 6px; height: 6px; margin-top: 3px; border-radius: 50%; flex-shrink: 0; }
.receipt-dot.sent { background: #2da38a; box-shadow: 0 0 0 3px rgba(45, 163, 138, 0.1); }
.receipt-dot.failed { background: #d26d67; box-shadow: 0 0 0 3px rgba(210, 109, 103, 0.1); }
.receipt-main { flex: 1; min-width: 0; }
.receipt-main b { display: block; color: #3d5869; font-size: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.receipt-main span { display: block; margin-top: 2px; color: #a2b3bd; font-size: 6.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.receipt-meta { text-align: right; flex-shrink: 0; }
.receipt-meta span { display: block; color: #8da4af; font-size: 7px; }
.receipt-meta small { display: block; margin-top: 2px; color: #aab9c1; font-size: 6px; }
.retry-link { border: 0; background: transparent; color: #c86d65; font-size: 7px; cursor: pointer; padding: 0; margin-top: 2px; }

.hub-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 12px 2px 3px;
  border-top: 1px solid #dee9ef;
  color: #92a6b2;
  font-size: 8px;
}
.hub-footer span { display: inline-flex; align-items: center; gap: 6px; }

.publish-preview {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin: 2px 0 10px;
  padding: 10px;
  background: #f7fbfc;
  border: 1px solid #e7f0f3;
  border-radius: 8px;
}
.publish-preview div { min-width: 0; }
.publish-preview span { display: block; color: #9aacb6; font-size: 8px; }
.publish-preview b { display: block; margin-top: 3px; color: #33576b; font-size: 9px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.publish-targets { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 12px; }
.publish-targets span { padding: 3px 7px; border-radius: 4px; background: #edf9f6; color: #327f70; font-size: 8px; }
.publish-note { padding: 9px 11px; border-radius: 7px; background: #f6fafb; color: #7f98a5; font-size: 9px; line-height: 1.65; }
.sub-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.sub-form-grid .span-2 { grid-column: span 2; }
.sub-form-grid :deep(.el-form-item) { margin-bottom: 14px; }
.sub-form-grid :deep(.el-form-item__label) { padding-bottom: 4px; font-size: 11px; color: #476174; }

.package-drawer { padding: 4px 10px 30px; }
.drawer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 15px; border-bottom: 1px solid #e7eff3; }
.drawer-head h2 { margin: 4px 0 4px; color: #17384e; font-size: 23px; }
.drawer-head .mono { color: #8da3af; font-size: 10px; }
.drawer-head-actions { display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.drawer-meta { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin: 16px 0; }
.drawer-meta div { padding: 9px 10px; border-radius: 7px; background: #f7fbfc; border: 1px solid #e9f1f4; }
.drawer-meta span { display: block; color: #96aab5; font-size: 8px; }
.drawer-meta b { display: block; margin-top: 3px; color: #315167; font-size: 10px; }
.drawer-hash { padding: 9px 11px; border: 1px dashed #d8e8e9; border-radius: 7px; background: #f8fdfb; }
.drawer-hash span { display: block; color: #8fa6ae; font-size: 8px; }
.drawer-hash code { display: block; margin-top: 4px; color: #168f7d; font-family: "Cascadia Code", Consolas, monospace; font-size: 9px; word-break: break-all; }
.drawer-section-title { display: flex; justify-content: space-between; align-items: baseline; margin: 20px 0 8px; }
.drawer-section-title span { color: #26475b; font-size: 13px; font-weight: 700; }
.drawer-section-title small { color: #98abb6; font-size: 8px; }
.drawer-dispatch-list { display: flex; flex-direction: column; border: 1px solid #e8f0f3; border-radius: 8px; overflow: hidden; }
.drawer-dispatch { display: grid; grid-template-columns: 10px 1.3fr .7fr .9fr 1fr auto; align-items: center; gap: 8px; min-height: 39px; padding: 6px 10px; border-bottom: 1px solid #f0f5f7; font-size: 8px; }
.drawer-dispatch:last-child { border-bottom: 0; }
.drawer-dispatch b { display: block; color: #3d5a6c; font-size: 9px; }
.drawer-dispatch span { color: #8fa4af; }
.drawer-dispatch .mono { font-size: 7px; }
.receipt-no { color: #2b9678 !important; }
.payload-preview { max-height: 300px; overflow: auto; padding: 13px; border-radius: 8px; background: #102c3e; color: #c7e7e2; font-family: "Cascadia Code", Consolas, monospace; font-size: 9px; line-height: 1.65; white-space: pre-wrap; word-break: break-all; }

@keyframes hub-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }
@keyframes flow-move { from { transform: translateX(-42px); opacity: 0; } 20% { opacity: 1; } 80% { opacity: 1; } to { transform: translateX(42px); opacity: 0; } }
@keyframes orb-spin { to { transform: rotate(360deg); } }

@media (max-width: 1560px) {
  .hub-layout { grid-template-columns: 264px minmax(430px, 1fr) 300px; }
  .hub-kpis { gap: 8px; }
  .hub-kpi { gap: 8px; padding: 10px; }
  .hub-kpi-copy strong { font-size: 21px; }
  .hero-copy h1 { font-size: 23px; }
  .flow-board { grid-template-columns: minmax(92px, 0.8fr) 38px 118px 38px minmax(150px, 1.15fr); }
}
@media (max-width: 1320px) {
  .hub-layout { grid-template-columns: 245px minmax(390px, 1fr) 280px; }
  .hero-side { min-width: 300px; }
  .hero-copy h1 { font-size: 21px; }
  .hub-kpi-copy em { display: none; }
  .hub-kpi { min-height: 70px; }
  .route-note { display: none; }
  .flow-board { grid-template-columns: 1fr; gap: 8px; }
  .flow-line { display: none; }
  .hub-orb { margin: 3px auto; }
  .flow-targets { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 1050px) {
  .hub-layout { grid-template-columns: 1fr; }
  .hub-col.left-col { display: grid; grid-template-columns: 1fr 1fr; }
  .hub-col.right-col { display: grid; grid-template-columns: 1fr 1fr; }
  .hub-kpis { grid-template-columns: repeat(3, 1fr); }
  .hub-hero { flex-direction: column; align-items: flex-start; }
  .hero-side { min-width: 0; text-align: left; }
  .hero-actions { justify-content: flex-start; }
  .route-bar { flex-wrap: wrap; }
  .hub-footer { flex-direction: column; }
}
</style>
