# 行情自选页 + AI 分析面板 — 设计规范

- **日期**: 2026-08-24
- **项目**: `D:\Codes\Task1`(Kuikly Compose KMP 应用,单一 codebase → Android/iOS/鸿蒙/H5/小程序)
- **依据**: `设计规范思路.md` + Figma 设计稿(fileKey `AOGH9OMqmNFPTeoiuMJzpE`,frames `1:360` 主屏 / `1:177` ai-bottom-sheet)

## 目标

用 Kuikly Compose UI 实现两屏:
1. **行情/自选列表页** — 顶部搜索、tab 栏、大盘摘要、股票卡片列表、底部「分析智窗」栏、底部导航。
2. **AI 分析面板** — 圆角顶部底部弹层(ai-bottom-sheet),含趋势/风险/买入建议/CTA。

遵循 `rules/kuiklyComposeDSL.mdc`:只有 `androidx.compose.runtime.*` 用官方包,其余一律 `com.tencent.kuikly.compose.*`;页面继承 `ComposeContainer` + `@Page("name")`。

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 页面结构 | 一个 `@Page("watchlist")` 列表页 + 页内 `ModalBottomSheet` AI 面板;另预留 `@Page("aiReport")` 独立落地页 |
| AI 显示模式 | 由 `StockItem.aiEnabled` 数据标记决定(推荐股显示 AI 卡,其余常规单行卡) |
| tab 切换 | 仅选中态(选中 `sp+1`);非自选 tab 显示空态占位 |
| 图标资产 | 从 Figma 下载 9 张 SVG 进 `assets/common/`,用 `Image` 渲染;若 Image 不渲 SVG 则转 PNG@2x |
| 数据 | `StockApi` 接口预留 + `SampleStockApi` 内置代表性样例;后续接真实行情只换实现 |
| 拖拽实现 | **纯 Compose** `pointerInput(item)` + `detectDragGestures`,长按抢占指针规避滚动冲突(尽力而为) |
| 交互映射 | 点卡→「股票详情」占位页;拖卡→AI 面板;点「分析智窗」栏→AI 面板(兜底保证可达) |

## 配置文件布局

全部位于 `shared/src/commonMain/kotlin/com/example/task1/`:

```
theme/AppColors.kt             设计 token:颜色/渐变常量(object AppColors)
data/StockApi.kt               interface StockApi + SampleStockApi + 数据模型
components/StockCard.kt        AI卡(80dp)/常规卡(46dp);aiEnabled 自动二选一
components/TabBar.kt           LazyRow 可选 tab;选中 sp+1
components/MarketOverviewBar.kt  大盘摘要(+23.45亿/已收盘/涨跌进度条)
components/AiBottomSheet.kt    AI 面板内容(handle/header/迷你卡/趋势/风险/买入/CTA)
components/AiBottomBar.kt      底部「分析智窗」栏(拖放目标 + 点击兜底)
components/BottomNav.kt        行情/分析/我的
pages/WatchlistPage.kt         @Page("watchlist")::ComposeContainer
pages/StockDetailPage.kt       @Page("stockDetail") 预留详情占位
pages/AiReportPage.kt          @Page("aiReport") 预留「完整报告」占位
assets/common/*.svg            9 张图标(下载自 Figma)
```

## 设计 token

| 颜色 | 值 | 用途 |
|---|---|---|
| 涨/价格红 | `#df0004` | 价格、涨跌、涨数、趋势信号 |
| 强调绿 | `#4a6b5a` | 风险低段、跌幅/进度底、MACD 强调字 |
| 次级灰 | `#666` | 股票代码、说明文案 |
| 主文字 | `#2d2d2d` | 股票名、标题 |
| 边框 | `#b5c5c5` | 卡片边框、进度底、进度亮色 |
| 页面背景 | `#f7f7f7` | 页面/弹层背景 |
| 头部背景 | `#f6dcd7` | 顶部区背景 |
| AI 渐变 | `#e9b8ac → #f6dcd7` | sparkles 圆形底、进度填充渐变 |
| AI 徽标底 | `rgba(233,184,172,0.15)` | 「Power by Ai模型」 |
| 涨跌徽标底 | `rgba(74,107,90,0.1)` | `+4.82`/`+2.35%` 徽标 |
| 风险橙 | `#f59e0b` | 中低风险、风险中段 |
| 推荐紫 | `#a78bfa` | 推荐指数 |
| 风险字 | `#884d3a` | 当前评级/高风险 |

集中为 `object AppColors`,渐变 `val AiGradient = listOf(Color(0xFFE9B8AC), Color(0xFFF6DCD7))`。

