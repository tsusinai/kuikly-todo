# 实施计划：修复 UI×AI 信息割裂的两个 P1

> 目标：① 报告页数据源与卡片不一致；② AI 信息无统一叙事（智窗建议与列表摘要割裂）。
> 范围：仅 `shared/commonMain`，不处理 mock 假数据（用户明确指示）。App 侧验收 = 编译门禁 + mock 手测（`shared` 无测试源集）。

## 背景与现状

- **P1-a 报告页数据源**：`WatchlistPage.openReport()` 跳转传空 JSONObject；`AiReportPage.AiReportScreen`
  的 `LaunchedEffect(Unit)` 硬取 `SampleStockApi.fetchWatchlist().stocks.firstOrNull()`（第一只股票），
  与用户实际点开的股票无关。
- **P1-b 无统一叙事**：列表摘要卡用 `deriveSummary` 产出「自选 X 涨 Y 跌，最强/最弱」分析文本，
  但智窗 `buildAdvice` 只拼「重点 3 只、逢低布局 2 只」分组数量串，两者同屏各说各话。

## 已确认的设计决策

1. 智窗「全盘 AI 建议」文案**复用 `summary.text`**（有摘要时），无摘要时回退到分组拼接。
2. 报告页**透传 `code` 参数**，按 code 从 `SampleStockApi` 取数（与 `StockDetailPage` 路由方式一致）。

---

## 任务 1：报告页透传 code 参数

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/pages/WatchlistPage.kt`
- 要做的：`openReport()` 改为带 `activeStock` 的 code 参数。当前实现（第 224-228 行）：
  ```kotlin
  fun openReport() {
      val pj = JSONObject()
      activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("aiReport", pj)
  }
  ```
  改为：
  ```kotlin
  fun openReport() {
      val pj = JSONObject()
      activeStock?.let { pj.put("code", it.code) }   // 透传当前弹层股票 code，供报告页按股取数
      activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("aiReport", pj)
  }
  ```
- 验证：编译通过；`openReport()` 仅此一处调用（`AiBottomSheet` 的 `onViewReport`），签名不变。

## 任务 2：报告页按 code 取数

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/pages/AiReportPage.kt`
- 要做的：读取路由参数 `code`，按 code 取股票与 AI 分析。当前 `AiReportScreen`（第 54-62 行）：
  ```kotlin
  @Composable
  fun AiReportScreen() {
      var stock by remember { mutableStateOf<StockItem?>(null) }
      var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
      LaunchedEffect(Unit) {
          val first = SampleStockApi.fetchWatchlist().stocks.firstOrNull()
          stock = first
          analysis = first?.let { SampleStockApi.fetchAiAnalysis(it.code) }
      }
  ```
  改为：
  ```kotlin
  @Composable
  fun AiReportScreen() {
      var stock by remember { mutableStateOf<StockItem?>(null) }
      var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
      // 从路由参数读取目标股票 code（由 WatchlistPage.openReport 透传）；缺省回退首只
      val code = LocalConfiguration.current.pageData.params.optString("code")
      LaunchedEffect(code) {
          val target = SampleStockApi.fetchStock(code) ?: SampleStockApi.fetchWatchlist().stocks.firstOrNull()
          stock = target
          analysis = target?.let { SampleStockApi.fetchAiAnalysis(it.code) }
      }
  ```
  需新增 import：`com.tencent.kuikly.compose.ui.platform.LocalConfiguration`。
- 验证：编译通过；`LaunchedEffect` 的 key 由 `Unit` 改为 `code`（参数变化时重新取数）。

## 任务 3：智窗建议复用摘要文本

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/pages/WatchlistPage.kt`
- 要做的：`advice` 改为复用 `summary.text`，无摘要时回退到 `buildAdvice`。当前（第 235-238 行）：
  ```kotlin
  LaunchedEffect(dimension, stocks) {
      advice = buildAdvice(groupStocks(stocks, dimension))
  }
  ```
  改为：
  ```kotlin
  // 智窗「全盘 AI 建议」优先复用列表摘要文本（与摘要卡同源，统一叙事）；
  // 摘要为空（如无数据）时回退到「分组维度覆盖」拼接。
  LaunchedEffect(dimension, stocks, summary) {
      advice = summary?.text?.takeIf { it.isNotBlank() }
          ?: buildAdvice(groupStocks(stocks, dimension))
  }
  ```
- 验证：编译通过；`buildAdvice` 保留（作为回退分支，非死代码）。

## 收尾验证

- App 编译门禁：`GRADLE_USER_HOME=D:/gradle-user-home-2`，`:shared:compileDebugKotlinAndroid --rerun-tasks` 应 `BUILD SUCCESSFUL`。
- mock 手测点（口头核验即可，无需真机）：
  1. 自选页点某只非首只股票 → 弹层 → 「查看完整报告」→ 报告页标题/名称应为该股票，而非贵州茅台。
  2. 智窗建议文案应展示「自选 X 涨 Y 跌…」类摘要，而非「重点 3 只…」。
