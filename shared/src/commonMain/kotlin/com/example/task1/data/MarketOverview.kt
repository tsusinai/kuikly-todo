package com.example.task1.data

import kotlin.math.roundToLong

/**
 * 行情首页顶部的大盘摘要。
 *
 * **全部由同一次列表拉取推导**——后端 /watchlist、直连腾讯、离线样例三条路径最终都会把
 * stocks 交到这里，所以顶部数值与列表天然同源：下拉刷新会一起更新，后端换了数据源也自动跟随，
 * 不需要新增后端字段。之前这里是写死的 "+23.45亿 / 涨23 / 跌12"。
 *
 * 口径（README 级约定，改口径先改这里）：
 *  - [netCapChangeYuan] = **Σ 流通市值变动**。用 `floatCap`(元) 与 `changePct` 反推：
 *    前收市值 = floatCap / (1 + changePct/100)，变动 = floatCap − 前收市值。
 *    注意它**不是「资金净流入」**——净流入要另一个数据源（腾讯资金流/北向），后端目前不提供。
 *  - 涨/跌/平家数：按 `changePct` 统计（>0 涨、<0 跌、=0 平）。
 *  - [asOfMs] = 客户端盖章的 `fetchedAt`；日期文案由它推，收盘状态由**当前时钟**推。
 */
data class MarketOverview(
    val netCapChangeYuan: Long,
    val riseCount: Int,
    val fallCount: Int,
    val flatCount: Int,
    val asOfMs: Long,
    val sessionOpen: Boolean,
) {
    val total: Int get() = riseCount + fallCount + flatCount

    /** 涨跌条里红条（涨）占比；无涨跌样本时为 0，渲染端据此画中性底。 */
    val riseFraction: Float
        get() {
            val denom = riseCount + fallCount
            return if (denom == 0) 0f else riseCount.toFloat() / denom
        }

    /** 收盘状态文案。列表混排 A 股与港股，任一场内开盘即算「盘中」。 */
    val statusText: String get() = if (sessionOpen) "盘中" else "已收盘"

    /** 数据时间文案（东八区）。 */
    val dateText: String get() = formatBeijingDate(asOfMs)
}

/**
 * 由列表推导顶部摘要。[nowMs] 只影响「盘中/已收盘」判定，默认取当前时钟（测试可注入）。
 */
fun deriveMarketOverview(
    stocks: List<StockItem>,
    asOfMs: Long,
    nowMs: Long = nowMillis(),
): MarketOverview {
    var net = 0.0
    var rise = 0
    var fall = 0
    var flat = 0
    for (s in stocks) {
        when {
            s.changePct > 0 -> rise++
            s.changePct < 0 -> fall++
            else -> flat++
        }
        // 前收市值只在涨跌幅不至于把市值打到 <=0 时才有意义（港股/异常值兜底）
        val ratio = 1.0 + s.changePct / 100.0
        if (s.floatCap > 0 && ratio > 0.1) {
            val prevCap = s.floatCap / ratio
            net += s.floatCap - prevCap
        }
    }
    return MarketOverview(
        netCapChangeYuan = net.roundToLong(),
        riseCount = rise,
        fallCount = fall,
        flatCount = flat,
        asOfMs = asOfMs,
        sessionOpen = isTradingNow(nowMs),
    )
}

private const val CN_OFFSET_MS = 8 * 3600_000L
private const val DAY_MS = 86_400_000L
private val WEEKDAY_CN = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

/**
 * A 股/港股交易时段判定（东八区，周末休市）。
 *
 * A 股 09:30-11:30 / 13:00-15:00，港股 09:30-12:00 / 13:00-16:00，列表里两者混排，
 * 取并集：任一场开市即算「盘中」。不含节假日日历（无数据源，刻意不做）。
 */
fun isTradingNow(nowMs: Long): Boolean {
    val day = floorDiv(nowMs + CN_OFFSET_MS, DAY_MS)
    val weekday = weekdayIndex(day)
    if (weekday == 0 || weekday == 6) return false
    val minute = (nowMs + CN_OFFSET_MS - day * DAY_MS) / 60_000L
    val aShare = minute in 570L..690L || minute in 780L..900L          // 09:30-11:30 / 13:00-15:00
    val hk = minute in 570L..720L || minute in 780L..960L              // 09:30-12:00 / 13:00-16:00
    return aShare || hk
}

/** epoch millis → "2026-09-13 星期日"（东八区）。无日期库，用 civil_from_days 手算。 */
fun formatBeijingDate(ms: Long): String {
    if (ms <= 0L) return ""
    val day = floorDiv(ms + CN_OFFSET_MS, DAY_MS)
    val (y, m, d) = civilFromDays(day)
    val weekday = weekdayIndex(day)
    return "$y-${pad2(m)}-${pad2(d)} ${WEEKDAY_CN[weekday]}"
}

/** 星期：0=周日 … 6=周六（1970-01-01 是星期四）。 */
private fun weekdayIndex(dayIndex: Long): Int = ((((dayIndex + 4) % 7) + 7) % 7).toInt()

private fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"

/** 向下取整除法（KMP commonMain 没有 Math.floorDiv）。 */
private fun floorDiv(a: Long, b: Long): Long = if (a >= 0) a / b else -((-a + b - 1) / b)

/** 公历天数（自 1970-01-01 起）→ (年, 月, 日)。Howard Hinnant 的 civil_from_days。 */
private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
}
