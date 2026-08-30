package com.example.task1.backend.dto

import kotlinx.serialization.Serializable

/** AI 四维画像(镜像客户端 `AiProfile`)。 */
@Serializable
data class AiProfileDto(
    val action: String,   // 重点关注/低吸关注/持股观望/建议回避
    val signal: String,   // MACD金叉/量能放大/低位企稳/超跌反弹
    val score: Int,       // 0-100
    val scenario: String, // 建议加自选/建议建仓/建议减仓/继续持有
)

/** 每一只归一化后的股票(镜像客户端 `StockItem`)。单位:价格/涨跌/高低/开=分;市值/流通市值=元。 */
@Serializable
data class StockItemDto(
    val code: String,
    val name: String,
    val price: Long,          // 分
    val change: Long,         // 涨跌额(分,带符号)
    val changePct: Double,    // 涨跌幅 %
    val high: Long,           // 今日最高(分)
    val low: Long,            // 今日最低(分)
    val open: Long,           // 今开(分)
    val marketCap: Long,      // 总市值(元)
    val floatCap: Long,       // 流通市值(元)
    val pe: Double,           // 市盈率
    val aiProfile: AiProfileDto,
    val aiEnabled: Boolean,
    val aiBrief: String,
)

/** `/watchlist` 整表响应。 */
@Serializable
data class WatchlistResponseDto(
    val stocks: List<StockItemDto>,
    val missing: Int,
)

/** `/analysis/:token` 弹层分析(镜像客户端 `AiAnalysis`)。 */
@Serializable
data class AiAnalysisDto(
    val code: String,
    val name: String,
    val trendLabel: String,
    val trendText: String,
    val riskLevel: String,
    val riskText: String,
    val score: Int,
    val targetPrice: Long,   // 分
    val stopLossPrice: Long, // 分
)

/** 统一错误响应。 */
@Serializable
data class ErrorDto(val error: String)

/** `/health` 探活响应。 */
@Serializable
data class HealthDto(val status: String)
