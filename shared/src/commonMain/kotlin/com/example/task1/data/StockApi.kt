package com.example.task1.data

data class StockItem(
    val id: String,
    val name: String,
    val code: String,
    val price: Long,          // 分
    val change: Long,         // 涨跌额（分，带符号）：+4362 -> +43.62
    val changePct: Double,    // 涨跌幅 %
    val aiEnabled: Boolean,
    val aiBrief: String,      // AI 卡副标题，如 "近5日连续上涨，MACD金叉形成，建议查看详情"
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
}

object SampleStockApi : StockApi {
    private val list = listOf(
        StockItem("1", "贵州茅台", "600519", 185600, 4262, 2.35, true, "近5日连续上涨，MACD金叉形成，建议查看详情"),
        StockItem("2", "腾讯控股", "00700", 39640, 475, 1.20, false, ""),
        StockItem("3", "宁德时代", "300750", 23520, -188, -0.80, false, ""),
        StockItem("4", "比亚迪", "002594", 28600, 887, 3.10, true, "量能齐升，短线动能增强，建议观察"),
        StockItem("5", "中国平安", "601318", 4820, -24, -0.50, false, ""),
        StockItem("6", "五粮液", "000858", 13860, 90, 0.65, false, ""),
        StockItem("7", "中芯国际", "688981", 9870, 415, 4.20, true, "放量上攻，MACD翻红，注意回踩"),
    )

    override suspend fun fetchWatchlist(): List<StockItem> = list

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
