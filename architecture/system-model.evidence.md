# 证据索引 · 当前状态架构

- 系统：净源智研 · 固定污染源企业画像与 AI 辅助研判平台
- 仓库：`fixed-source-platform`
- 范围：当前状态（current state），L1 系统上下文 + L2 容器与模块 + 关键闭环 + 数据资产
- 生成日期：2026-10-22
- 模型源：`architecture/system-model.json`
- 交互图：`architecture/system-architecture.html`
- 说明：`architecture/system-model.summary.md`

> 图中每个节点与关系都带有 `sourceRefs` 与置信度。源码路径均相对仓库根目录 `fixed-source-platform/`。

## 1. 取景与证据强度

| 项目 | 结论 | 证据 | 置信度 |
|---|---|---|---|
| 系统边界 | Vue 3 SPA（PC + H5）+ Spring Boot 后端 + H2 文件库 + 本地上传目录 | `README.md:9-14`、`docker-compose.yml:1-38` | 高 |
| 部署形态 | Docker Compose：frontend 8088、backend 8080、两个 named volume | `docker-compose.yml:1-38` | 高 |
| 技术栈 | Java 17 源码、Spring Boot 3.2.0、Spring JDBC、H2 文件模式、JDK 21 运行镜像 | `backend/pom.xml:10-37`、`backend/Dockerfile:6-8`、`README.md:212-223` | 高 |
| 代码规模 | 44 个 Java 文件、22 个 Controller、13 个 Service | 目录与文件计数 | 高 |
| 数据规模 | 25 张 H2 表 | `backend/src/main/java/com/fixedsource/db/SchemaService.java` | 高 |
| 演示数据 | 11 家企业、22 个点位、6 类污染物、35,685 条逐小时记录 | `README.md:79-86` | 中（README 口径，随采集/回测变化） |
| 地图 | 内置北京 16 区 GeoJSON，离线 ECharts 渲染，无在线瓦片 | `frontend/src/components/BeijingRiskMap.vue:16-20`、`frontend/src/utils/beijingMap.js` | 高 |
| AI | 本地 `mock-rag-v1`，10 条法规片段，无外部模型/向量库 | `backend/src/main/java/com/fixedsource/service/AiService.java:43-58`、`backend/src/main/resources/knowledge/regulations.json` | 高 |

## 2. L1 系统上下文节点

| 节点 | 关键事实 | 证据 | 置信度 |
|---|---|---|---|
| 监测分析人员 `analyst` | 看画像/证据/趋势，发起 AI 研判，手工标记线索；台账只读 | `backend/src/main/java/com/fixedsource/service/AuthService.java:19`、`README.md:48` | 高 |
| 审核人员 `reviewer` | 确认/撤销线索，拆分合并事件，创建下发督办关闭任务，维护台账 | `AuthService.java:20`、`ClueController.java:81-104`、`TaskService.java:31-175` | 高 |
| 现场核查人员 `inspector` | H5/Android 接单、拍照水印、附件上传、反馈 | `AuthService.java:21`、`MobileTaskDetail.vue:153-240`、`WatermarkRenderer.kt` | 高 |
| 系统管理员 `admin` | 规则、数据源、台账维护；拥有全部角色权限 | `AuthService.java:22`、`AuthService.java:44-56` | 高 |
| 净源智研平台 | 系统边界：15 个 PC 业务路由 + 2 个移动路由 + 后端 + H2 + 上传目录 | `frontend/src/router.js:6-38`、`docker-compose.yml` | 高 |
| 模拟监测数据源 | 10 家企业、20 个点位、6 类污染物、异常剧本；HTTP 失败回退进程内生成器 | `MockSourceController.java:13-28`、`MockSourceService.java:25-70`、`CollectService.java:112-125` | 高 |
| 国家数据共享平台（模拟） | 目录、打包、订阅、HMAC 投递、回执、开放拉取；与业务后端同 JVM、共用 H2 | `DataHubService.java:94-530`、`DataHubSourceClient.java:21-80`、`DataHubBootstrap.java:20-60` | 高 |
| 订阅系统 ×5 | npe / enf / city / open / edge，各有 app_key / app_secret 与端点 | `SeedService.java:186-200`、`MockConsumerController.java:13-30` | 高 |
| 开放数据消费者 | 独立 `X-Open-Data-Key`，catalog / latest | `OpenDataController.java:15-55`、`application.yml:27-31` | 高 |
| 公开法规知识库 | 10 条法规片段，按 ruleCode + pollutant 匹配，最多引用 3 条 | `regulations.json`、`AiService.java:43-58`、`AiService.java:137-170` | 高 |

