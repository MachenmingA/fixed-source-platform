# 固定污染源企业画像与 AI 辅助研判原型 · 测试报告

测试日期：2026-10-22  
测试环境：Docker Desktop，后端 JDK 21 + Spring Boot 3.2，H2 文件库，前端 Nginx + Vue 3；Playwright 驱动本机 Chrome（headless）；Android JVM 单元测试使用 JDK 17 + Gradle 8.7。

## 一、测试环境与入口

| 项目 | 值 |
|---|---|
| PC 端 / 首页大屏 | http://localhost:8088 |
| 国家数据共享平台 | http://localhost:8088/data-hub |
| 开放数据目录 | http://localhost:8088/open-data/v1/catalog，Key `national-open-demo-key` |
| 模拟接收端 | `/mock-consumer/v1/{system}/ingest`，HMAC-SHA256 |
| 后端健康检查 | http://localhost:8080/api/health |
| 移动 H5 | http://localhost:8088/m/tasks |
| Android APK | http://localhost:8088/downloads/fixed-source-mobile-debug.apk |
| 后端容器 | `fixed-source-backend`，健康状态 |
| 前端容器 | `fixed-source-frontend` |
| 数据卷 | `fixed-source-platform_fixed-source-data` / `..._uploads` |
| 演示账号 | `analyst` / `reviewer` / `inspector` / `admin`，密码 `123456` |
| 企业台账维护权限 | `reviewer` / `admin`；`analyst` 只读并有提示 |

## 二、测试数据规模

| 数据 | 数量 |
|---|---:|
| 企业 | 11 家（10 家种子 + 1 家台账维护新增，覆盖 10 个区域、9 个行业） |
| 排口 / 监测点 | 22 个 |
| 污染物 | 6 类：SO₂、NOx、颗粒物、COD、氨氮、VOCs |
| 小时监测数据 | 35,685 条（30 天历史 + 当日数据） |
| 真值标签 | 28 条（R001—R005 五类异常剧本） |
| 自动/人工线索 | 34 条 |
| 聚合异常事件 | 30 件 |
| 核查任务 | 6 件（1 件已下发待接收、5 件已关闭，闭环率 83.3%） |
| 核查报告 | 5 份 |
| 数据质量问题 | 5 条开放问题（均为缺失类） |
| AI 研判 | 34 条，其中无依据拒答 9 条 |
| 任务下发消息 | 6 条 |
| 共享数据目录 | 7 类（含 2 类公开参考数据） |
| 数据订阅系统 | 5 个（4 个启用，1 个停用边缘节点） |
| 已发布数据包 | 10 个 |
| 数据投递 / 回执 | 30 次，成功率 100% |
| 数据仓累计记录 | 1,282 条 |

公开参考数据：20 条重点排污单位公开名录（来自北京市生态环境局、朝阳区生态环境局历年公开名单），5 条标准/数据平台元数据（GB 16297-1996、GB 8978-1996、GB 37822-2019、HJ 819-2017、全国排污许可证管理信息平台公开端）。每条记录保存来源名称与来源链接；名录仅保留企业名称、区域、信用代码和名录类别，不伪造联系方式、坐标、生产或排放数据。

## 三、规则回测结果

最新回测批次：`BT20261022014715444`，窗口 2026-10-15 至 2026-10-22，规则版本 `R001-R005/v1.0`。

| 指标 | 数值 |
|---|---:|
| 真值标签 | 28 |
| 规则命中 | 28 |
| TP | 28 |
| FP | 0 |
| FN | 0 |
| Precision | 100.00% |
| Recall | 100.00% |
| F1 | 100.00% |

分规则结果：

| 规则 | 标签 | 命中 | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|
| R001 浓度超限 | 10 | 10 | 100% | 100% | 100% |
| R002 数据缺失 | 5 | 5 | 100% | 100% | 100% |
| R003 连续恒值 | 7 | 7 | 100% | 100% | 100% |
| R004 异常突变 | 4 | 4 | 100% | 100% | 100% |
| R005 状态不一致 | 2 | 2 | 100% | 100% | 100% |

说明：异常剧本按业务日期固定槽位生成，标签同步对同一业务日期幂等替换，避免服务跨天重启或重复采集造成“标签漂移”。命中匹配仍为“规则 + 点位 + 污染物 + 日期”的分组计数口径，小时级时间重叠和规则阈值版本对比属于后续增强项。

## 四、页面与功能测试矩阵

