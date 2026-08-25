package com.example.task1.data

data class StockItem(
    val id: String,
    val name: String,
    val code: String,
    val price: Long,          // 分
    val change: Long,         // 涨跌额（分，带符号）：+4362 -> +43.62
    val changePct: Double,    // 涨跌幅 %
    val aiEnabled: Boolean,
    val aiBrief: String,      // AI 推介（后端预置推荐）副标题，如 "近5日连续上涨，MACD金叉形成，建议查看详情"
    val high: Long,           // 今日最高(分)
    val low: Long,            // 今日最低(分)
    val open: Long,           // 今开(分)
    val marketCap: Long,      // 总市值(元)
    val floatCap: Long,       // 流通市值(元)
    val pe: Double,           // 市盈率
    val etfRatio: Double,     // 含X ETF占比 %
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

/** 预留：接真实行情/分析接口时只替换实现。 */
interface StockApi {
    suspend fun fetchWatchlist(): List<StockItem>
    suspend fun fetchAiAnalysis(code: String): AiAnalysis
    suspend fun fetchStock(code: String): StockItem?
    suspend fun fetchGlobalAdvice(): String
}

/** 全盘股票 AI 建议（智窗建议态文案，组件会补「全盘股票AI建议：」前缀）。 */
const val GLOBAL_ADVICE: String = "这是一条全盘股票ai建议，面对全局的股票建议"

object SampleStockApi : StockApi {
    private val list = listOf(
        StockItem("1", "贵州茅台", "600519", 185600, 4262, 2.35, true, "近5日连续上涨，MACD金叉形成，建议查看详情",
            187200, 184500, 185100, 2_300_000_000_000, 2_280_000_000_000, 32.0, 18.8),
        StockItem("2", "腾讯控股", "00700", 39640, 475, 1.20, false, "游戏与广告回暖，估值处历史低位，可逢低关注",
            39850, 39000, 39200, 3_100_000_000_000, 3_000_000_000_000, 18.6, 8.4),
        StockItem("3", "宁德时代", "300750", 23520, -188, -0.80, false, "锂电龙头回调企稳，紧盯海外产能落地节奏",
            23900, 23300, 23750, 1_000_000_000_000, 900_000_000_000, 22.4, 12.5),
        StockItem("4", "比亚迪", "002594", 28600, 887, 3.10, true, "量能齐升，短线动能增强，建议观察",
            28800, 27500, 27750, 830_000_000_000, 820_000_000_000, 24.0, 10.2),
        StockItem("5", "中国平安", "601318", 4820, -24, -0.50, false, "寿险改革成效初显，股息率具备吸引力，适合长线",
            4900, 4780, 4850, 880_000_000_000, 870_000_000_000, 8.6, 3.4),
        StockItem("6", "五粮液", "000858", 13860, 90, 0.65, false, "白酒板块情绪回暖，批价企稳，底部渐明",
            14000, 13600, 13800, 540_000_000_000, 530_000_000_000, 19.8, 7.9),
        StockItem("7", "中芯国际", "688981", 9870, 415, 4.20, true, "放量上攻，MACD翻红，注意回踩",
            10000, 9400, 9480, 780_000_000_000, 500_000_000_000, 48.0, 5.6),
    )

    override suspend fun fetchWatchlist(): List<StockItem> = list

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
