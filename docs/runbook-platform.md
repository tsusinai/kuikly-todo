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
- 桥接不全：`iosApp/iosApp/KuiklyExpand/Modules/HRBridgeModule.m` 只实现 `copyToPasteboard`/`log`；commonMain `BridgeModule.kt` 调用的 `vibrateShort`/`toast`/`showAlert` 在 iOS 侧 **no-op（不崩）**。需要触觉/Toast 时按需补齐。
- Compose 行为待验证：`LocalConfiguration.statusBarHeight` 顶栏避让、`ModalBottomSheet(4 参)`、`boundsInRoot`、动画 fork 包。改 commonMain 后务必用 Android 回归。

---

## 4. 鸿蒙 HarmonyOS（⚠️ 代码已就绪，但 shared 构建需先补 Compose）

- Host：`ohosApp/`（DevEco/ArkTS，非 Gradle 模块）。`pages/Index.ets` 用 `Kuikly({ pagerName, pagerData, delegate, nativeManager })` 拉起渲染。
- 消费方式：`:shared` 的 `ohosArm64` 目标编译为 `libshared.so`；CMake 链接 `libshared.so` + `libkuikly.so`；NAPI `initKuikly()` 初始化。
- 独立 Gradle 世界：`settings.ohos.gradle.kts` + `build.ohos.gradle.kts` + `shared/build.ohos.gradle.kts`（KBA Kotlin `2.0.21-KBA-010`，Kuikly `2.7.0-2.0.21-ohos`），与主 `settings.gradle.kts`（Kotlin 2.1.21）**不统一**。

### 🔴 首要障碍：`shared/build.ohos.gradle.kts` 是 DSL 模板，缺 Compose
- 未应用 `com.tencent.kuikly-open.kuikly` / `kotlin("plugin.compose")`。
- `commonMain` 只有 `core`/`core-annotations`，**无 `compose` 依赖** ⇒ commonMain 的 Compose 代码编译不过。
- **必须先修**：commonMain 增 `com.tencent.kuikly-open:compose:${getKuiklyOhosVersion()}`，并应用 Compose 插件（若 KBA `2.0.21-KBA-010` 无内置 compose 插件，需排查可用版本；此步要能编译过 `:shared` 的 ohosArm64 再继续）。

### 在 DevEco 上构建运行
```bash
./ohosApp/runOhosApp.sh          # 注意：脚本用了 macOS 路径 /Applications/DevEco-Studio.app，Windows 需改路径或改用 DevEco GUI
```
步骤：`ohpm install --all` → `hvigorw.js --sync` → `assembleHap` → 签名 → `hdc install` + `aa start -a EntryAbility -b com.example.task1`。

### 其余卡点
- **签名**：`ohosApp/build-profile.json5` 的 `signingConfigs: []` 为空；`runOhosApp.sh` 会校验 `entry-default-signed.hap`，否则报错退出。需在 DevEco 生成签名并回填。
- **资产**：鸿蒙不内置打包资产。需 `kuiklyCopyAssetsPlugin()` 或手动拷贝 `shared/src/commonMain/assets/common/*` → `ohosApp/entry/src/main/resources/resfile/common/*`。注意：`entry/hvigorfile.ts` 只挂了 `kuiklyCompilePlugin()`，`kuiklyCopyAssetsPlugin` 被 import 但**未注册**。

---

## 5. 小程序（本次不做）

- `settings.gradle.kts` 有 `include(":miniApp")` 空壳，无任何代码。Kuikly 小程序为 Beta 方案（`./gradlew :miniApp:copyAssets`）。已评估，暂不实施。

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
