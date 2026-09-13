# Task1 多端运行手册（Runbook）

Kuikly Kotlin Multiplatform 单代码库 App，commonMain 100% Kuikly Compose DSL。本文档记录各端**如何构建、运行、以及已知卡点**，供在对应环境就绪时按步执行，避免重复踩坑。

> 维护约定：改动 `shared` 的源码或任一构建配置后，先用 Android 回归（见 §1），保持 `:shared` 可编译。

---

## 1. 共用先决条件

- Gradle Wrapper 8.5（Tencent 镜像），JDK 17（`org.gradle.java.home`）。
- `:shared` = 业务库，`commonMain` 存放全部 Kotlin/Compose 代码；各端只用薄 host 接入。
- 资产放 `shared/src/commonMain/assets/`（`common/` 为共享图标、`$pageName/` 为页面资产）。
- 页面用 `@Page("name") + ComposeContainer + setContent`；路由名用字符串，勿用类名。

```bash
# Android 回归（本机唯一可跑的目标）
./gradlew :shared:compileDebugKotlinAndroid
```

---

## 2. Android（✅ 已跑通）

- Host：`androidApp`（`KuiklyRenderActivity` + `KRApplication`），通过 `implementation(project(":shared"))` 依赖。
- 入口页：`@Page("router")`。
- 运行：Android Studio 跑 `androidApp`，或

```bash
./gradlew :androidApp:installDebug
```

- 桥接：`androidApp/.../module/KRBridgeModule.kt`（含 `vibrateShort`/`toast`/`showAlert` 等全量实现）。

---

## 3. iOS（⚠️ 代码已就绪，需 macOS 才能构建运行）

- Host：`iosApp/`（SwiftUI，非 Gradle 模块）。Pod 依赖 `OpenKuiklyIOSRender ~> 2.7.0` + `pod 'shared', :path => '../shared'`。
- 消费方式：`pod 'shared'` 经 `shared/shared.podspec`（build script 触发 `:shared:syncFramework`）编译 `shared.framework`。
- 入口：`ContentView.swift` → `KuiklyRenderViewPage(pageName: "router", data: [:])`。

### 在 Mac 上构建运行
```bash
cd iosApp && pod install
open iosApp.xcodeproj    # Xcode 运行
```
（需 macOS + Xcode + CocoaPods；Kotlin/Native 的 `iosArm64` 目标仅在 macOS 支持交叉编译。）

### 已知卡点 / 待办
- 桥接现状：已实现 `copyToPasteboard` / `log` / **`vibrateShort`**（UIImpactFeedbackGenerator，type 取 heavy/medium/light）/ **`toast`**（黑底浮层，2s 淡出）。仍是 no-op 的是 `showAlert` 与 `closePage` —— 二者在 Android 侧同样只是空实现，且 commonMain 未调用，**故 iOS 侧刻意不臆造签名**，需要时两端一起补。
- ⚠️ 上述 .m 改动**未经编译验证**（本机无 macOS / Xcode）。改法是照抄同文件既有 ObjC 风格 + 主线程切回，风险集中在 `UIImpactFeedbackGenerator` / `UIScene` 的可用性（deploymentTarget 14.1，两者均满足）。
- Compose 行为待验证：`LocalConfiguration.statusBarHeight` 顶栏避让、`ModalBottomSheet(4 参)`、`boundsInRoot`、动画 fork 包。改 commonMain 后务必用 Android 回归。

---

## 4. 鸿蒙 HarmonyOS（⚠️ 代码已就绪，但 shared 构建需先补 Compose）

- Host：`ohosApp/`（DevEco/ArkTS，非 Gradle 模块）。`pages/Index.ets` 用 `Kuikly({ pagerName, pagerData, delegate, nativeManager })` 拉起渲染。
- 消费方式：`:shared` 的 `ohosArm64` 目标编译为 `libshared.so`；CMake 链接 `libshared.so` + `libkuikly.so`；NAPI `initKuikly()` 初始化。
- 独立 Gradle 世界：`settings.ohos.gradle.kts` + `build.ohos.gradle.kts` + `shared/build.ohos.gradle.kts`（KBA Kotlin `2.0.21-KBA-010`，Kuikly `2.7.0-2.0.21-ohos`），与主 `settings.gradle.kts`（Kotlin 2.1.21）**不统一**。