| 页面 / 功能 | 测试方式 | 结果 | 证据 |
|---|---|---|---|
| 登录、四类角色快捷登录、Android APK 下载入口 | Playwright | 通过 | `01-login.png`、E2E |
| 首页监管大屏、KPI、实时事件、AI/任务面板 | Playwright | 通过 | `02-dashboard.png`、`20-e2e-bigscreen.png` |
| 北京风险地图：企业撒点、图层切换、点击企业联动态势卡 | Playwright | 通过 | E2E，11 个企业点位 |
| 地图区域下钻、空白区域 0 数据兜底 | Playwright | 通过 | `02b-dashboard-region.png` |
| 地图缩放、平移、复位、全屏 | Playwright | 通过 | `38-bigscreen-fullscreen.png` |
| 运行今日采集 | Playwright + API | 通过 | E2E |
| 企业列表、关键词/区域/风险/状态筛选 | Playwright | 通过 | E2E |
| 企业台账新增、编辑、坐标地图点选、审计 | Playwright + API | 通过 | `03-enterprises.png`、`39-enterprise-edit.png` |
| 企业台账角色保护（analyst 只读） | Playwright + API | 通过 | 前端提示 + 后端 403 |
| 企业档案主页（风险画像、趋势、事件、点位、位置地图、空状态引导） | Playwright | 通过 | `04-enterprise-profile.png`、`04b-enterprise-profile-empty.png` |
| 全站视觉基线、页面头卡/KPI/流程条/表格/弹窗/空状态 | Playwright 截图走查 | 通过 | `screenshots/` 全量刷新 |
| 响应式：14 个路由 × 1280/1366/1600 宽度 + 390px 移动端 | Playwright 路由/溢出审计 | 通过 | 无横向溢出、无控制台错误 |
| 登录页移动端 390px：品牌头、角色卡、APK 入口 | Playwright | 通过 | `08b-mobile-login.png` 验收，无横向滚动 |
| 排口/监测点新增、编辑、停用 | Playwright + API | 通过 | 2 个点位、状态维护 |
| 企业画像手工标记线索 + 事件工作台手工新增线索入口 | Playwright + API | 通过 | `28-e2e-manual-clue.png`，来源 `MANUAL`，进入同一聚合链路 |
| 事件工作台、证据链、AI 条款引用/拒答 | Playwright + API | 通过 | `06-event-detail.png` |
| 区域态势、风险矩阵、行业统计 | Playwright | 通过 | `21-e2e-cockpit.png` |
| 监测数据页：逐时曲线、近 7/14/30 日走势、24 小时画像、热力图、日汇总 | Playwright | 通过 | `41-monitoring.png`、`41-e2e-monitoring.png` |
| 监测分析 API：执行限值/超限小时/完整率/热力数据 | Python smoke + Playwright | 通过 | 7 日 168 条样本、日聚合 7 天、热力 168 格 |
| 审核确认、生成任务、下发 | Playwright | 通过 | `34-e2e-reviewer-dispatch.png` |
| 事件拆分 / 合并 | 专项脚本 + API | 通过（历史验收） | `35-e2e-split.png`、`36-e2e-merge.png` |
| 任务列表、督办、反馈、关闭 | Playwright + API | 通过 | `07-tasks.png`、`33-e2e-mobile-feedback.png` |
| 采集批次、数据源配置 | Playwright + API | 通过 | E2E / API |
| 数据质量汇总与列表 | Playwright | 通过 | `25-e2e-data-quality.png` |
| 规则配置、编辑与启停 | Playwright + API | 通过 | E2E / API |
| 规则回测运行与历史载入 | Playwright | 通过 | `22-e2e-backtest.png`、`23-e2e-backtest-run.png` |
| 测试数据标签、命中状态、CSV 导出 | Playwright | 通过 | `24-e2e-test-data.png`，28 行 |
| 集成对账、outbox 重试 | Playwright + API | 通过 | `26-e2e-integration.png`、`27-e2e-reconcile.png` |
| 核查报告列表、生成、打印视图 | Playwright | 通过 | `29-e2e-report.png` |
| 移动 H5 任务列表 / 详情 / 接收 | Playwright | 通过 | `30-e2e-mobile-tasks.png`、`31-e2e-mobile-detail.png` |
| 移动 H5 拍照水印、附件、反馈 | Playwright | 通过 | `32-e2e-mobile-form.png`、`33-e2e-mobile-feedback.png` |
| 健康检查、登录、Dashboard、企业、事件、任务、移动任务、监测分析、数据仓、开放目录 API | Python smoke test | 12/12 通过 | `scripts/smoke_test.py` |
| 主链路后端集成测试（登录→确认→任务→下发→接收→反馈→关闭） | JUnit + MockMvc | 1/1 通过 | `MainFlowTest.java` |
| Android 登录请求、Token、任务详情、附件上传、URL、断连 | JUnit + MockWebServer | 6/6 通过 | `android/.../ApiClientTest.kt` |
| Android Debug APK 构建与清单检查 | Gradle + aapt | 通过 | 6.5 MB，minSdk 26 / targetSdk 34 |
| 国家数据仓总览、目录、订阅方、数据包和回执面板 | Playwright | 通过 | `70-data-hub.png` |
| 数据包发布、自动匹配订阅方、数据包详情 | Playwright + API | 通过 | `72-data-hub-publish.png`、`74-data-hub-package-created.png` |
| 数据包 JSON 下载与 SHA-256 指纹查看 | Playwright | 通过 | 下载文件 `DP*.json` |
| 订阅系统维护界面与启停配置 | Playwright | 通过 | `75-data-hub-subscriber-edit.png` |
| HMAC-SHA256 推送、时间戳校验、幂等回执 | JUnit + MockMvc | 通过 | 正常签名 200，错误签名 401，重复包幂等 |
| 失败投递与人工重试 | API | 通过 | 断开端点返回 FAILED，恢复后重试 200 并变为 SENT |
| 开放数据目录与最新包拉取、Key 校验 | Playwright API + curl | 通过 | 正确 Key 200（7 类目录），错误 Key 401 |
| 基础数据与公开参考出口（企业、排口、标准、小时、日指标、公开名录、标准库） | JUnit + API | 通过 | 企业 11、排口 22、标准 6、小时 1,152、日指标 48、公开名录 20、标准库 5；加工类目录返回 400 |
| AI 有依据引用 / 无依据拒答 | API | 通过 | 34 条研判，9 条拒答；拒答时 citations=0 |

