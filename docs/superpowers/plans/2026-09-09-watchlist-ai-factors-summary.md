# AI 因子与摘要 (A1+A3, D1-D4) Implementation Plan

> **For Claude:** Use `${SUPERPOWERS_SKILLS_ROOT}/skills/collaboration/executing-plans/SKILL.md` to implement this plan task-by-task.

**Goal:** 本地 mock 先完整演示 D1 摘要卡 / D2 动态标签 / D3 时效提示 / D4 因子详情，同时后端具备同款推导与「可配置、可回退」的 LLM 摘要接口，为后续切后端数据源铺平契约。

**Architecture:** 方案 3 阶段性迁移。App 侧先扩展数据模型 + 纯函数推导（tags/summary/基准差），WatchlistPage/StockCard/AiReportPage 直接消费产出；后端同步实现同款推导（QuoteParser 扩字段 + 静态行业表 + 指数基准 + tags/summary 镜像），LLM 仅做摘要文案且失败回退规则。切换数据源时 App 只改取数来源、删除本地推导。

**Tech Stack:** Kuikly Compose DSL + KMP shared(仅 androidx.compose.runtime 官方包); 后端 Kotlin 2.1.21 + Ktor 2.3.12 + kotlinx-serialization; 两端 kotlin.time.Clock(实验性) 盖时间戳。

---

## 全局约束(每个任务都必须对照)

- **契约单一事实源**: `docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md`。M1 先更新该文档,后续任务字段/命名必须与它一致。
- **腾讯字段位号**(2026-09-09 真实样本核实,勿再漂移): change(31)/changePct(32)/high(33)/low(34)/pe(39,A股与HK同)/floatCap(44,亿)/marketCap(45,亿)/outer(7)/inner(8)/amplitude(43)/turnover(A股38,HK 59)/volumeRatio(49,A股;HK 无→0)/amount(37,A股单位万→×1e4归一为元,HK 原值;⚠️ 实现时再取 1 个 HK 样本复核 37/59)。
- **大盘指数**(同一声明时间追加,位号与个股一致): sh000001(沪)/sz399001(深)/hkHSI(港)。
- **新阈值常量(两端各自单一事实源,互相镜像)**: TAG_VOLUME_RATIO=1.5; TAG_AMPLITUDE_PCT=4.0; TAG_LEAD_PCT=3.0; STALE_MS=5*60_000L。
- **tags 推导规则(两端逐条镜像)**: 强于大盘(benchmarkDelta>0); 量比异动(volumeRatio≥1.5); 放量上攻(changePct>0 && volumeRatio≥1.5); 振幅放大(amplitude≥4.0); 领涨(changePct≥3.0); 低位企稳(pe in 1.0..20.0 && changePct<0)。
- **门禁**: shared 编译 = `$env:GRADLE_USER_HOME="D:/gradle-user-home"; .\gradlew.bat :shared:compileDebugKotlinAndroid`; 后端测试 = `cd backend; $env:GRADLE_USER_HOME="D:/gradle-user-home"; .\gradlew.bat test`(需沙箱外批准; 用户 home 含撇号,GRADLE_USER_HOME 必填)。
- **提交**: 仓库策略默认不提交;若用户要求提交,按任务粒度 `git add <精确文件>`(禁止 -A/.,shared/ 有历史未提交工作树)。
- **shared 无测试源集**: App 侧任务以编译门禁 + mock 手测验收;TDD 仅用于后端(M5)。
- **HK 独有**: 量比/内外盘 HK 恒 0,tags 推导自动跳过(规则基于数值,0 不触发阈值)。

---

## M1: 契约 spec 更新(纯文档,先于一切代码)

**Files:**
- Modify: `docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md`

**Step 1:** 在 StockItem JSON 示例中追加字段(带单位注释): turnover(%), volumeRatio, amplitude(%), amount(元), outer, inner, industry(未分类), benchmarkDelta(null=缺指数), tags([])。