### Compose 依赖：已补齐（未验证）
原先 `shared/build.ohos.gradle.kts` 是 DSL 模板 —— 未应用 Compose 编译器插件，`commonMain` 也只有
`core`/`core-annotations` 没有 `compose`，因此 ohosArm64 编译必然失败。现已补：
- `build.ohos.gradle.kts` 根插件块新增 `kotlin("plugin.compose").version("2.0.21-KBA-010")`。
- `shared/build.ohos.gradle.kts` 应用 `kotlin("plugin.compose")`，并在 `commonMain` 增加
  `com.tencent.kuikly-open:compose:${Version.getKuiklyOhosVersion()}`。

> ⚠️ **仍未在本机验证**（无 DevEco / ohosArm64 工具链）。第一个要确认的点是
> `compose-compiler-gradle-plugin:2.0.21-KBA-010` 是否在 KBA 仓库可解析；若不可解析，
> 退路是在 `settings.ohos.gradle.kts` 的 pluginManagement 里补 KBA 仓库地址，或改用 KBA 内置的 Compose 支持。
> 另外 `shared/build.ohos.gradle.kts` **仍未应用 `com.tencent.kuikly-open.kuikly`**：
> 该插件在 ohos 世界的可用版本号与 `core` 的 `2.27.0-2.0.21-ohos` 不同源（缓存里见到的候选是
> `core-gradle-plugin:2.14.1-2.0.21`），盲填版本号会让 ohos 构建直接挂掉，故留待 DevEco 环境实测后再定。

### 在 DevEco 上构建运行
```bash
./ohosApp/runOhosApp.sh          # 注意：脚本用了 macOS 路径 /Applications/DevEco-Studio.app，Windows 需改路径或改用 DevEco GUI
```
步骤：`ohpm install --all` → `hvigorw.js --sync` → `assembleHap` → 签名 → `hdc install` + `aa start -a EntryAbility -b com.example.task1`。

### 其余卡点
- **签名**：`ohosApp/build-profile.json5` 的 `signingConfigs: []` 为空；`runOhosApp.sh` 会校验 `entry-default-signed.hap`，否则报错退出。需在 DevEco 生成签名并回填。
- **资产**：鸿蒙不内置打包资产，靠 `kuiklyCopyAssetsPlugin()` 把 `commonMain/assets` 拷进 `resfile`。原先该插件只被 import 未注册（图标全丢），**现已注册**。若实测未生效，退路是手动拷 `shared/src/commonMain/assets/common/*` → `ohosApp/entry/src/main/resources/resfile/common/*`。

---

## 5. H5 / 小程序（本次不做）

- `settings.gradle.kts` 里的 `include(":h5App")` 与 `include(":miniApp")` 指向的**目录并不存在**，两者均为空壳，无任何代码。`:shared` / `:androidApp` 构建不受影响。
- `:shared` 已配置 `js(IR) { browser { … } }` 目标（产物名 `nativevue2.js`），但缺 host 工程，因此「H5 可运行」目前不成立。
- 小程序为 Kuikly Beta 方案（`./gradlew :miniApp:copyAssets`），已评估，暂不实施。

---

## 6. 跨端桥接契约（各端需一致）

- 桥接名：`HRBridgeModule`（`MODULE_NAME`）。
- commonMain 侧：`shared/src/commonMain/kotlin/com/example/task1/base/BridgeModule.kt`。
- 各端实现：Android `KRBridgeModule.kt`（全量）/ iOS `HRBridgeModule.m`（部分）/ 鸿蒙 `KRBridgeModule.ets`（部分）。新增 native 方法时三端同步补。

---

## 7. 资产与资源规则

