package com.example.task1.backend.model

/** 单根 K 线 / 分时点。价格单位:分;[volume] 单位:手。 */
data class ChartBar(
    val label: String,
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
)

/** 一个周期的完整序列。[avgPrice] 仅分时有值,与 [bars] 等长。 */
data class ChartSeries(
    val prevClose: Long,
    val bars: List<ChartBar>,
    val avgPrice: List<Long> = emptyList(),
)

/**
 * 图表周期。[wire] 是对外契约值,[tencent] 是腾讯 kline 的周期段,[count] 是请求根数。
 *
 * 年 K 的腾讯端点只回 1 根(2026-09-12 实测,不可用),因此 [YEAR] 取月 K 后本地聚合。
 */
enum class ChartPeriod(val wire: String, val tencent: String, val count: Int) {
    INTRADAY("intraday", "minute", 0),
    DAY("day", "day", 320),
    WEEK("week", "week", 260),
    MONTH("month", "month", 120),
    YEAR("year", "month", 130),
    ;

    companion object {
        fun of(wire: String): ChartPeriod? = entries.firstOrNull { it.wire == wire }
    }
}