**Step 2:** WatchlistResponseDto 追加 `summary: { text, generatedAt, stale }`;AiAnalysis 追加 `factors: { momentum, value, risk, industry, industryRank(-1=无), benchmarkDelta }`。

**Step 3:** 新增「指数基准」小节: 请求端按 code 市场追加 sh000001/sz399001/hkHSI;benchmarkDelta = 个股changePct − 指数changePct;指数缺失→null(UI 显示 —,tags 跳过基准类)。

**Step 4:** 新增「tag 规则与阈值」小节(照抄全局约束的阈值常量与规则,标注两端镜像)。

**Step 5:** 新增「时效」小节: generatedAt 来源 = 服务端/推导时间;过期判定 STALE_MS=5min;过期后摘要与 tags 降级提示,不影响列表渲染。

**验收:** 文档字段与「全局约束」逐字一致;无代码改动。

---

## M2: App 数据模型扩展(shared)

### Task 1: StockItem/AiAnalysis/WatchlistBundle 新字段

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/data/StockApi.kt`

**Step 1:** 在 StockApi.kt 追加两个模型(放 WatchlistBundle 之前):

```kotlin
/** 列表级 AI 摘要(D1/D3)。stale 由渲染端按 STALE_MS 判定后盖章。 */
data class AiSummary(
    val text: String,
    val generatedAt: Long,
    val stale: Boolean = false,
)

/** 弹层/报告页因子明细(D4)。industryRank -1 = 无法排名。 */
data class AiFactors(
    val momentum: Int,   // 动量分 0-60
    val value: Int,      // 价值分 0-25
    val risk: Int,       // 风险分 0-8
    val industry: String,
    val industryRank: Int = -1,
    val benchmarkDelta: Double? = null,
)
```

**Step 2:** StockItem 尾部追加(全部带默认值,避免炸现有调用点):

```kotlin
    val turnover: Double = 0.0,      // 换手率 %
    val volumeRatio: Double = 0.0,   // 量比
    val amplitude: Double = 0.0,     // 振幅 %
    val amount: Long = 0L,           // 成交额(元)
    val outer: Long = 0L,            // 外盘(手)
    val inner: Long = 0L,            // 内盘(手)
    val industry: String = "未分类",
    val benchmarkDelta: Double? = null, // 个股changePct − 指数changePct
    val tags: List<String> = emptyList(), // D2 动态标签
```

**Step 3:** WatchlistBundle 追加 `val summary: AiSummary = AiSummary("", 0L)`(默认空摘要,构造器兼容)。

**Step 4:** AiAnalysis 尾部追加 `val factors: AiFactors = AiFactors()`。

**Step 5:** 编译门禁压绿(命令见全局约束)。预期: 现有 7 行 SampleStockApi 与 TencentStockApi 构造无需改动即通过。

### Task 2: SampleStockApi 样本数据扩展(mock 素材)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/data/StockApi.kt`(SampleStockApi.list)

**Step 1:** 给 7 个条目补 `industry`(贵州茅台/五粮液=白酒, 腾讯控股=互联网, 宁德时代=电池, 比亚迪=汽车, 中国平安=保险, 中芯国际=半导体)。

**Step 2:** 补行情因子(暂不含 tags/benchmarkDelta,由 Task 4 推导后 copy 覆盖): turnover 0.4..2.8、volumeRatio 0.7..2.3、amplitude 1.0..5.2、amount、outer/inner 给合理值(可参考真实样本数量级: 茅台 amount≈41.7亿, 成交额 4.17e9)。

**Step 3:** 编译门禁压绿。预期: 无编译错误。

---

## M3: App 推导纯函数(shared,编译门禁 + mock 手测)