- 共享资产 `shared/src/commonMain/assets/common/*`；页面资产 `shared/src/commonMain/assets/$pageName/*`。
- 引用：`ImageUri.commonAssets("sparkles.png")` / `ImageUri.pageAssets("sample.png")`。
- 端打包：Android/iOS 从 `src/commonMain/assets` 打包；鸿蒙需拷贝到 `resfile`（见 §4）；H5 产物 `build/dist/js/productionExecutable/assets`。

---

## 8. 已知 fork 坑（改 UI 时绕开）

- 不用 `fadeIn/fadeOut/scaleIn/scaleOut/alpha`（layerBlock bug）→ 用 `AnimatedVisibility` + `expandVertically/shrinkVertically`。
- `boundsInWindow()` 恒抛 `segqwe` → 用 `boundsInRoot()`（`onGloballyPositioned { it.boundsInRoot() }`）。
- 无 `animateColorAsState`；无 `statusBarsPadding()/safeDrawingPadding()` → 顶栏避让用 `LocalConfiguration.current.statusBarHeight`（Float dp）。
- `ModalBottomSheet()` 旧产物缺 3 参数（按 `visible/onDismissRequest/containerColor/scrimColor` 使用）。
- 动画用 `com.tencent.kuikly.compose.animation.core.*`（fork 包），勿用 `androidx.compose.animation.*`。
- 包规则：仅 `androidx.compose.runtime.*` 用官方包；其余一律 `com.tencent.kuikly.compose.*`；`setContent` 单独 import。

---

## 9. 数据源与自建后端联调

### 数据源链（客户端）

`StockApis.stocks()` / `StockApis.chart()` 组装固定降级链，**页面不感知走了哪一层**：

| 层 | 行情 | 走势 | 分析 |
|---|---|---|---|
| 1 自建后端 | `GET /watchlist` | `GET /chart/{token}?period=` | `GET /analysis/{token}`（带 LLM 缝） |
| 2 直连腾讯 | `qt.gtimg.cn` | —（后端挂了就走样例） | 本地规则引擎在**真实行情**上推导 |
| 3 本地样例 | `SampleStockApi` | `SampleChartApi` | `SampleStockApi` |

降级判据是「这一层有没有取到数据」，不是异常；命中层级由数据源自己盖章进 `WatchlistBundle.source`，
页面据此显示「实时 / 缓存 / 离线」角标与更新时间。

### 起后端

```powershell
cd D:\Codes\Task1\backend
$env:GRADLE_USER_HOME = "D:/gradle-user-home"
.\gradlew.bat run        # 监听 8080
```

冒烟：
```powershell
curl.exe -s "http://127.0.0.1:8080/health"
curl.exe -s "http://127.0.0.1:8080/chart/sz300750?period=day"
```

### 让 App 找到后端

`data/BackendClient.kt` 的 `BackendConfig.hostCandidates` 按序探测 `/health`，首个可用者胜出并缓存：

- Android 模拟器 → `http://10.0.2.2:8080`（已列首位，开箱可用）
- 真机 / 局域网 → 把 `http://<本机IP>:8080` 插到候选首位

全部不可达时自动下沉到直连腾讯，不会白屏。要彻底关掉后端：`StockApis.backendEnabled = false`。

> 真机走 http 明文：已由 `androidApp/src/main/res/xml/network_security_config.xml` 的
> `cleartextTrafficPermitted="true"` 放行，无需额外配置。
> 注意 Kuikly `NetworkModule` 对 **JSON 回包直接给出解析后的对象**（只有非 JSON 回包才会包一层
> `{"data":"<原文>"}`），故 `BackendHttp` 直接读回调的 `data` 字段。

### LLM 开关

后端设 `AI_PROVIDER=llm` + `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` 后，`/watchlist` 的
`summary.text` 由真实 LLM 产出。App 侧不区分来源——`WatchlistViewModel` 只在后端**没给**摘要时才用
本地规则重算，所以 LLM 文案不会被悄悄顶掉。
