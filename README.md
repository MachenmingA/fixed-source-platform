# 净源智研 · 固定污染源企业画像与 AI 辅助研判原型

这是一个可独立演示的毕业设计原型：每日从模拟数据源采集监测数据，按规则生成证据，聚合为异常事件，计算企业风险与研判可信度，结合公开法规片段生成带条款引用的 AI 辅助研判，最后由人工确认并把核查任务下发到 PC / 移动 H5 / 原生 Android 现场端执行、取证、反馈和闭环。

系统包含三个使用端和一个数据共享平台：

- **Web PC 端**：首页监管大屏、企业台账与画像、事件工作台、任务闭环、规则回测、报告。
- **移动 H5**：免安装的现场核查演示端。
- **Android 原生端**：Kotlin 实现的现场核查 App，支持相机水印、附件上传、弱网缓存。
- **国家数据共享平台**：模拟国家生态环境公共数据信息库，只负责企业基础信息、排口点位、污染物标准、监测指标等基础数据的打包、签名、发送、回执与开放拉取；规则、事件、风险、AI 和任务闭环属于另一个数据处理项目，不进入本平台。

## 快速启动（Docker）

镜像使用本地预构建产物，首次启动前先在宿主机完成构建：

```bash
# 1. 后端打 jar
cd backend
mvn -q -DskipTests package

# 2. 前端打 dist
cd ../frontend
npm install
npm run build

# 3. 启动完整栈
cd ..
docker compose up -d --build
```

访问地址：

- PC 端 / 首页大屏：http://localhost:8088
- 国家数据共享平台：http://localhost:8088/data-hub
- 开放数据目录：http://localhost:8088/open-data/v1/catalog（Header：`X-Open-Data-Key: national-open-demo-key`）
- 后端健康检查：http://localhost:8080/api/health
- 移动 H5：http://localhost:8088/m/tasks
- Android APK 下载：http://localhost:8088/downloads/fixed-source-mobile-debug.apk

演示账号（密码均为 `123456`）：

| 账号 | 角色 | 用途 |
|---|---|---|
| `analyst` | 监测分析人员 | 看画像、查证据、发起 AI 研判、手工标记线索；企业台账只读 |
| `reviewer` | 审核人员 | 确认线索、生成/拆分/合并事件、下发、督办、关闭；可维护企业台账 |
| `inspector` | 现场核查人员 | H5 / Android 接收任务、拍照取证、提交反馈 |
| `admin` | 系统管理员 | 规则启停与编辑、数据源配置、企业台账维护 |

登录页提供“移动 H5 核查端”和“Android APK”快捷入口。

## 页面清单

| 路径 | 页面 |
|---|---|
| `/dashboard` | **首页监管大屏**：北京风险地图、风险/密度/事件图层、区域与企业下钻、全屏模式 |
| `/cockpit` | 区域态势（区域/行业/风险矩阵） |
| `/enterprises` | **企业画像与台账**：新增企业、编辑主体/坐标/生产状态/许可证/联络信息、台账状态筛选 |
| `/enterprises/:id` | 企业风险画像、企业档案、监测趋势、排口与监测点新增/编辑、手工线索 |
| `/events` | 事件工作台、手工新增线索、证据链、AI 引用、拆分/合并、报告生成 |
| `/tasks` | 核查任务、督办、反馈、关闭 |
| `/collect` | 采集批次、数据源配置 |
| `/monitoring` | **监测数据分析**：逐时曲线、近 7/14/30 日走势、24 小时时段画像、日 × 小时热力图和日汇总 |
| `/data-quality` | 数据质量汇总与缺失明细 |
| `/rules` | 规则配置与编辑 |
| `/backtest` | 规则回测工作台 |
| `/test-data` | 异常真值标签与命中对照、CSV 导出 |
| `/integration` | 任务下发 outbox、重试、下发对账 |
| `/data-hub` | **国家数据共享平台**：基础数据目录、打包发布、订阅系统、HMAC 推送、回执与重试、开放拉取接口 |
| `/reports` | 核查报告库 |
| `/reports/:id` | 可打印报告（浏览器打印 / 导出 PDF） |
| `/m/tasks` | 移动 H5 任务列表 |
| `/m/tasks/:taskNo` | 移动 H5 任务详情、接收、拍照水印、反馈 |
| `android/` | 原生 Android 现场端源码与构建说明 |