## 五、自动化命令

```bash
# 只读冒烟
python scripts/smoke_test.py

# 后端集成测试（H2 内存库）
cd backend && mvn test

# 浏览器逐页 E2E（需 Docker 已启动）
cd frontend && node e2e-tests.mjs

# 事件拆分/合并专项测试（依赖当前演示数据）
cd frontend && node split-merge-test.mjs

# 国家数据共享平台专项测试
cd frontend && node data-hub-test.mjs

# 全站视觉与响应式审计（15 路由 × 3 宽度 + 移动端）
cd frontend && npm run test:visual

# Android 网络层单元测试
cd android && ./gradlew testDebugUnitTest

# Android Debug APK
cd android && ./gradlew assembleDebug
```

最近一次执行结果：

- 冒烟测试：**12/12**。
- 后端集成测试：**2/2**（主链路 + 数据仓签名回执）。
- 逐页 E2E：**22/22**，无控制台错误。
- 国家数据共享平台专项 E2E：**5/5**，无控制台错误。
- 视觉与响应式审计：15 个路由 × 1280/1366/1600 + 390px 移动端登录/任务列表/任务详情，**无横向溢出、无控制台错误**。
- Android JVM 单元测试：**6/6**，耗时约 0.6s。
- Android APK 构建：**BUILD SUCCESSFUL**。
- 失败投递重试专项：通过（断开端点 → FAILED → 恢复端点 → SENT，重试次数 +1）。
- 事件拆分/合并专项：历史验收通过；本轮改动未涉及拆分/合并逻辑。

## 六、本轮发现并修复的问题