## 数据模型与预留接口

```kotlin
data class StockItem(
    val id: String, val name: String, val code: String,
    val price: Long,          // 按分存储,format 复用 base/Utils.kt convertToPriceStr
    val changePct: Double,
    val aiEnabled: Boolean,
    val aiBrief: String,      // 如 "MACD金叉形成，建议查看详情"
)

data class AiAnalysis(
    val trendLabel: String,       // "短期看涨信号明显"
    val trendText: String,
    val riskLevel: String,        // "中低风险"
    val riskText: String,
    val score: Int,               // 推荐指数 85/100
    val targetPrice: Long, val stopLossPrice: Long,
)

interface StockApi {
    suspend fun fetchWatchlist(): List<StockItem>
    suspend fun fetchAiAnalysis(code: String): AiAnalysis
}
```

`SampleStockApi` 内置代表性样例(贵州茅台 `aiEnabled=true`、腾讯控股、宁德时代、比亚迪、中国平安…),部分标记 `aiEnabled` 以演示两种卡片;后续接真实行情只替换实现。

## 组件与外观

- **StockCard**:白色卡片、`border 1.dp Color(0xFFB5C5C5)`、`RoundedCornerShape(12.dp)`、`padding 12.dp`,间距 10.dp。
  - AI 卡(80dp):上排=名称/代码 | 价格 + `+4.82` + `+2.35%` 徽标;下排=sparkles 圆形 + "近5日连续上涨," + 绿字 `MACD...`。
  - 常规卡(46dp):仅上排。
- **TabBar**:LazyRow(自选/全球/港股/期贷/A股/美股/黄金),选中 `sp+1`(20→21)。
- **MarketOverviewBar**:`+23.45亿`(红 24sp)、`已收盘`、`2026-12-12 星期二`、`涨23`/`跌12` + 涨跌进度条(红渐变 267/392,余绿)。
- **AiBottomSheet**(`ModalBottomSheet`,containerColor `#F7F7F7`,dismissOnDrag=true,animationDurationMillis=250):handle / header(sparkle+智能分析+Power by Ai模型+X)/ 迷你卡 / 涨势分析(sparkline)/ 风险评估(三段 gauge)/ 买入建议(进度条)/ CTA「查看完整报告」。
- **AiBottomBar**:sparkle + `分析智窗`(18sp bold) + `拖入股票进入ai分析`(13sp)。
- **BottomNav**:行情/分析/我的。

## 交互模型

- 点卡片主体 → `openPage("stockDetail", item)`(预留占位页)。
- 拖拽卡片到「分析智窗」栏松手 → 以该股票打开 AI 面板。
- 点「分析智窗」栏 → 以默认/首个自选股打开 AI 面板(点击兜底,保证恒可达)。
- 面板内「查看完整报告」→ `openPage("aiReport")`。

## 拖拽算法(纯 Compose,最高风险)

- 页级状态:`draggingStock: StockItem?` + 拖拽全局 Y。
- 每张卡 `Modifier.pointerInput(item)`:`awaitLongPressOrCancellation` 长按识别,让拖拽在 LazyColumn 垂直滚动前抢占指针;位移超阈值即标记 `draggingStock`,卡片视觉跟随手指(悬浮)。
- 落点判定:用 `onGloballyPositioned` 记录「分析智窗」栏的窗口 Y(或用「屏高-栏高-导航高」推算),`onDragEnd/onDragCancel` 时命中栏→弹面板,否则取消。
- 若 Kuikly 手势仍与 `LazyColumn` 垂直滚动竞态:临时 `bouncesEnable(false)` / 消费事件;「点智窗栏即开面板」为始终可达的兜底。

## 动画

- 面板开合:`ModalBottomSheet.animationDurationMillis = 250`。注意 Kuikly 的 `fadeIn/scaleIn` 当前有 bug(layerBlock 缺失),**不使用**。
- 列表加载:简单 `AnimatedVisibility` 展开/收起,或直接线性渲染。

## 验证

1. `./gradlew :shared:compileDebugKotlinAndroid`(JDK17)。
2. Android 端以 `pageName=watchlist` 启动(`KuiklyRenderActivity` 读 extra)。
3. 逐项核对:卡片 AI/常规两态;tab 选中 `sp+1`;拖拽/点击开面板;CTA→`aiReport`;关闭动画。

## 明确不做

- 不引入新依赖/不装 Tailwind。
- 不做真实行情 API(仅预留接口)。
- 不做完整股票详情与完整报告(仅占位页)。
- 拖拽为尽力而为,靠「点智窗栏」兜底保证可达。