### Task 3: FactorThresh + deriveTags + deriveBenchmarkDelta + deriveSummary

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/data/FactorDeriver.kt`

**Step 1:** 新建 FactorDeriver.kt,内容(两端镜像,阈值逐一对应全局约束):

```kotlin
package com.example.task1.data

/** D1/D2 阈值:单一事实源(与后端 AiThresh 镜像)。 */
object FactorThresh {
    const val TAG_VOLUME_RATIO = 1.5
    const val TAG_AMPLITUDE_PCT = 4.0
    const val TAG_LEAD_PCT = 3.0
    const val STALE_MS = 5 * 60_000L
}

/** 大盘基准差:指数缺/失败返回 null。mock 阶段指数由 MockBenchmark 提供。 */
fun deriveBenchmarkDelta(item: StockItem, indexChangePct: Double?): Double? =
    indexChangePct?.let { item.changePct - it }

/** D2 动态标签:纯函数,规则与后端逐条镜像;0 值字段自动不触发。 */
fun deriveTags(item: StockItem): List<String> {
    val t = mutableListOf<String>()
    item.benchmarkDelta?.let { if (it > 0) t += "强于大盘" }
    if (item.volumeRatio >= FactorThresh.TAG_VOLUME_RATIO) {
        t += "量比异动"
        if (item.changePct > 0) t += "放量上攻"
    }
    if (item.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) t += "振幅放大"
    if (item.changePct >= FactorThresh.TAG_LEAD_PCT) t += "领涨"
    if (item.pe in 1.0..AiThresh.PE_GOOD && item.changePct < 0) t += "低位企稳"
    return t
}

/** D1 摘要:涨跌家数 + 最强/最弱 + 一句倾向。stale 由渲染端按 now-fetchedAt 判定。 */
fun deriveSummary(stocks: List<StockItem>, fetchedAt: Long): AiSummary {
    if (stocks.isEmpty()) return AiSummary("暂无行情数据,下拉刷新重试", fetchedAt)
    val up = stocks.count { it.changePct > 0 }
    val down = stocks.count { it.changePct < 0 }
    val best = stocks.maxByOrNull { it.changePct }
    val worst = stocks.minByOrNull { it.changePct }
    val avg = stocks.map { it.changePct }.average()
    val tone = if (avg >= 0) "今日整体偏强,关注领涨股" else "今日偏弱,控制仓位"
    val text = "自选${up}涨${down}跌;最强「${best?.name}」${best?.changePct}%,最弱「${worst?.name}」${worst?.changePct}%。$tone。"
    return AiSummary(text, fetchedAt)
}

/** mock 阶段的大盘指数(固定值,模拟指数行情)。 */
object MockBenchmark {
    val changePctByMarket: Map<String, Double> = mapOf("sh" to 0.62, "sz" to -0.18, "hk" to 0.35)
    fun of(code: String): String = when {
        code.startsWith("6") -> "sh"; code.startsWith("0") || code.startsWith("3") -> "sz"; else -> "hk"
    }
}
```

**Step 2:** 编译门禁压绿。

### Task 4: deriveAiAnalysis 因子化 + SampleStockApi 接线

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/data/AiProfileDeriver.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/task1/data/StockApi.kt`(SampleStockApi)

**Step 1:** AiProfileDeriver.kt 里把评分三部分抽成 `private fun scoreParts(changePct: Double, pe: Double): Triple<Int, Int, Int>`(momentum/value/risk 现状逻辑原样搬入),deriveAiProfile 复用;deriveAiAnalysis 追加 `factors = AiFactors(scoreParts(changePct, pe) 三值, item.industry, benchmarkDelta = item.benchmarkDelta)`。

**Step 2:** SampleStockApi.fetchWatchlist 返回前 map 一遍:{ item.copy(benchmarkDelta = deriveBenchmarkDelta(item, MockBenchmark.changePctByMarket[MockBenchmark.of(item.code)])).let { it.copy(tags = deriveTags(it)) } }。