1. **H2 大写列名与新增字段映射**：企业台账新增地址、坐标、生产状态、许可证、联系人字段，补充响应键归一化；移动任务详情补齐 `slaHours`、点位名和污染物名。
2. **首页地图只显示撒点、不显示区划**：Geo 坐标系与 Map series 使用了两套投影，导致企业点位与区域错位、点击命中错误；改为单地图投影 + DOM 点位层，点位通过 `convertToPixel` 定位，支持缩放/平移后重算。
3. **城市中心企业点位重叠**：11 家企业集中撒点，部分点位无法点击；增加碰撞检测和环形错位，保证每个企业可点选。
4. **大屏图表底部被裁切**：EChart 组件默认 `min-height: 160px` 与紧凑布局冲突；对趋势/规则图表单独覆盖，并调整 1920×1080 大屏高度。
5. **企业编辑弹窗超出视口**：表单内容长，地图选点和底部保存按钮不可见；为对话框 body 增加最大高度和滚动，保存按钮始终可达。
6. **企业地图选点组件宽度塌陷**：在弹性表单中宽度变为内容宽度；修复为 100% 宽度，并将坐标输入放到地图下方。
7. **回测标签跨天漂移**：异常剧本原先按“距今天数”取槽位，服务跨天重启或重跑采集会把旧数据对到新标签，导致 Recall 降到 51.9%；改为按业务日期取稳定槽位，并按日期幂等替换标签，恢复 28/28。
8. **地图点位与区域点击冲突**：重写为 DOM 点位后，企业点击阻止冒泡；区域点击走 Map series，空白区域显示 0 数据态势卡。
9. **闭环率、R004 误报、事件拆分重聚合**：沿用前一轮修复并回归通过；闭环率按已关闭/全部任务计算，R004 增加相邻小时和关联规则抑制，事件重建优先尊重已有 event_id。
10. **移动端取证**：验证 Canvas 时间/地点/企业/点位水印、附件上传和反馈回写。
11. **公开数据接入**：新增重点排污单位公开名录和标准/指南元数据两个基础数据目录；公开名录只提取政府公开字段并记录来源链接，不补造企业隐私数据。
12. **数据包预览为空**：H2 CLOB 在部分查询里返回的列键大小写不一致，直接读取 `payload_json` 得到空值；增加多键兼容和 Clob 读取后，详情预览、完整 JSON 下载一致。
13. **开放数据接口未经网关**：Nginx 只代理 `/api`、`/mock-source`、`/uploads`，浏览器访问 `/open-data` 落入 SPA；补充 `/open-data/` 和 `/mock-consumer/` 代理。
14. **数据推送找不到接收方密钥**：发布时使用的订阅方列表是脱敏视图，不包含 `app_secret`，导致签名密钥为空；改为分发时使用内部订阅方记录，并对 UI 隐藏密钥。
15. **企业画像页面重构**：将原来的平铺卡片改为企业档案驾驶舱，增加风险因子条、事件流、点位状态、企业位置小地图和空数据引导；保留台账编辑、点位维护、手工线索和事件下钻能力。
16. **失败重试验证**：临时把国家排污许可平台接收地址改为不可达端口，发布基础数据包得到 PARTIAL/FAILED，恢复地址后重试成功，数据包回写 DISTRIBUTED，重试次数递增。
17. **全站视觉基线**：页面头卡、KPI、流程条、表格、输入、弹窗、空状态统一；事件、任务、报告、采集、质量、规则、回测、对账均补充业务摘要或口径说明。
18. **回测指标卡与表格挤压**：8 列指标卡在 1366 宽度下单位换行，右侧指标表把页面撑出 73px；统一指标卡样式，回测双栏在 1500px 以下改为单列，并为网格子项补充 `min-width: 0`。
19. **报告页高度失衡**：报告生成流程与边界卡高度不一致，左侧出现大块留白；补充“报告固定章节”和自动拼装说明，使页面结构完整。
20. **移动端登录横向溢出**：390px 下登录面板被角色卡最小内容宽度撑到 419px；修复 grid 列宽、增加移动品牌头和 DEMO 标识，并精简角色描述避免省略号。
21. **移动端任务与详情信息层级**：任务卡增加风险色条、列表增加现场核查指引；详情页增加“下发→接收→取证→回写”四步进度，便于演示闭环位置。
22. **移动 E2E 截图伪影**：全页截图滚动后会把 sticky 头部重复渲染到页面中部；移动端证据改用视口截图，避免验收材料误读，功能测试结果不变。

## 七、局限

- 数据为合成数据，回测 100% 不代表真实业务效果；真实数据会有口径、设备和人为因素。
- 回测仍是分组计数匹配，未做小时级时间重叠、状态排除和规则版本阈值对比。
- AI 为确定性 mock RAG，用于离线演示引用、拒答和人工复核边界，不是真实大模型。
- 移动 H5 是模拟执行端，但已与 Android 原生端共用 `/api/mobile/*`。
- Android APK 已构建，网络层与接口契约有单元测试；当前机器无 Android 模拟器（虚拟化未开启）或真机，相机、定位和页面级运行尚未在本机验收，需接真机补测。
- 原型使用 HTTP 明文局域网访问，生产环境需 HTTPS、Token 刷新、设备绑定和更细权限控制。
- 国家数据仓与业务平台部署在同一个 Spring Boot 应用中，接收端为同应用模拟节点；真实生产应拆分独立共享服务，接入 API 网关、mTLS、密钥托管、限流、异步重投和跨组织审计。
- HMAC 已验证报文完整性、时间戳和 AppKey 身份，但原型 AppSecret 仅用于演示，订阅方界面只显示脱敏值。
- 数据开放接口使用静态 Demo Key，尚未实现 OAuth2 客户端凭证、IP 白名单和调用配额。
- 数据共享目录只包含基础数据；规则、事件、风险、AI 和任务闭环属于另一个数据处理项目，未通过本平台共享。
- P2 的用电/工况联动、离线队列和重污染天气仍只有设计。