## 演示数据覆盖

- 11 家监管企业、22 个排口/监测点；其中 10 家核心企业各有 2 个点位，每个点位维护 2—3 类污染物。
- 30 天逐小时监测记录（35,685 条），覆盖 SO₂、NOx、颗粒物、COD、氨氮、VOCs；另有按日汇总指标。
- 每家核心企业的两个点位均有近 24 小时数据，可切换点位和污染物查看趋势、限值和状态。
- 京西资源循环利用有限公司（E011）故意保留“无污染物、无监测数据”的空状态，用于演示数据缺失时的引导和补录入口，不是全量数据缺口。
- 监测数据页按企业/点位/污染物展示逐时曲线、日走势、时段画像和热力图；每日完整率按 24 个应到小时计算，超限按污染物执行限值判断。
- 人工线索与自动线索并存：事件工作台和企业画像都可以手工新增线索，来源标记为 `MANUAL`，进入同一事件聚合、风险计算和 AI 研判链路。

## 全站界面设计

本轮对 PC、登录页和移动 H5 做了统一视觉整理，避免“只有首页好看、内页还是默认组件”的割裂感：

- **统一工作台基线**：页面头卡、KPI 指标带、业务卡、表格、输入框、按钮、弹窗、抽屉和空状态共享同一套颜色、圆角、阴影与间距；页面头带渐变强调条与轻量环形装饰。
- **业务页信息层级**：事件、任务、报告、集成对账增加“总览 KPI + 业务流/检查项”，采集、质量、规则、回测和测试数据增加指标条与口径说明；企业画像采用深色企业头部 + 风险环 + 事件流 + 点位/地图的驾驶舱布局。
- **多端差异化**：首页监管大屏保留深色指挥中心风格；国家数据共享平台使用政务深蓝 + 金色徽标；登录页使用品牌分屏；移动 H5 使用深色行动头部、风险色条任务卡和现场核查指引。
- **响应式与细节**：覆盖 1280 / 1366 / 1440 / 1600 桌面宽度和 390px 移动端；修复移动登录横向溢出、1366 回测表格挤压、指标卡单位换行、报告页空白等问题；桌面与移动端均无横向滚动。

## 首页监管大屏

- 内置北京 16 区 GeoJSON，离线可渲染，不依赖在线地图瓦片。
- 区域面按“风险均分 / 在管企业数 / 未闭环事件数”切换热力；企业点位按 A/B/C/D 风险着色。
- 支持地图缩放、平移、复位；点击区域或企业弹出态势卡，展示高风险数、事件数、点位、生产状态，并可进入企业画像或直接维护台账。
- 顶部实时时钟、批次/事件滚动信息、6 项 KPI；左右两侧包含区域风险排行、高风险企业、采集质量、实时事件流、AI 协同、任务态势。
- 支持浏览器全屏模式，1920×1080 一屏展示。
- 有企业未维护坐标时，地图内给出补录提示，形成“台账维护 → 地图呈现”的闭环。

## 企业台账维护

`reviewer` / `admin` 可以在企业列表或企业画像中维护：

- 企业名称、信用代码、区域、行业、管理类别、生产状态、台账状态。
- 企业地址、经纬度（支持地图点选）、法定代表人、环保联系人、电话、排污许可证号、备注。
- 排口/监测点的新增与编辑，包括点位类型、运行状态、停用保留历史。
- 每次维护写入 `audit_log`；风险分仍由规则、事件和反馈自动计算，不允许直接改分。
- 新增企业会立即进入企业列表和首页风险地图。

## 已实现能力（P0 + P1）

