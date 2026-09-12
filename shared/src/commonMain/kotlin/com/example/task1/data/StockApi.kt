package com.example.task1.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 当前时间（epoch millis）。KMP 无系统时钟,用 stdlib 实验性 Clock;封装一处便于替换。 */
@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

data class StockItem(
    val id: String,
    val name: String,
    val code: String,
    val price: Long,          // 分
    val change: Long,         // 涨跌额（分，带符号）：+4362 -> +43.62
    val changePct: Double,    // 涨跌幅 %
    val aiEnabled: Boolean,
    val aiBrief: String,      // AI 推介（后端预置推荐）副标题，如 "近5日连续上涨，MACD金叉形成，建议查看详情"
    val aiProfile: AiProfile, // AI 四维画像(分组依据)
    val high: Long,           // 今日最高(分)
    val low: Long,            // 今日最低(分)
    val open: Long,           // 今开(分)
    val marketCap: Long,      // 总市值(元)
    val floatCap: Long,       // 流通市值(元)
    val pe: Double,           // 市盈率
    val etfRatio: Double,     // 含X ETF占比 %
    val turnover: Double = 0.0,      // 换手率 %
    val volumeRatio: Double = 0.0,   // 量比
    val amplitude: Double = 0.0,     // 振幅 %
    val amount: Long = 0L,           // 成交额(元)
    val outer: Long = 0L,            // 外盘(手)
    val inner: Long = 0L,            // 内盘(手)
    val industry: String = "未分类",
    val benchmarkDelta: Double? = null, // 个股changePct − 指数changePct
    val tags: List<String> = emptyList(), // D2 动态标签
)

/** 主列表数据源状态:实时/缓存/离线。客户端盖章,页面据此打角标(实时/缓存/离线三态)。 */
enum class DataSource { LIVE, CACHE, OFFLINE }

/** 列表级 AI 摘要(D1/D3)。stale 由渲染端按 STALE_MS 判定后盖章。 */
data class AiSummary(
    val text: String,
    val generatedAt: Long,
    val stale: Boolean = false,
)

/** 弹层/报告页因子明细(D4)。industryRank -1 = 无法排名。 */
data class AiFactors(
    val momentum: Int = 0,   // 动量分 0-60
    val value: Int = 0,      // 价值分 0-25
    val risk: Int = 0,       // 风险分 0-8
    val industry: String = "未分类",
    val industryRank: Int = -1,
    val benchmarkDelta: Double? = null,
)

/**
 * 主列表一行数据 + 元信息(更新时间/来源/缺失数)。fetchWatchlist 的返回值。
 *
 * [missing] 是本次未能取到的条目数(0 = 全成功),由数据源给出——后端在整体响应里带 `missing`,
 * 直连腾讯时由实现自行统计。页面据此打「部分行情获取失败」角标,**不再向下转型**去读实现类字段。
 */
data class WatchlistBundle(
    val stocks: List<StockItem>,
    val fetchedAt: Long,
    val source: DataSource,
    val summary: AiSummary = AiSummary("", 0L),
    val missing: Int = 0,
)

data class AiAnalysis(
    val trendLabel: String,       // "短期看涨信号明显"
    val trendText: String,        // 正文
    val riskLevel: String,        // "中低风险"
    val riskText: String,         // "当前评级：中低风险"
    val score: Int,               // 推荐指数 85/100
    val targetPrice: Long,        // 分
    val stopLossPrice: Long,      // 分
    val factors: AiFactors = AiFactors(),
)

/** 预留：接真实行情/分析接口时只替换实现。fetchWatchlist 返回带元信息的 Bundle。 */
interface StockApi {
    suspend fun fetchWatchlist(): WatchlistBundle
    suspend fun fetchAiAnalysis(code: String): AiAnalysis
    suspend fun fetchStock(code: String): StockItem?
    suspend fun fetchGlobalAdvice(): String
}

/** 全盘股票 AI 建议（智窗建议态文案，组件会补「全盘股票AI建议：」前缀）。 */
const val GLOBAL_ADVICE: String = "这是一条全盘股票ai建议，面对全局的股票建议"