**Step 3:** SampleStockApi.fetchWatchlist 的 Bundle 填 `summary = deriveSummary(处理后的 list, nowMillis())`(与 fetchedAt 用同一时刻)。

**Step 4:** SampleStockApi.fetchAiAnalysis 保持现状(deriveAiAnalysis 已带 factors)。

**Step 5:** 编译门禁压绿;mock 手测清单(验收时走查): 列表卡片 tags 出现、摘要卡文案非空、弹层分析含因子。

---

## M4: UI 落地(shared,编译门禁 + mock 手测)

### Task 5: WatchlistPage 摘要卡(D1/D3)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/pages/WatchlistPage.kt`

**Step 1:** 状态区追加 `var summary by remember { mutableStateOf<AiSummary?>(null) }`;applyFetch 里 `summary = bundle.summary.takeIf { it.text.isNotBlank() }`。

**Step 2:** LazyColumn 中 `item { MarketOverviewBar() }` 之前插入摘要卡 item:

```kotlin
item {
    summary?.let { s ->
        val stale = s.stale || (nowMillis() - s.generatedAt > FactorThresh.STALE_MS)
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .background(if (stale) AppColors.HeaderBg else AppColors.AiLight, RoundedCornerShape(8.dp))
            .padding(10.dp)) {
            Column {
                Text("AI 摘要 · ${formatTime(s.generatedAt)}", color = AppColors.MainText, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(s.text, color = AppColors.MainText, fontSize = 14.sp)
                if (stale) Text("基于较旧数据,建议下拉刷新", color = AppColors.RiskText, fontSize = 11.sp)
            }
        }
    }
}
```

`nowMillis` 在 data 包为 internal,页面 import 后直接用;stale 判定不与盘中/收盘做日历区分(演示级)。

**Step 3:** 编译门禁压绿。

### Task 6: StockCard 标签行(D2)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/components/StockCard.kt`

**Step 1:** 在 `TopRow(item)` 之后、建议行之前插入:

```kotlin
if (item.tags.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    TagRow(item.tags)
}
```

**Step 2:** 文件内新增私有 `@Composable private fun TagRow(tags: List<String>)`:Row 横向排列(间距 6.dp),每个 tag 一个圆角描边小胶囊(Color.White 背景, border 1.dp AppColors.AiLight, RoundedCornerShape(4.dp), padding 横6竖2, Text fontSize 11.sp color AppColors.MainText)。

**Step 3:** 编译门禁压绿。

### Task 7: AiReportPage 因子区(D4)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/pages/AiReportPage.kt`

**Step 1:** BuySection 之后追加 `FactorSection(analysis.factors)` 区块(analysis 非空时)。

**Step 2:** AiReportPage.kt 内新增私有 Composable `FactorSection(f: AiFactors)`:
  - 三行文本: 动量分 f.momentum / 价值分 f.value / 风险分 f.risk;
  - 一行: 行业 f.industry(industryRank>=0 时追加「组内第 ${rank+1}」);
  - 一行: 强于大盘 f.benchmarkDelta 格式化(带符号 x.xx%,null 显示 —)。

**Step 3:** 编译门禁压绿;mock 手测走查清单(见 Task 4 Step 5 + 本条): 报告页出现因子明细、数字与卡片画像一致。

---

## M5: 后端同款推导 + 可配置 LLM(TDD)

### Task 8: QuoteParser/RawStock 扩字段

**Files:**
- Modify: `backend/src/main/kotlin/com/example/task1/backend/parse/QuoteParser.kt`
- Modify: `backend/src/main/kotlin/com/example/task1/backend/model/RawStock.kt`
- Test: `backend/src/test/kotlin/com/example/task1/backend/parse/QuoteParserTest.kt`

**Step 1(写失败测试):** QuoteParserTest 真实 sh600000 回归测试追加断言: turnover=0.15、amplitude=0.86、amount=467_550_000L(46755万→元)、volumeRatio=0.70、outer=208325、inner=297000。