1. **模拟数据源**：`GET /mock-source/v1/base-data`、`GET /mock-source/v1/monitoring?bizDate=yyyy-MM-dd`，内置 10 家企业、20 个排口/监测点、6 类污染物和异常剧本；演示库另维护了 1 家企业和 2 个监测点。
2. **每日采集**：批次号、业务日期、应收/实收、成功/失败、耗时和说明；唯一键幂等，支持重跑和补采；采集失败时回退到同契约内置生成器。
3. **规则引擎**：R001 浓度超限、R002 数据缺失、R003 连续恒值、R004 异常突变、R005 状态不一致；证据快照、fingerprint 去重、规则版本和可信度因子。
4. **事件聚合**：同企业/排口/污染物/相邻时段线索聚合为事件；按严重度、可信度和企业风险计算 P1/P2/P3；支持人工新增线索、事件拆分和合并，人工线索以 `MANUAL` 来源标识并与自动线索同池处理。
5. **企业风险画像**：风险分、A/B/C/D 分级、风险因子、区域/行业统计和监管建议。
6. **AI 辅助研判**：本地 `mock-rag` 检索 10 条公开法规片段，输出解释、条款级引用、核查建议、不确定性和置信度；无依据时拒答并降低线索可信度。
7. **任务闭环**：确认线索 → 生成任务 → outbox 下发 → 移动端接收 → 照片/附件 → 反馈 → 关闭；任务状态与线索状态分离，支持对账、重试和督办。
8. **移动 H5**：任务列表、证据/AI、接收任务、动态反馈表单、Canvas 时间/地点/企业/点位水印、重复提交保护。
9. **原生 Android 现场端**：Kotlin + Material 3，登录鉴权、任务缓存、相机构图取证、定位水印、附件上传、反馈草稿与 401 处理。
10. **企业台账维护**：主体信息、坐标、生产状态、许可证、联络信息与排口监测点新增/编辑，含角色校验和审计。
11. **监管大屏与地图**：离线北京区划、三种图层、企业撒点、区域/企业下钻、全屏大屏。
12. **数据质量**：缺失按点位/污染物/日期留痕，支持汇总、明细和 Top 点位。
13. **规则回测**：以业务日期稳定剧本生成真值标签，输出 Precision、Recall、F1、TP/FP/FN，保存回测批次。
14. **测试数据**：28 条异常注入标签，支持按规则/命中状态筛选和 CSV 导出。
15. **核查报告**：由事件证据、AI 引用和现场反馈生成报告草稿，支持打印视图和浏览器导出 PDF。
16. **集成对账**：任务下发 outbox 消息、下发重试、超时任务、待回执和差异统计。
17. **国家数据共享平台**：7 类基础与公开数据目录、5 个订阅系统、标准数据包、SHA-256 指纹、HMAC-SHA256 推送、幂等回执、失败重试、开放拉取接口和订阅系统维护；不承载加工类数据。
18. **监测数据分析**：按企业、点位、污染物和 7/14/30 天窗口输出四类图形——最新业务日逐时曲线、日走势、24 小时时段画像、日 × 小时热力图，并同步计算日均、峰值、超限小时、有效样本和完整率；支持从企业画像一键跳转到对应点位/污染物。

## 国家数据共享平台（模拟国家公共数据信息库）

数据流转边界：

```text
固定污染源业务平台
  /api/data-service/v1/export/{dataset}
        │  标准导出 + X-Token
        ▼
国家生态环境公共数据信息库（/data-hub）
  编目 → 数据包 → SHA-256 指纹 → 订阅匹配
        │
        ├─ Push：AppKey + 时间戳 + HMAC-SHA256 → 订阅系统接收端 → 回执
        └─ Pull：GET /open-data/v1/datasets/{code}/latest → X-Open-Data-Key
```

内置 7 类基础与公开数据目录：企业基础信息、排口与监测点、污染物与排放限值、小时监测明细、日监测指标、重点排污单位公开名录、公开标准与依据。内置 5 个订阅系统，其中 4 个在线：

- 国家排污许可信息平台：企业基础、排口点位、日指标、公开名录、标准依据
- 北京市生态环境执法平台：企业基础、排口点位、小时明细、污染物标准、公开名录、标准依据
- 城市运行管理平台：小时明细、日指标、污染物标准、标准依据
- 国家公共数据开放平台：企业基础、排口点位、污染物标准、日指标、公开名录、标准依据
- 海淀区生态环境分局边缘节点（默认停用，用于演示离线订阅方）