## 3. L2 容器与模块节点

| 节点 | 关键事实 | 证据 | 置信度 |
|---|---|---|---|
| Vue 3 单页应用 | Vue 3.4 / Vite 5 / Element Plus / ECharts / Axios；PC 15 路由 + 移动 2 路由 | `frontend/package.json:10-22`、`frontend/src/router.js:6-38`、`frontend/src/api.js:5-29` | 高 |
| Android 现场端 | Kotlin / Material 3 / OkHttp / Gson；相机、定位水印、缓存、401 处理 | `android/app/build.gradle.kts:18-36`、`ApiClient.kt:21-90`、`WatermarkRenderer.kt`、`AndroidManifest.xml:10-24` | 高 |
| Nginx | 1.27-alpine；托管 dist 与 APK；代理 `/api`、`/open-data`、`/mock-*`、`/uploads` | `frontend/nginx.conf:1-42`、`frontend/Dockerfile` | 高 |
| Spring Boot 后端 | 单体、单 JVM；业务模块 + 模拟源 + 数据共享模块 + Mock 接收端 | `backend/pom.xml`、`backend/Dockerfile`、`docker-compose.yml` | 高 |
| 认证与会话 | X-Token/Bearer；UUID token 存内存 ConcurrentHashMap；重启失效 | `AuthService.java:15-56`、`AuthInterceptor.java:24-71` | 高 |
| 采集接入 | POST 触发；批次、HTTP 拉取、MERGE 幂等、失败兜底、后续质量/规则/AI/事件链路 | `CollectController.java:31-40`、`CollectService.java:73-140` | 高 |
| 数据质量 | 缺失/异常留痕；每日完整率按 24 个应到小时计算 | `DataQualityService.java:25-92` | 高 |
| 规则引擎 | R001 超限、R002 缺失、R003 恒值、R004 突变、R005 状态不一致；证据快照与 fingerprint | `RuleEngineService.java:82-215` | 高 |
| 事件聚合与风险 | 同企业/排口/污染物/相邻时段聚合；P1–P3；A/B/C/D 风险分级 | `EventRiskService.java:33-180` | 高 |
| AI 辅助研判 | mock-rag-v1；命中引用、未命中拒答；置信度公式 | `AiService.java:80-200` | 高 |
| 任务闭环 | 确认线索→任务→outbox→移动端轮询→接收→反馈→关闭；督办/重试对账 | `TaskService.java:31-175`、`TaskController.java:24-92` | 高 |
| 台账·画像·大屏 | 企业/点位维护、风险画像、监测分析、区域/行业态势、离线地图 | `EnterpriseController.java:45-287`、`DashboardController.java:28`、`MonitoringController.java:32` | 高 |
| 回测·报告·测试数据 | 规则回测、可打印报告、异常真值标签与 CSV | `BacktestService.java:31-150`、`ReportService.java:28-199`、`LabelService.java:22-50` | 高 |
| 基础数据服务出口 | 7 类基础数据集；reviewer/admin/system 角色；服务账号 token | `DataServiceController.java:18-65`、`DataHubSourceClient.java:28-80` | 高 |
| 数据目录/打包 | header + body + SHA-256 payload hash；包状态 PUBLISHED/DISTRIBUTED/PARTIAL/FAILED | `DataHubService.java:94-160` | 高 |
| HMAC 投递/回执 | HMAC-SHA256(timestamp + packageNo + sha256(body))；10 分钟时间窗；packageNo 幂等 | `DataHubService.java:395-530`、`DataHubService.java:672-680` | 高 |
| 开放拉取 / Mock 接收 | 独立 Key 的 catalog/latest；Mock 接收端验签并写回执 | `OpenDataController.java:18-55`、`MockConsumerController.java:13-30` | 高 |
| H2 文件数据库 | 25 张表；MODE=MySQL；`jdbc:h2:file:/app/data/fixedsource`；Docker volume | `application.yml:6-13`、`application-docker.yml:1-10`、`SchemaService.java:17-405`、`docker-compose.yml:13-16` | 高 |
| 上传目录 | `/app/uploads`；单文件 10MB、请求 20MB；Docker volume | `FileController.java:20-40`、`application.yml:13-17`、`docker-compose.yml:13-16` | 高 |