**Step 2:** 运行 `.\gradlew.bat test --tests "com.example.task1.backend.parse.QuoteParserTest"`。预期 FAIL(新字段不存在)。

**Step 3:** RawStock 追加 `turnover/volumeRatio/amplitude/amount/outer/inner`;QuoteParser 常量:`OUTER=7, INNER=8, TURNOVER_A=38, TURNOVER_HK=59, AMPLITUDE=43, VOLUME_RATIO=49, AMOUNT=37`;parse 中 amount 按市场分支(A股 37 位×1e4 元;HK 原值);turnover 按 market 选 38/59。

**Step 4:** `.\gradlew.bat test` 全绿(现有 32 + 新断言)。

**Step 5(复核):** 取 1 个真实 HK 样本(hk00700)确认 37 位为原值金额、59 位为换手率;若不符,调整常量与断言并记录到契约文档。

### Task 9: DTO 扩展(StockItemDto/WatchlistResponseDto/AiAnalysisDto)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/task1/backend/dto/Dtos.kt`
- Test: `backend/src/test/kotlin/com/example/task1/backend/dto/DtosTest.kt`

**Step 1(写失败测试):** DtosTest 追加 round-trip 断言新字段(串行化后反序列化,含 summary 与 factors)。

**Step 2:** 运行该测试。预期 FAIL。

**Step 3:** 新增 `@Serializable AiSummaryDto(text, generatedAt, stale=false)`、`AiFactorsDto(momentum, value, risk, industry, industryRank=-1, benchmarkDelta=null)`;StockItemDto 追加 turnover/volumeRatio/amplitude/amount/outer/inner/industry/benchmarkDelta/tags(默认值对齐契约);WatchlistResponseDto 追加 `summary: AiSummaryDto? = null`;AiAnalysisDto 追加 `factors: AiFactorsDto = AiFactorsDto(...)`。

**Step 4:** 测试全绿。

### Task 10: 静态行业表 + 后端因子镜像(deriveTags/deriveSummary/deriveBenchmarkDelta)

**Files:**
- Create: `backend/src/main/kotlin/com/example/task1/backend/ai/FactorRules.kt`
- Create: `backend/src/main/kotlin/com/example/task1/backend/ai/IndustryMap.kt`
- Test: `backend/src/test/kotlin/com/example/task1/backend/ai/FactorRulesTest.kt`

**Step 1(写失败测试):** FactorRulesTest 覆盖: benchmarkDelta 计算(null 指数→null);tags 六规则各一例 + 0 值不触发;deriveSummary 空表/全跌/单票领涨三例;IndustryMap 已知 code 与未知 code。

**Step 2:** 运行测试。预期 FAIL(文件不存在)。

**Step 3:** IndustryMap: 7 只 codebook code → 行业,未知 → "未分类"。FactorRules: 镜像 App FactorDeriver 的 FactorThresh 常量与 deriveTags/deriveSummary/deriveBenchmarkDelta(签名以 RawStock/List<RawStock> 为准,输出 DTO 字段),摘要文本逐字镜像 App。

**Step 4:** 测试全绿。

### Task 11: WatchlistService 指数基准 + 摘要组装

**Files:**
- Modify: `backend/src/main/kotlin/com/example/task1/backend/service/WatchlistService.kt`
- Test: `backend/src/test/kotlin/com/example/task1/backend/service/WatchlistServiceTest.kt`

**Step 1(写失败测试):** FakeClient 记录 lastQuery,断言请求串包含 `sh000001,sz399001,hkHSI`(按 tokens 市场去重);响应含指数原始体时 benchmarkDelta 正确、stocks[0].tags 含 "强于大盘";指数体缺失 → benchmarkDelta null 且 summary 文本含 "基准数据缺失"。

**Step 2:** 运行测试。预期 FAIL。