数据域边界：本平台只搬运和分发基础数据，不生成规则线索、异常事件、风险画像、AI 结论或核查任务；这些加工结果属于另一个数据处理项目，不进入共享目录。

平台能力：

- 发布数据包时调用业务平台标准数据服务出口，生成带记录数、业务日期、敏感级别和 SHA-256 指纹的数据包。
- 按 `data_subscriber.datasets` 自动匹配订阅系统并逐个推送；每次推送记录 AppKey、签名、HTTP 状态、延迟、请求时间和错误信息。
- 模拟接收端 `/mock-consumer/v1/{system}/ingest` 会校验时间戳与 HMAC-SHA256，重复包按“数据包号 + 订阅方”幂等处理，并写入回执。
- 失败投递可人工重试；数据包详情中可直接下载完整 JSON，查看回执和样本。
- 订阅系统可在页面维护：系统编码、接收地址、AppKey/AppSecret、订阅目录和启停状态。
- 开放数据目录与最新数据包走独立拉取接口，适合不定期主动同步的外部系统。
- Docker 首次启动会自动发布 7 个演示数据包，产生可追踪的订阅和投递回执；之后启动不会重复生成。

公开数据来源（种子同时保存来源名称与链接，随数据包一起下发）：

- [北京市 2018 年重点排污单位名录](https://sthjj.beijing.gov.cn/bjhrb/index/xxgk69/sthjlyzwg/wrygl/830495/index.html)
- [北京市 2019 年重点排污单位名录](https://sthjj.beijing.gov.cn/bjhrb/index/xxgk69/sthjlyzwg/wrygl/847451/index.html)
- [朝阳区 2020 年重点排污单位名录](http://www.bjchy.gov.cn/dynamic/notice/8a24fe837128f1500171292821e4021a.html)
- [GB 16297-1996 大气污染物综合排放标准](https://openstd.samr.gov.cn/bzgk/gb/newGbInfo?hcno=72B7BB9338A1B19773000E6E318AEB44)
- [GB 8978-1996 污水综合排放标准](https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=1BF9CDBD6FDE2AD5053C69A36318972C)
- [全国排污许可证管理信息平台（公开端）](https://permit.mee.gov.cn/permitExt/defaults/default-index!getInformation.action)

公开名录只保存企业名称、区域、统一社会信用代码/组织机构代码、名录类别和来源链接等公开字段，不伪造联系人、坐标、生产数据或排放数值。

核心接口：

| 接口 | 说明 |
|---|---|
| `GET /api/monitoring/analysis` | 企业/点位/污染物的逐时曲线、日聚合、热力图、超限小时与数据完整率 |
| `GET /api/data-service/v1/export/{datasetCode}` | 业务平台标准数据出口，供数据仓拉取 |
| `POST /api/data-hub/packages/publish` | 发布单个数据包并自动分发 |
| `POST /api/data-hub/packages/publish-all` | 一键发布全部目录 |
| `GET /api/data-hub/overview` | 数据仓总览、目录、订阅、数据包、投递和趋势 |
| `GET /api/data-hub/packages/{id}` | 数据包详情、指纹、投递回执和样本 |
| `GET /api/data-hub/packages/{id}/payload` | 下载完整数据包 JSON |
| `POST /api/data-hub/dispatches/{id}/retry` | 重试失败投递 |
| `GET /api/data-hub/subscribers` | 订阅系统列表 |
| `POST/PUT /api/data-hub/subscribers` | 新增或维护订阅系统 |
| `GET /open-data/v1/catalog` | 开放数据目录（`X-Open-Data-Key`） |
| `GET /open-data/v1/datasets/{code}/latest` | 拉取指定目录最新数据包 |

## 技术栈

- 后端：Java 17 源码 / Spring Boot 3.2 / Spring JDBC / H2（文件模式）/ Docker JDK 21 运行时。
- Web：Vue 3 / Vue Router / Element Plus / ECharts / Axios / Vite，内置北京 GeoJSON。
- Android：Kotlin 1.9.24 / AGP 8.4.2 / Material 3 / OkHttp / Gson / Coroutines；系统相机 + FileProvider + Canvas 水印。
- 数据共享：Spring RestClient / HMAC-SHA256 / SHA-256 / 标准数据服务出口 / 模拟接收端回执。
- AI：本地模拟 RAG，不依赖外部模型或网络；后续可替换为 Spring AI + 向量库。
- 数据：合成企业、排口、监测数据 + 公开重点排污单位名录与标准/指南元数据；不接入真实企业生产数据，公开数据只保留政府公开字段并附来源。

## 测试数据与指标

当前演示数据（执行采集/回测后会变化）：

| 数据 | 数量 |
|---|---:|
| 监管企业 | 11 家（10 家种子 + 1 家台账维护新增） |
| 排口 / 监测点 | 22 个 |
| 污染物 | 6 类 |
| 小时监测数据 | 35,685 条（30 天历史 + 当日） |
| 真值标签 | 28 条 |
| 自动/人工线索 | 34 条 |
| 聚合异常事件 | 30 件 |
| 核查任务 | 6 件（1 件已下发待接收、5 件已关闭，闭环率 83.3%） |
| 核查报告 | 5 份 |
| 数据质量问题 | 5 条开放问题 |
| AI 研判 | 34 条，其中无依据拒答 9 条 |
| 任务下发消息 | 6 条 |
| 共享数据目录 | 7 类（含 2 类公开参考数据） |
| 数据订阅系统 | 5 个（4 个启用，1 个停用边缘节点） |
| 已发布数据包 | 10 个 |
| 数据投递 / 回执 | 30 次，成功率 100% |
| 数据仓累计记录 | 1,282 条 |

当前留有一条已下发演示任务 `TSK-20261022-1965`，可用 `inspector` 在移动 H5 或 Android 端接收、拍照、反馈。

最新规则回测（2026-10-15 至 2026-10-22）：

| 指标 | 数值 |
|---|---:|
| 标签 / 命中 | 28 / 28 |
| TP / FP / FN | 28 / 0 / 0 |
| Precision | 100.00% |
| Recall | 100.00% |
| F1 | 100.00% |

异常剧本按业务日期固定槽位生成，服务重启或重复采集不会让标签与监测数据错位。R001—R005 分规则结果均为 100%。详细说明见 [TEST_REPORT.md](TEST_REPORT.md)。

## 测试命令

```bash
# 只读冒烟（需 Docker 已启动）
python scripts/smoke_test.py

# 后端集成测试（H2 内存库）
cd backend && mvn test

# 逐页浏览器 E2E（需 Docker 已启动）
cd frontend && node e2e-tests.mjs

# 事件拆分/合并专项 E2E
cd frontend && node split-merge-test.mjs

# 国家数据共享平台 E2E
cd frontend && node data-hub-test.mjs

# 全站视觉与响应式审计（15 路由 × 3 宽度 + 移动端）
cd frontend && npm run test:visual

# Android 网络层单元测试
cd android && ./gradlew testDebugUnitTest
```

最近一次结果：

- 冒烟测试 **12/12**。
- 后端集成测试 **2/2**：主链路 + 数据仓目录/角色/签名回执。
- 逐页 E2E **22/22**，无控制台错误。
- 数据共享平台 E2E **5/5**：总览、发布分发、JSON 下载、订阅维护、开放 Key 校验。
- 视觉与响应式审计：15 个路由 × 1280/1366/1600 + 390px 移动端登录/任务列表/任务详情，无横向溢出、无控制台错误。
- Android JVM 单元测试 **6/6**，Debug APK 构建成功。
- 企业台账编辑、地图撒点/图层/区域下钻、事件工作台手工新增线索纳入 E2E。

## Android 现场端

源码与构建说明见 [android/README.md](android/README.md)。

```bash
cd android
./gradlew assembleDebug       # Windows: gradlew.bat assembleDebug
```

- APK 已放入 Web 静态目录，可直接下载安装。
- 模拟器访问宿主机使用 `http://10.0.2.2:8080`；真机填写电脑局域网 IP。
- 演示账号 `inspector / 123456`。
- 当前开发机没有 Android 模拟器/真机，**相机与页面级运行验收未在本机执行**；网络接口层由 6 个 JVM 单元测试覆盖，同一任务链路由 H5 E2E 覆盖。接入真机后需补一轮相机水印和弱网回归。

## Docker 结构

- `fixed-source-backend`：Spring Boot 3.2 + JDK 21 运行，8080 端口。
- `fixed-source-frontend`：Nginx 托管 Vue 构建产物和 APK，8088 端口，反向代理 `/api`、`/mock-source`、`/uploads`。
- `fixed-source-data`：H2 文件库命名卷，数据持久化。
- `fixed-source-uploads`：现场取证附件命名卷。

重置演示数据：

```bash
docker compose down
docker volume rm fixed-source-platform_fixed-source-data fixed-source-platform_fixed-source-uploads
docker compose up -d --build
```

## API 示例

```bash
TOKEN=$(curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"reviewer","password":"123456"}' | jq -r .token)

curl -s http://localhost:8088/api/dashboard/overview -H "X-Token: $TOKEN"
curl -s http://localhost:8088/api/enterprises -H "X-Token: $TOKEN"
curl -s http://localhost:8088/api/enterprises/E011/profile -H "X-Token: $TOKEN"
curl -s "http://localhost:8088/api/mobile/tasks" -H "X-Token: $TOKEN"

# 国家数据仓
curl -s http://localhost:8088/api/data-hub/overview -H "X-Token: $TOKEN"
curl -s -X POST http://localhost:8088/api/data-hub/packages/publish \
  -H "X-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"datasetCode":"ENTERPRISE_MASTER"}'

# 开放拉取
curl -s http://localhost:8088/open-data/v1/catalog \
  -H 'X-Open-Data-Key: national-open-demo-key'
curl -s http://localhost:8088/open-data/v1/datasets/ENTERPRISE_MASTER/latest \
  -H 'X-Open-Data-Key: national-open-demo-key'
```

企业维护（审核/管理员）：

```bash
# 新增
curl -X POST http://localhost:8088/api/enterprises \
  -H "X-Token: $TOKEN" -H 'Content-Type: application/json' \
  --data-binary @enterprise.json

# 编辑
curl -X PUT http://localhost:8088/api/enterprises/E011 \
  -H "X-Token: $TOKEN" -H 'Content-Type: application/json' \
  --data-binary @enterprise.json

# 新增监测点
curl -X POST http://localhost:8088/api/enterprises/E011/points \
  -H "X-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"outletName":"备用排口","pointName":"备用监测点","pointType":"废气","status":"NORMAL"}'
```

完整接口见设计文档 7.1—7.3 节，或直接查看 Controller 源码。

## 目录结构

```
fixed-source-platform/
├── backend/             Spring Boot 后端、H2 schema、种子数据、模拟源、集成测试
├── frontend/            Vue 3 PC 端 + 移动 H5 + 数据共享平台 + 北京 GeoJSON + Playwright E2E
│   ├── e2e-tests.mjs     逐页 E2E 与截图
│   ├── visual-audit.mjs  全站响应式与横向溢出审计
│   ├── data-hub-test.mjs 国家数据仓专项 E2E
│   └── public/downloads/ Android Debug APK
├── android/             原生 Kotlin Android 现场核查端
├── screenshots/         Playwright 验收截图
├── scripts/             冒烟测试脚本
├── TEST_REPORT.md       测试报告与逐页测试矩阵
├── docker-compose.yml
└── README.md
```

## 当前边界与下一步

- Android 原生端源码和 APK 已完成；当前环境无模拟器/真机，相机与 UI 运行验收需在真机补测。
- H5 继续保留为免安装演示端，与 Android 共用 `/api/mobile/*`。
- AI 为确定性 mock RAG，以保证离线演示和可复现；接真实模型时保留引用、拒答和人工复核边界。
- P2 的用电/工况联动、离线反馈队列和重污染天气仍保留设计。
- 回测按“规则 + 点位 + 污染物 + 日期”分组计数匹配，已支持日期稳定剧本；小时级时间重叠和规则版本阈值对比可继续加强。
- 原型使用 HTTP 明文局域网访问，生产环境需要 HTTPS、Token 刷新、设备绑定和更细的权限控制。
- 选题中的 Kotlin 已落地到 Android 现场端；后端仍为 Java 17 源码 + JDK 21 容器运行。
- 国家数据仓目前作为同一 Spring Boot 应用中的独立模块实现，接收端是同应用内的模拟节点；生产化应拆成独立共享服务，并补充 API 网关、mTLS、密钥托管、限流、异步重投和跨组织审计。
- HMAC 推送验证了报文完整性、时间戳和订阅方身份；原型中的 AppSecret 仅用于演示，不能直接用于生产。

## 验收截图

- `screenshots/01-login.png`：品牌分屏登录、四类角色快捷入口、移动 H5 与 Android APK 下载
- `screenshots/02-dashboard.png`：首页监管大屏（风险地图、KPI、实时事件、AI 与任务）
- `screenshots/02b-dashboard-region.png`：区域下钻态势卡
- `screenshots/38-bigscreen-fullscreen.png`：全屏大屏
- `screenshots/03-enterprises.png`：企业画像与台账列表，含新增/编辑入口
- `screenshots/04-enterprise-profile.png`：企业档案驾驶舱（风险因子、监测趋势、事件、点位与企业位置）
- `screenshots/04b-enterprise-profile-empty.png`：无监测数据企业的空状态引导
- `screenshots/39-enterprise-edit.png`：企业台账编辑与地图选点
- `screenshots/05-events.png`、`06-event-detail.png`：事件工作台、证据链与条款级 AI 引用
- `screenshots/28-e2e-manual-clue.png`：事件工作台手工新增线索（企业、点位、污染物、时段与描述）
- `screenshots/07-tasks.png`：任务闭环、移动执行端入口与闭环质量
- `screenshots/08b-mobile-login.png`、`08-mobile-tasks.png`、`09-mobile-detail.png`、`10-mobile-form.png`、`11-mobile-feedback.png`：移动 H5 登录、任务、证据、取证表单与反馈
- `screenshots/12-collect.png`：采集批次、数据源配置与模拟契约
- `screenshots/41-monitoring.png`、`41-e2e-monitoring.png`：监测数据分析（逐时曲线、日走势、时段画像、热力图与日汇总）
- `screenshots/13-data-quality.png`：数据质量汇总与缺失明细
- `screenshots/14-rules.png`：规则配置与证据口径
- `screenshots/15-integration.png`：任务下发 outbox 与对账检查
- `screenshots/16-backtest.png`、`23-e2e-backtest-run.png`：规则回测与指标解释
- `screenshots/17-test-data.png`：异常真值标签与命中对照
- `screenshots/18-cockpit.png`：区域态势、风险矩阵与行业统计
- `screenshots/19-report-library.png`、`29-e2e-report.png`：报告库与可打印核查报告
- `screenshots/20-e2e-bigscreen.png`、`21-e2e-cockpit.png`、`24-e2e-test-data.png`、`25-e2e-data-quality.png`、`26-e2e-integration.png`：逐页 E2E 验收
- `screenshots/34-e2e-reviewer-dispatch.png`：审核确认、生成任务与下发
- `screenshots/35-e2e-split.png`、`36-e2e-merge.png`：事件拆分/合并历史验收
- `screenshots/70-data-hub.png`：国家数据共享平台基础数据目录、流转、订阅与回执
- `screenshots/71-data-hub-package.png`：数据包详情、SHA-256 指纹与下载
- `screenshots/72-data-hub-publish.png`：发布数据包并自动匹配订阅方
- `screenshots/74-data-hub-package-created.png`：发布完成后的数据包与投递回执
- `screenshots/75-data-hub-subscriber-edit.png`：订阅系统维护
- `screenshots/77-data-hub-public.png`：重点排污单位公开名录与公开标准目录
