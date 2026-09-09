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
)

/** 主列表数据源状态:实时/缓存/离线。客户端盖章,页面据此打角标(实时/缓存/离线三态)。 */
enum class DataSource { LIVE, CACHE, OFFLINE }

/** 主列表一行数据 + 元信息(更新时间/来源)。fetchWatchlist 的返回值;fetchedAt/source 客户端计算。 */
data class WatchlistBundle(
    val stocks: List<StockItem>,
    val fetchedAt: Long,
    val source: DataSource,
)

data class AiAnalysis(
    val trendLabel: String,       // "短期看涨信号明显"
    val trendText: String,        // 正文
    val riskLevel: String,        // "中低风险"
    val riskText: String,         // "当前评级：中低风险"
    val score: Int,               // 推荐指数 85/100
    val targetPrice: Long,        // 分
    val stopLossPrice: Long,      // 分
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
            high = 187200, low = 184500, open = 185100, marketCap = 2_300_000_000_000, floatCap = 2_280_000_000_000, pe = 32.0, etfRatio = 18.8,
            // 1 贵州茅台
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_MACD, 95, AiLabels.SCENARIO_ADD)),
        StockItem("2", "腾讯控股", "00700", 39640, 475, 1.20, false, "游戏与广告回暖，估值处历史低位，可逢低关注",
            high = 39850, low = 39000, open = 39200, marketCap = 3_100_000_000_000, floatCap = 3_000_000_000_000, pe = 18.6, etfRatio = 8.4,
            // 2 腾讯控股
            aiProfile = AiProfile(AiLabels.ACTION_DIP, AiLabels.SIGNAL_BOTTOM, 78, AiLabels.SCENARIO_BUILD)),
        StockItem("3", "宁德时代", "300750", 23520, -188, -0.80, false, "锂电龙头回调企稳，紧盯海外产能落地节奏",
            high = 23900, low = 23300, open = 23750, marketCap = 1_000_000_000_000, floatCap = 900_000_000_000, pe = 22.4, etfRatio = 12.5,
            // 3 宁德时代
            aiProfile = AiProfile(AiLabels.ACTION_HOLD, AiLabels.SIGNAL_OVERSOLD, 66, AiLabels.SCENARIO_KEEP)),
        StockItem("4", "比亚迪", "002594", 28600, 887, 3.10, true, "量能齐升，短线动能增强，建议观察",
            high = 28800, low = 27500, open = 27750, marketCap = 830_000_000_000, floatCap = 820_000_000_000, pe = 24.0, etfRatio = 10.2,
            // 4 比亚迪
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_VOLUME, 90, AiLabels.SCENARIO_BUILD)),
        StockItem("5", "中国平安", "601318", 4820, -24, -0.50, false, "寿险改革成效初显，股息率具备吸引力，适合长线",
            high = 4900, low = 4780, open = 4850, marketCap = 880_000_000_000, floatCap = 870_000_000_000, pe = 8.6, etfRatio = 3.4,
            // 5 中国平安
            aiProfile = AiProfile(AiLabels.ACTION_HOLD, AiLabels.SIGNAL_BOTTOM, 64, AiLabels.SCENARIO_KEEP)),
        StockItem("6", "五粮液", "000858", 13860, 90, 0.65, false, "白酒板块情绪回暖，批价企稳，底部渐明",
            high = 14000, low = 13600, open = 13800, marketCap = 540_000_000_000, floatCap = 530_000_000_000, pe = 19.8, etfRatio = 7.9,
            // 6 五粮液
            aiProfile = AiProfile(AiLabels.ACTION_DIP, AiLabels.SIGNAL_BOTTOM, 75, AiLabels.SCENARIO_ADD)),
        StockItem("7", "中芯国际", "688981", 9870, 415, 4.20, true, "放量上攻，MACD翻红，注意回踩",
            high = 10000, low = 9400, open = 9480, marketCap = 780_000_000_000, floatCap = 500_000_000_000, pe = 48.0, etfRatio = 5.6,
            // 7 中芯国际
            aiProfile = AiProfile(AiLabels.ACTION_FOCUS, AiLabels.SIGNAL_VOLUME, 88, AiLabels.SCENARIO_ADD)),
    )

    // 固定数据替身:作为「首载无缓存失败」的 OFFLINE 兜底;source 恒为 OFFLINE。
    override suspend fun fetchWatchlist(): WatchlistBundle = WatchlistBundle(list, nowMillis(), DataSource.OFFLINE)

    override suspend fun fetchStock(code: String): StockItem? = list.find { it.code == code }

    override suspend fun fetchGlobalAdvice(): String = GLOBAL_ADVICE

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis = AiAnalysis(
        trendLabel = "短期看涨信号明显",
        trendText = "近5日连续上涨，成交量放大，MACD金叉形成，短期看涨信号明显。",
        riskLevel = "中低风险",
        riskText = "当前评级：中低风险",
        score = 85,
        targetPrice = 192000,
        stopLossPrice = 180000,
    )
}