## 4. 关系证据

| 关系 | 协议 | 证据 | 置信度 |
|---|---|---|---|
| 角色 → 平台 | HTTP + X-Token / Bearer | `frontend/src/api.js:5-17`、`AuthInterceptor.java:44-70` | 高 |
| 浏览器 → Nginx | 静态资源与 XHR | `frontend/nginx.conf:37-39` | 高 |
| Nginx → 后端 | `proxy_pass http://backend:8080` | `frontend/nginx.conf:6-36` | 高 |
| Android → 后端 | HTTP 8080，默认 `10.0.2.2:8080`，可配置 | `android/app/build.gradle.kts:33`、`ApiClient.kt:21-60` | 高 |
| 后端 → H2 | JDBC / JdbcTemplate | `backend/pom.xml:33-37`、`application.yml:6-13` | 高 |
| 后端 → 上传目录 | 本地文件系统 / multipart | `FileController.java:20-40`、`docker-compose.yml:13-16` | 高 |
| 采集 → 模拟源 | HTTP GET `/mock-source/v1`，失败回退进程内生成器 | `CollectService.java:86-125` | 高 |
| 数据平台 → 基础数据出口 | HTTP 自调用 + datahub 服务账号 token | `DataHubSourceClient.java:28-80` | 高 |
| 数据平台 → 订阅方 | HTTP POST，HMAC-SHA256，双向请求/回执 | `DataHubService.java:458-520`、`MockConsumerController.java:21-30` | 高 |
| 开放消费者 → 数据平台 | HTTP GET，独立 `X-Open-Data-Key` | `OpenDataController.java:30-55` | 高 |

## 5. 关键闭环证据

| 步骤 | 事实 | 证据 |
|---|---|---|
| 1 模拟源出数 | 按业务日期生成逐小时数据，内置异常剧本 | `MockSourceService.java:154-235` |
| 2 采集入库 | 批次 + HTTP 拉取 + MERGE 幂等 | `CollectService.java:73-140` |
| 3 质量校验 | 缺失/异常留痕，24 小时完整率 | `DataQualityService.java:25-92` |
| 4 规则生成线索 | R001–R005；clue + 证据快照 + fingerprint | `RuleEngineService.java:82-215` |
| 5 AI 研判 | 命中引用；未命中拒答并降低可信度 | `AiService.java:80-200` |
| 6 事件与风险 | 聚合、P1–P3、A/B/C/D、risk_snapshot | `EventRiskService.java:33-180` |
| 7 审核下发 | 线索确认→任务→outbox→DISPATCHED | `ClueController.java:81-104`、`TaskService.java:31-95` |
| 8 现场闭环 | 轮询→接收→拍照水印→反馈→关闭 | `TaskService.java:98-175`、`MobileTaskDetail.vue:153-240` |
| 共享 1 数据服务出口 | 7 类基础数据集 + 服务账号 | `DataServiceController.java:26-65` |
| 共享 2 封装数据包 | header + body + SHA-256 + data_package | `DataHubService.java:94-160` |
| 共享 3 HMAC 投递 | 签名 + 10 分钟时间窗 + 重试 | `DataHubService.java:458-520` |
| 共享 4 验签回执 | AppKey/时间窗/签名/packageNo 幂等 | `DataHubService.java:395-445` |
| 共享 5 对账重试 | data_dispatch 状态/延迟/重试 | `DataHubService.java:529-535`、`IntegrationController.java:23-56` |
| 共享 6 开放拉取 | catalog / latest，独立 Key | `OpenDataController.java:30-55` |

## 6. 数据资产证据

