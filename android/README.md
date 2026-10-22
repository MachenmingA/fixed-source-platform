# 净源智研 · Android 现场核查端

原生 Kotlin Android 客户端，面向现场核查人员，复用 Web/H5 同一套 `/api/mobile/*` 接口：

登录鉴权 → 任务列表/筛选 → 任务详情与规则证据 → AI 条款引用 → 接收任务 → 拍照并叠加时间/坐标/企业/点位水印 → 上传附件 → 提交反馈 → 回写闭环。

## 技术栈与结构

- Kotlin 1.9.24，AGP 8.4.2，Gradle 8.7，JDK 17。
- minSdk 26，targetSdk 34，compileSdk 34。
- Material 3 + ViewBinding + RecyclerView；不依赖 WebView。
- OkHttp + Gson + Kotlin Coroutines。
- 系统相机 `ActivityResultContracts.TakePicture` + FileProvider；Canvas 绘制水印；ExifInterface 纠正照片方向。
- SharedPreferences 缓存最近任务、任务详情和反馈草稿；401 自动清理登录态。

```
android/
├── app/src/main/java/com/fixedsource/mobile/
│   ├── data/        ApiClient、会话、缓存、DTO
│   ├── ui/          登录、任务列表、任务详情、列表适配器
│   ├── util/        水印渲染、定位、中文状态文案
│   └── App.kt
├── app/src/main/res/布局、主题、矢量图标、Geo/网络配置
└── app/src/test/    ApiClient JVM 单元测试
```

## 构建

前置环境：JDK 17、Android SDK Platform 34、Build-Tools 34.0.0。

```bash
cd android
# 写入 SDK 路径；Windows 示例：
# sdk.dir=D\:\\Android\\Sdk
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`。

Web 镜像中的下载地址：<http://localhost:8088/downloads/fixed-source-mobile-debug.apk>，登录页也提供下载入口。

## 连接后端

登录页可修改服务地址：

| 场景 | 地址 |
|---|---|
| Android 模拟器 | `http://10.0.2.2:8080` |
| 真机（同一局域网） | `http://<电脑局域网IP>:8080` |

演示账号：`inspector / 123456`（接收任务、提交反馈）；`reviewer / 123456` 可查看任务但后端会拒绝非现场人员接收。

原型使用明文 HTTP，仅适合本地演示；生产应改为 HTTPS 并增加证书校验、Token 刷新和细粒度权限。

## 页面能力

1. **登录页**：服务地址、连接测试、账号密码、Token 持久化；后端重启导致 Token 失效时自动回到登录页。
2. **任务列表**：待接收 / 核查中 / 待确认筛选，指标卡、下拉刷新、离线缓存、SLA 逾期和督办标识。
3. **任务详情**：规则证据、触发值/阈值/可信度、逐时证据、AI 解释、建议核查项、法规条款引用、AI 拒答提示。
4. **现场取证**：调用真实系统相机；定位权限按需申请；Canvas 叠加时间、经纬度、企业、点位、任务号水印；压缩后上传 `/api/files`。
5. **反馈闭环**：核查结果、问题描述、处置情况、附件；反馈编号固定为 `FB-AND-{taskNo}`，网络重试不会产生重复反馈；失败时保存本地草稿。
6. **弱网处理**：最近任务与详情本地只读缓存；网络恢复后可继续提交；反馈失败给出明确提示。

## 测试

```bash
cd android
./gradlew testDebugUnitTest
```

当前结果：**6/6 单元测试通过**，覆盖登录请求、401 Token 失效、任务详情/证据/引用解析、multipart 附件上传、服务地址归一化、网络断连异常。APK 构建成功（约 6.5 MB，minSdk 26 / targetSdk 34）。

**环境限制**：当前开发机没有可用的 Android 模拟器（虚拟化未开启）或已连接真机，因此未在本机执行相机与页面级运行验收。控制台网络契约由 JVM 测试覆盖；同一后端链路由 Web E2E 和移动 H5 E2E 覆盖。接入真机后建议补一轮：登录 → 收任务 → 拍照水印 → 反馈 → PC 端查看附件与关闭。

## 后续可扩展

- WorkManager 离线反馈队列与 Wi-Fi 恢复自动重试。
- CameraX 连续取证、照片压缩策略和 EXIF 风险校验。
- HTTPS 证书固定、Token 刷新、设备绑定与审计上报。
- 现场扫码核对企业/排口二维码，减少误选。
