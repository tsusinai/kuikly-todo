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

/** 列表级 AI 摘要(镜像客户端 AiSummary)。stale 由渲染端按 STALE_MS 判定。 */
@Serializable
data class AiSummaryDto(
    val text: String,
    val generatedAt: Long,
    val stale: Boolean = false,
)

/** 弹层/报告页因子明细(镜像客户端 AiFactors)。industryRank -1 = 无法排名。 */
@Serializable
data class AiFactorsDto(
    val momentum: Int,   // 动量分 0-60
    val value: Int,      // 价值分 0-25
    val risk: Int,       // 风险分 0-8
    val industry: String,
    val industryRank: Int = -1,
    val benchmarkDelta: Double? = null,
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
    val turnover: Double = 0.0,        // 换手率 %
    val volumeRatio: Double = 0.0,     // 量比
    val amplitude: Double = 0.0,       // 振幅 %
    val amount: Long = 0L,             // 成交额(元)
    val outer: Long = 0L,              // 外盘(手)
    val inner: Long = 0L,              // 内盘(手)
    val industry: String = "未分类",
    val benchmarkDelta: Double? = null, // 个股changePct − 指数changePct
    val tags: List<String> = emptyList(),
)

/** `/watchlist` 整表响应。 */
@Serializable
data class WatchlistResponseDto(
    val stocks: List<StockItemDto>,
    val missing: Int,
    val summary: AiSummaryDto? = null,
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
    val factors: AiFactorsDto = AiFactorsDto(0, 0, 0, "未分类"),
)

/** 统一错误响应。 */
@Serializable
data class ErrorDto(val error: String)

/** 一根 K 线 / 分时点(镜像客户端 `Candle`)。价格单位:分;[volume] 单位:手。 */
@Serializable
data class ChartBarDto(
    val label: String,   // 分时 "10:32";日/周K "09-12";月K "26-09";年K "2026"
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
)

/** `/chart/{token}` 响应(镜像客户端 `StockChartData`)。[avgPrice] 仅分时有值。 */
@Serializable
data class ChartResponseDto(
    val code: String,
    val period: String,          // intraday | day | week | month | year
    val prevClose: Long,         // 分
    val bars: List<ChartBarDto>,
    val avgPrice: List<Long> = emptyList(),
)

/** `/health` 探活响应。 */
@Serializable
data class HealthDto(val status: String)