object SampleStockApi : StockApi {
    private val list = listOf(
        StockItem("1", "贵州茅台", "600519", 185600, 4262, 2.35, true, "近5日连续上涨，MACD金叉形成，建议查看详情",
            high = 187200, low = 184500, open = 185100, marketCap = 2_300_000_000_000, floatCap = 2_280_000_000_000, pe = 32.0, etfRatio = 18.8, industry = "白酒", turnover = 1.1, volumeRatio = 1.4, amplitude = 2.8, amount = 4_170_000_000L, outer = 108_325L, inner = 97_000L,
            // 1 贵州茅台
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_MACD, 95, AiLabels.SCENARIO_ADD)),
        StockItem("2", "腾讯控股", "00700", 39640, 475, 1.20, false, "游戏与广告回暖，估值处历史低位，可逢低关注",
            high = 39850, low = 39000, open = 39200, marketCap = 3_100_000_000_000, floatCap = 3_000_000_000_000, pe = 18.6, etfRatio = 8.4, industry = "互联网", turnover = 0.8, volumeRatio = 1.1, amplitude = 2.2, amount = 6_800_000_000L, outer = 176_000L, inner = 158_000L,
            // 2 腾讯控股
            aiProfile = AiProfile(AiLabels.ACTION_DIP, AiLabels.SIGNAL_BOTTOM, 78, AiLabels.SCENARIO_BUILD)),
        StockItem("3", "宁德时代", "300750", 23520, -188, -0.80, false, "锂电龙头回调企稳，紧盯海外产能落地节奏",
            high = 23900, low = 23300, open = 23750, marketCap = 1_000_000_000_000, floatCap = 900_000_000_000, pe = 22.4, etfRatio = 12.5, industry = "电池", turnover = 1.5, volumeRatio = 1.6, amplitude = 3.5, amount = 2_600_000_000L, outer = 68_000L, inner = 61_000L,
            // 3 宁德时代
            aiProfile = AiProfile(AiLabels.ACTION_HOLD, AiLabels.SIGNAL_OVERSOLD, 66, AiLabels.SCENARIO_KEEP)),
        StockItem("4", "比亚迪", "002594", 28600, 887, 3.10, true, "量能齐升，短线动能增强，建议观察",
            high = 28800, low = 27500, open = 27750, marketCap = 830_000_000_000, floatCap = 820_000_000_000, pe = 24.0, etfRatio = 10.2, industry = "汽车", turnover = 2.6, volumeRatio = 2.1, amplitude = 4.8, amount = 3_200_000_000L, outer = 84_000L, inner = 75_000L,
            // 4 比亚迪
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_VOLUME, 90, AiLabels.SCENARIO_BUILD)),
        StockItem("5", "中国平安", "601318", 4820, -24, -0.50, false, "寿险改革成效初显，股息率具备吸引力，适合长线",
            high = 4900, low = 4780, open = 4850, marketCap = 880_000_000_000, floatCap = 870_000_000_000, pe = 8.6, etfRatio = 3.4, industry = "保险", turnover = 0.6, volumeRatio = 0.9, amplitude = 1.6, amount = 1_500_000_000L, outer = 39_000L, inner = 35_000L,
            // 5 中国平安
            aiProfile = AiProfile(AiLabels.ACTION_HOLD, AiLabels.SIGNAL_BOTTOM, 64, AiLabels.SCENARIO_KEEP)),
        StockItem("6", "五粮液", "000858", 13860, 90, 0.65, false, "白酒板块情绪回暖，批价企稳，底部渐明",
            high = 14000, low = 13600, open = 13800, marketCap = 540_000_000_000, floatCap = 530_000_000_000, pe = 19.8, etfRatio = 7.9, industry = "白酒", turnover = 0.9, volumeRatio = 1.0, amplitude = 1.9, amount = 1_800_000_000L, outer = 47_000L, inner = 42_000L,
            // 6 五粮液
            aiProfile = AiProfile(AiLabels.ACTION_DIP, AiLabels.SIGNAL_BOTTOM, 75, AiLabels.SCENARIO_ADD)),
        StockItem("7", "中芯国际", "688981", 9870, 415, 4.20, true, "放量上攻，MACD翻红，注意回踩",
            high = 10000, low = 9400, open = 9480, marketCap = 780_000_000_000, floatCap = 500_000_000_000, pe = 48.0, etfRatio = 5.6, industry = "半导体", turnover = 2.8, volumeRatio = 2.3, amplitude = 5.2, amount = 1_200_000_000L, outer = 31_000L, inner = 28_000L,
            // 7 中芯国际
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_VOLUME, 88, AiLabels.SCENARIO_ADD)),
    )

    // 固定数据替身:作为「首载无缓存失败」的 OFFLINE 兜底;source 恒为 OFFLINE。
    override suspend fun fetchWatchlist(): WatchlistBundle {
        val enriched = list.map { item ->
            item.copy(benchmarkDelta = deriveBenchmarkDelta(item, MockBenchmark.changePctByMarket[MockBenchmark.of(item.code)]))
                .let { it.copy(tags = deriveTags(it)) }
        }
        val now = nowMillis()
        return WatchlistBundle(enriched, now, DataSource.OFFLINE, summary = deriveSummary(enriched, now))
    }

    override suspend fun fetchStock(code: String): StockItem? = list.find { it.code == code }

    override suspend fun fetchGlobalAdvice(): String = GLOBAL_ADVICE

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis {
        val items = fetchWatchlist().stocks          // 已含 industry/benchmarkDelta/tags 的富化结果
        val item = items.firstOrNull { it.code == code } ?: items.first()
        return deriveAiAnalysis(item)
    }
}
