package com.example.task1.backend.parse

import com.example.task1.backend.model.ChartBar
import com.example.task1.backend.model.ChartSeries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * 腾讯 K 线 / 分时 JSON 解析。纯函数:`web.ifzq.gtimg.cn` 响应 → [ChartBar] / [ChartSeries]。
 *
 * 字段位号与键名均按 2026-09-12 真实响应核实,勿凭印象改写:
 * - K 线行序是 `[日期, 开, 收, 高, 低, 量]`,**不是** OHLC;港股行尾多一个分红/回购字典(按位取,忽略)。
 * - A 股传 `qfq` 时键名带前缀(`qfqday`/`qfqweek`/`qfqmonth`),**港股即使传 qfq 仍是裸 `day`** → 逐个探测。
 * - `prec` 是「首根之前」的收盘价,不是昨收,不可用于分时基准;分时昨收取 `qt[4]`。
 * - 分时的量是**累计**值,画柱须差分;成交额同为累计值,均价 = 累计额 / (累计量 × 每单位股数) × 100 分。
 * - 分时的量单位看市场:A 股是**手**(100 股),港股是**股**。这条不统一,港股均价会小两个数量级(实测 00700 算成 4.21 元)。
 */
object ChartParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val COL_DATE = 0
    private const val COL_OPEN = 1
    private const val COL_CLOSE = 2
    private const val COL_HIGH = 3
    private const val COL_LOW = 4
    private const val COL_VOLUME = 5
    private const val KLINE_MIN_COLUMNS = 6

    /** 分时行 `"HHMM 价 累计量(手) 累计额(元)"`。 */
    private const val MINUTE_MIN_FIELDS = 4

    /** 行情数组里的昨收位号(与客户端口径一致:现价 3 / 昨收 4 / 今开 5)。 */
    private const val QT_PREV_CLOSE = 4

    /** A 股分时的量以「手」计(1 手 = 100 股)。 */
    const val SHARES_PER_LOT = 100

    /** 港股分时的量以「股」计(港股每手股数不统一,不能用 100 去猜)。 */
    const val SHARES_PER_HK_UNIT = 1

    /** K 线周期 → 根数请求。[period] 取 "day"/"week"/"month"/"year"(year 由月 K 聚合)。 */
    fun parseKline(rawJson: String, token: String, period: String): List<ChartBar>? {
        val source = if (period == "year") "month" else period
        val rows = klineRows(rawJson, source) ?: return null
        val parsed = rows.mapNotNull { rawBar(it) }
        if (parsed.isEmpty()) return null
        val series = if (period == "year") aggregateYears(parsed) else parsed
        return series.map { bar ->
            ChartBar(label(bar.date, period), bar.open, bar.high, bar.low, bar.close, bar.volume)
        }
    }

    /**
     * 分时 JSON → 序列(含昨收与均价线);无数据/非 JSON 返回 null。
     *
     * [sharesPerVolume] 是上游一单位成交量对应的股数:A 股填 [SHARES_PER_LOT],港股填 [SHARES_PER_HK_UNIT]。
     * 均价线要按它把「累计额 / 累计量」折算成元/股,再 ×100 成分,否则画出来的均价线量级不对。
     */
    fun parseMinute(rawJson: String, sharesPerVolume: Int = SHARES_PER_LOT): ChartSeries? {
        val entry = entryOf(rawJson) ?: return null
        val lines = (entry["data"] as? JsonObject)?.get("data") as? JsonArray ?: return null
        if (lines.isEmpty()) return null

        val qt = (entry["qt"] as? JsonObject)?.values
            ?.filterIsInstance<JsonArray>()
            ?.firstOrNull { it.size > QT_PREV_CLOSE }
        val prevClose = qt?.number(QT_PREV_CLOSE)?.let(::fen) ?: 0L

        val bars = ArrayList<ChartBar>(lines.size)
        val avgPrice = ArrayList<Long>(lines.size)
        var lastCumulativeVolume = 0L
        var previousPrice = prevClose

        for (line in lines) {
            val parts = (line as? JsonPrimitive)?.content?.trim()?.split(" ")?.filter { it.isNotEmpty() }
            if (parts == null || parts.size < MINUTE_MIN_FIELDS) continue
            val price = parts[1].toDoubleOrNull()?.let(::fen) ?: continue
            val cumulativeVolume = parts[2].toDoubleOrNull()?.roundToLong() ?: 0L
            val cumulativeAmount = parts[3].toDoubleOrNull() ?: 0.0

            val open = if (bars.isEmpty()) prevClose.takeIf { it > 0L } ?: price else previousPrice
            bars += ChartBar(
                label = minuteLabel(parts[0]),
                open = open,
                high = max(open, price),
                low = min(open, price),
                close = price,
                volume = (cumulativeVolume - lastCumulativeVolume).coerceAtLeast(0L),
            )
            // 均价(分) = 累计额(元) / (累计量 × 每单位股数) × 100。
            // A 股:量以手计,系数 100 与 ×100 相抵,数值上就是「额 / 量」(实测 sz300750 33306);
            // 港股:量以股计,少那个 100,不显式 ×100 会得到 421 分(4.21 元),把 Y 轴拉到 4~847,整幅分时压成一条线。
            val shares = cumulativeVolume * sharesPerVolume
            avgPrice += if (shares > 0L) {
                (cumulativeAmount / shares * 100.0).roundToLong()
            } else {
                price
            }

            lastCumulativeVolume = cumulativeVolume
            previousPrice = price
        }
        if (bars.isEmpty()) return null
        return ChartSeries(prevClose = prevClose, bars = bars, avgPrice = avgPrice)
    }

    // —— 内部 ——

    private data class RawBar(
        val date: String,
        val open: Long,
        val high: Long,
        val low: Long,
        val close: Long,
        val volume: Long,
    )

    /** 取 `data.<token>` 这一层。 */
    private fun entryOf(rawJson: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: return null
        return data.values.firstOrNull() as? JsonObject
    }

    /** 按候选键取 K 线行:`qfq<source>` 优先,港股回落到裸 `<source>`。 */
    private fun klineRows(rawJson: String, source: String): List<JsonArray>? {
        val entry = entryOf(rawJson) ?: return null
        for (key in listOf("qfq$source", source)) {
            val rows = entry[key] as? JsonArray ?: continue
            val usable = rows.filterIsInstance<JsonArray>()
            if (usable.isNotEmpty()) return usable
        }
        return null
    }

    private fun rawBar(row: JsonArray): RawBar? {
        if (row.size < KLINE_MIN_COLUMNS) return null
        val date = row.string(COL_DATE) ?: return null
        val open = row.number(COL_OPEN) ?: return null
        val close = row.number(COL_CLOSE) ?: return null
        val high = row.number(COL_HIGH) ?: return null
        val low = row.number(COL_LOW) ?: return null
        // 前复权把早期价格压到 0 以下(实测 sz300750 2018 年 open=-542),该类根整根不可用,
        // 留着会让图表 Y 轴范围爆炸 → 丢弃。
        if (open <= 0.0 || close <= 0.0 || high <= 0.0 || low <= 0.0) return null
        return RawBar(
            date = date,
            open = fen(open),
            high = fen(high),
            low = fen(low),
            close = fen(close),
            volume = row.number(COL_VOLUME)?.roundToLong() ?: 0L,
        )
    }

    /** 月 K → 年 K:开=首月开,收=末月收,高/低=极值,量=求和。 */
    private fun aggregateYears(months: List<RawBar>): List<RawBar> = months
        .groupBy { it.date.substringBefore('-') }
        .map { (year, group) ->
            RawBar(
                date = "$year-01-01",
                open = group.first().open,
                high = group.maxOf { it.high },
                low = group.minOf { it.low },
                close = group.last().close,
                volume = group.sumOf { it.volume },
            )
        }

    private fun label(date: String, period: String): String = when {
        period == "year" -> date.take(4)
        period == "month" -> if (date.length >= 7) date.substring(2, 7) else date
        else -> if (date.length >= 10) date.substring(5) else date
    }

    private fun minuteLabel(hhmm: String): String =
        if (hhmm.length >= 4) "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}" else hhmm

    private fun JsonArray.number(index: Int): Double? =
        (getOrNull(index) as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonArray.string(index: Int): String? =
        (getOrNull(index) as? JsonPrimitive)?.content

    /** 价位 元 → 分。 */
    private fun fen(yuan: Double): Long = (yuan * 100.0).roundToLong()
}