**Step 3:** fetchWatchlist: 由 tokens 市场推导指数 code 集合,追加到 client.fetch(query);解析指数体(QuoteParser.parse 复用,只取 changePct);逐股 benchmarkDelta = stock.changePct − index.changePct;组 tags;摘要由 SummaryProvider 产(见 Task 12),规则兜底文本含基准缺失注记。fetchAnalysis 同步带 benchmarkDelta(拉对应指数)。

**Step 4:** 全量测试绿。

### Task 12: 可配置 LLM 摘要接口(AI_PROVIDER=llm,失败回退)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/task1/backend/config/Config.kt`
- Create: `backend/src/main/kotlin/com/example/task1/backend/ai/SummaryProvider.kt`
- Test: `backend/src/test/kotlin/com/example/task1/backend/config/ConfigTest.kt` + 新建 `SummaryProviderTest.kt`

**Step 1(写失败测试):** ConfigTest: LLM_BASE_URL/LLM_API_KEY/LLM_MODEL/LLM_TIMEOUT_MS 解析与非法值失败;SummaryProviderTest: 规则兜底文案;伪造 http 抛错/非 200 → 回退兜底文案;200 且 JSON 含 text → LLM 文案。

**Step 2:** 运行。预期 FAIL。

**Step 3:** Config 追加 `llmBaseUrl/llmApiKey/llmModel/llmTimeoutMs`(env 覆盖,默认空/4500);新建:

```kotlin
interface SummaryProvider { suspend fun summarize(context: String, fallback: String): String }
class RuleSummaryProvider : SummaryProvider { override suspend fun summarize(ctx: String, fb: String) = fb }
class LlmSummaryProvider(private val config: Config, private val fallback: SummaryProvider = RuleSummaryProvider()) : SummaryProvider {
    // POST ${config.llmBaseUrl}/chat/completions, OpenAI 兼容:
    //   model=config.llmModel, messages=[system+user(context)],
    //   要求 JSON 返回 {"text": "..."};
    //   HTTP 非 2xx / 超时 / 解析失败 → fallback.summarize(ctx, fb)
}
```

**Step 4:** 测试全绿。

### Task 13: Application/Routes 接线 + 全量验收

**Files:**
- Modify: `backend/src/main/kotlin/com/example/task1/backend/Application.kt`
- Modify: `backend/src/main/kotlin/com/example/task1/backend/service/WatchlistService.kt`(构造注入 summaryProvider)
- Test: `backend/src/test/kotlin/com/example/task1/backend/route/RoutesTest.kt`

**Step 1(写失败测试):** RoutesTest 追加: /watchlist 正常响应包含 summary 字段;缺指数体时仍 200 且 summary 存在。

**Step 2:** 运行。预期 FAIL。

**Step 3:** WatchlistService 构造追加 `summaryProvider: SummaryProvider = RuleSummaryProvider()`;fetchWatchlist 组装 summary(AiSummaryDto(text=summaryProvider.summarize(context, 规则文本), generatedAt=clock.nowMillis(), stale=false))。Application: aiProvider=="llm" && llmBaseUrl 非空 → LlmSummaryProvider(config),否则 RuleSummaryProvider;注入 service。

**Step 4:** 全量 `.\gradlew.bat test` 绿;shared 编译不受影响(未动)。

**Step 5(验收手测):** 起服务(GRADLE_USER_HOME 环境),`/watchlist?codes=sh600519,hk00700,sz300750` 返回含新字段与 summary;`/analysis/sh600519` 含 factors。

---

## M6(后续分支,本次不做)

- App 数据源切后端:TencentStockApi 换走 /watchlist 契约,删除 App 侧 FactorDeriver 与本地推导,仅保留渲染与 D3 时效逻辑。

## 执行交接

计划已保存。执行选项: 1) Subagent 驱动(本会话,每任务新 agent + 任务间审查); 2) 并行会话(executing-plans 分批)。