| 数据域 | 表 | 证据 |
|---|---|---|
| 基础主数据 | enterprise、monitor_point、pollutant、public_reference_item | `SchemaService.java:17-48`、`SchemaService.java:313-337` |
| 监测与采集 | monitoring_record、collect_batch、data_quality_issue、data_source_config | `SchemaService.java:49-82`、`SchemaService.java:231-258` |
| 规则与线索 | rule_config、clue、test_anomaly_label、backtest_run | `SchemaService.java:83-143`、`SchemaService.java:260-297` |
| 事件·风险·研判 | abnormal_event、risk_snapshot、ai_assessment | `SchemaService.java:124-156`、`SchemaService.java:209-219` |
| 任务与报告 | inspection_task、task_feedback、inspection_report、dispatch_outbox、audit_log | `SchemaService.java:157-230`、`SchemaService.java:298-312` |
| 数据共享 | data_catalog、data_subscriber、data_package、data_dispatch、data_receipt | `SchemaService.java:338-420`、`SeedService.java:186-200` |

关键口径：

- R001：连续超限小时聚为一条线索，峰值 ≥1.3×限值为 HIGH（`RuleEngineService.java:82-105`）。
- R002：有效记录少于 22 小时（`RuleEngineService.java:110-128`）。
- 事件优先级：HIGH 且可信度 ≥65 → P1；HIGH 或（MEDIUM 且 ≥55）→ P2；其余 P3（`EventRiskService.java:108-115`）。
- 风险分：`high×16 + medium×8 + low×3 + (100−完整率)×0.35 + 待办任务×4`，0–100；<25 A、<50 B、<75 C、其余 D（`EventRiskService.java:136-180`）。
- AI：拒答置信度 0.18、可信度 −12；命中置信度 `min(0.88, 0.58 + 0.08×引用数)`、可信度 +8（`AiService.java:137-175`）。

## 7. 已知边界与验证项

| 项 | 说明 | 证据 | 置信度 |
|---|---|---|---|
| 采集触发方式 | README 描述“每日采集”，但代码中只发现 `@EnableScheduling`，未发现 `@Scheduled` 任务；当前采集由 `POST /api/collect-batches` 或页面按钮触发。若答辩需要“每日自动采集”，需补调度器。 | `FixedSourceApplication.java:8`、`CollectController.java:31-40` | 高 |
| 数据共享平台不是独立部署单元 | 与业务后端同 JVM、共用 H2；HTTP 自调用模拟跨平台对接；无独立服务、消息队列或对象存储。 | `application-docker.yml:11-16`、`DataHubSourceClient.java:21-27` | 高 |
| 任务下发没有推送网关 | 写 `dispatch_outbox` + 状态变更；H5/Android 轮询 `/api/mobile/tasks`。outbox 用于演示对账与重试，不是可靠消息投递。 | `TaskService.java:62-95`、`MobileTasks.vue:105` | 高 |
| 会话不持久化 | token 存内存 Map，重启失效；无刷新 token、设备绑定或集中式会话。 | `AuthService.java:15-56`、`AuthInterceptor.java:44-70` | 高 |
| AI 是确定性 mock RAG | 不调用外部大模型/向量库，法规片段仅 10 条；置信度与引用数量是确定性公式。 | `AiService.java:43-58`、`README.md:223` | 高 |
| Android 运行验收待补 | 源码与 Debug APK 完成，但当前环境无模拟器/真机，相机、定位与 UI 运行验收需真机补测。 | `README.md:389` | 待验证 |
| 单节点存储与明文链路 | H2 单文件、本地上传目录、HTTP 明文、局域网演示；生产化需要 HTTPS、备份、对象存储、Token 刷新和更细权限。 | `backend/src/main/resources/application.yml:6-17`、`README.md:390-394` | 高 |

## 8. 复核方法

1. 打开 `system-architecture.html`，点击任意节点或连线，右侧会列出该对象的证据路径。
2. 打开 `system-model.json`，按 `id` 检索节点/关系，查看 `sourceRefs`、`confidence`、`state`。
3. 用编辑器按 `path:line` 跳转源码复核；源码路径均相对 `fixed-source-platform/`。
4. 需要重新生成模型或增补证据时，以 `system-model.json` 为唯一源，更新后执行 `node architecture/build/build.mjs` 重新生成 HTML。
