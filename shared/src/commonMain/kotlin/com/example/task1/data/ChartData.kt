package com.example.task1.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 图表周期维度：分时 / 日K / 周K / 月K / 年K。
 *
 * [slots] 是该周期的采样点数，[volatility] 是相对波动倍率（周期越长，单根振幅越大）。
 * 新增周期只需在这里加一项，选择条与图表会自动跟随。
 */
enum class ChartPeriod(
    val label: String,
    val slots: Int,
    val volatility: Double,
    /** 后端契约值(`/chart/{token}?period=`),与 [label] 解耦,改文案不影响接口。 */
    val wire: String,
) {
    INTRADAY("分时", 240, 1.0, "intraday"),
    DAY("日K", 60, 1.0, "day"),
    WEEK("周K", 52, 2.2, "week"),
    MONTH("月K", 36, 4.0, "month"),
    YEAR("年K", 10, 8.0, "year"),
    ;

    companion object {
        fun of(wire: String): ChartPeriod? = entries.firstOrNull { it.wire == wire }
    }
}

/** 主图样式维度：蜡烛 / 线图 / 面积。 */
enum class ChartStyle(val label: String) {
    CANDLE("蜡烛"),
    LINE("线图"),
    AREA("面积"),
}

/** 叠加指标维度：成交量子图 / 均线叠加。 */
enum class ChartMetric(val label: String) {
    VOLUME("成交量"),
    MA("均线"),
}

/**
 * 单根 K 线。价格单位：分；[volume] 单位：手。
 *
 * [label] 是该点的时间标签（分时 "10:32"、日K "09-12"、月K "26-09"），由数据源格式化，
 * 图表不解析时间语义，因此时间轴刻度与选点信息条可以共用同一字段。
 */
data class Candle(
    val label: String,
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    val volume: Long,
) {
    val isUp: Boolean get() = close >= open
}

/**
 * 一个周期下的完整图表数据。
 *
 * [avgPrice] 分时均价线（与 [candles] 等长）；[ma] 均线族，key 为周期 5/10/20，
 * 序列与 [candles] 等长，前 window-1 项为 null（组件跳过不画，避免均线从 0 起跳）。
 */
data class StockChartData(
    val period: ChartPeriod,
    val candles: List<Candle>,
    val prevClose: Long,
    val avgPrice: List<Long> = emptyList(),
    val ma: Map<Int, List<Long?>> = emptyMap(),
) {
    val isEmpty: Boolean get() = candles.isEmpty()
    val last: Candle? get() = candles.lastOrNull()
}

/**
 * 图表数据源。接后端代理时替换实现即可（如 GET /chart/{code}?period=day），
 * 页面与组件只依赖 [StockChartData]，不关心数据来源。
 *
 * [quote] 是「这只票当前的实时行情」，只给**本地兜底**用：兜底走势是围着基准价现造的，
 * 若改用样例固定基价，就会造出与页面顶部行情对不上的图（图上 1856.00、页头却是 1275.16）。
 * 有后端时它被忽略（后端的走势本来就取自实时行情）。
 */
interface ChartApi {
    suspend fun fetchChart(code: String, period: ChartPeriod, quote: StockItem? = null): StockChartData
}

/**
 * 本地固定数据实现：按 code + 周期做确定性伪随机，同一只股票每次进入看到同一条走势。
 *
 * 分时以「昨收 + 布朗桥」生成并回填当日真实开/高/低/收；K 线由当日价格向前反推，
 * 最后一根强制使用 [StockItem] 的真实 OHLC，保证图表与上方行情区数值一致。
 */
object SampleChartApi : ChartApi {

    private val MA_WINDOWS = listOf(5, 10, 20)

    override suspend fun fetchChart(code: String, period: ChartPeriod, quote: StockItem?): StockChartData {
        // 优先用调用方给出的实时行情做基准:它决定了「昨收/开/高/低/最新」四个锚点,
        // 也决定了图上最后一根与页面顶部的行情是否说得上是同一只票
        val item = quote
            ?: SampleStockApi.fetchStock(code)
            ?: return StockChartData(period, emptyList(), 0L)
        val data = if (period == ChartPeriod.INTRADAY) intraday(item) else kline(item, period)
        // 分时也带均线:分时图上的均线是分钟收盘价的 5/10/20 均,和 K 线走同一套算法
        return data.copy(ma = movingAverages(data.candles))
    }

    /** 分时：240 个点（09:30-11:30 / 13:00-15:00），以昨收为基准做布朗桥，端点回填真实值。 */
    private fun intraday(item: StockItem): StockChartData {
        val n = ChartPeriod.INTRADAY.slots
        val prevClose = item.price - item.change
        val rnd = Random(item.code.hashCode() * 31 + 17)
        val amp = (item.price * 0.0012).roundToLong().coerceAtLeast(1L)

        val walk = LongArray(n + 1)
        var acc = 0L
        for (i in 1..n) {
            acc += rnd.nextLong(-amp, amp + 1)
            walk[i] = acc
        }
        val drift = item.price - prevClose
        val series = LongArray(n) { i ->
            val t = i.toDouble() / (n - 1)
            prevClose + (drift * t).roundToLong() + walk[i] - (walk[n] * t).roundToLong()
        }
        series[0] = item.open
        series[n - 1] = item.price
        // 当日最高/最低必须出现在走势里，否则与上方「最高/最低」对不上
        series[series.indices.maxByOrNull { series[it] } ?: 0] = item.high
        series[series.indices.minByOrNull { series[it] } ?: 0] = item.low
        for (i in 0 until n) series[i] = series[i].coerceIn(item.low, item.high)

        val avg = LongArray(n)
        var sum = 0L
        val baseVolume = (item.amount / 1_000L).coerceAtLeast(1_000L)
        val candles = ArrayList<Candle>(n)
        for (i in 0 until n) {
            sum += series[i]
            avg[i] = sum / (i + 1)
            val prev = if (i == 0) item.open else series[i - 1]
            val jitter = (series[i] * 0.0004).roundToLong().coerceAtLeast(0L)
            candles += Candle(
                label = intradayLabel(i),
                open = prev,
                high = max(prev, series[i]) + jitter,
                low = min(prev, series[i]) - jitter,
                close = series[i],
                volume = (baseVolume * (0.3 + rnd.nextDouble() * 1.4)).toLong().coerceAtLeast(1L),
            )
        }
        return StockChartData(
            period = ChartPeriod.INTRADAY,
            candles = candles,
            prevClose = prevClose,
            avgPrice = avg.toList(),
        )
    }

    /** K 线：由当日价格向前反推随机游走，最后一根使用真实 OHLC。 */
    private fun kline(item: StockItem, period: ChartPeriod): StockChartData {
        val n = period.slots
        val rnd = Random(item.code.hashCode() * 131 + period.ordinal * 7)
        val step = 0.018 * period.volatility

        val closes = DoubleArray(n)
        closes[n - 1] = item.price.toDouble()
        var v = item.price.toDouble()
        for (i in n - 2 downTo 0) {
            val pct = (rnd.nextDouble() * 2 - 1) * step
            v /= (1.0 + pct)
            closes[i] = v
        }

        val times = timeAxis(period, n)
        val baseVolume = (item.amount / 500L).coerceAtLeast(10_000L)
        val candles = ArrayList<Candle>(n)
        for (i in 0 until n) {
            val close = closes[i].roundToLong().coerceAtLeast(1L)
            val open = if (i == 0) (close * (1 - rnd.nextDouble() * 0.01)).roundToLong().coerceAtLeast(1L)
            else closes[i - 1].roundToLong().coerceAtLeast(1L)
            val volume = (baseVolume * (0.4 + rnd.nextDouble() * 1.2)).toLong().coerceAtLeast(1L)
            val label = klineLabel(times[i], period)
            candles += if (i == n - 1) {
                Candle(label, item.open.coerceAtLeast(1L), item.high, item.low, item.price, volume)
            } else {
                val pad = (max(open, close) * (0.002 + rnd.nextDouble() * step)).roundToLong().coerceAtLeast(1L)
                Candle(label, open, max(open, close) + pad, min(open, close) - pad, close, volume)
            }
        }
        return StockChartData(
            period = period,
            candles = candles,
            prevClose = candles.first().open,
        )
    }

    private fun timeAxis(period: ChartPeriod, n: Int): LongArray {
        val gapDays = when (period) {
            ChartPeriod.WEEK -> 7L
            ChartPeriod.MONTH -> 30L
            ChartPeriod.YEAR -> 365L
            else -> 1L
        }
        val today = nowMillis()
        return LongArray(n) { i -> today - (n - 1 - i) * gapDays * DAY_MILLIS }
    }

    /** 分时第 i 分钟（0..239）对应的时刻标签：前 120 分钟 09:30 起，后 120 分钟 13:00 起。 */
    private fun intradayLabel(i: Int): String {
        val minuteOfDay = if (i < 120) 9 * 60 + 30 + i else 13 * 60 + (i - 120)
        return "${pad2(minuteOfDay / 60)}:${pad2(minuteOfDay % 60)}"
    }

    private fun klineLabel(ms: Long, period: ChartPeriod): String {
        val (year, month, day) = civilFromDays(epochDay(ms))
        return when (period) {
            ChartPeriod.MONTH -> "${pad2(year % 100)}-${pad2(month)}"
            ChartPeriod.YEAR -> "$year"
            else -> "${pad2(month)}-${pad2(day)}"
        }
    }
}

internal const val DAY_MILLIS: Long = 86_400_000L

/** 均线窗口:5 / 10 / 20。 */
private val MA_WINDOWS = listOf(5, 10, 20)

/**
 * 均线族,key 为窗口,序列与 [candles] 等长,前 window-1 项为 null(组件跳过不画,避免均线从 0 起跳)。
 *
 * 按收盘价滚动平均:分时上是分钟均线,日K 上是日均线。抽成顶层函数供样例源与 [BackendChartApi] 共用,
 * 保证两条数据源、五个周期的均线口径一致。
 */
internal fun movingAverages(candles: List<Candle>): Map<Int, List<Long?>> =
    MA_WINDOWS.associateWith { window ->
        List(candles.size) { i ->
            if (i < window - 1) null
            else (i - window + 1..i).sumOf { candles[it].close } / window
        }
    }

/** 东八区 epoch day（图表数据固定按北京时间展示）。 */
private fun epochDay(ms: Long): Long = (ms + 8 * 3_600_000L).floorDiv(DAY_MILLIS)

/** Howard Hinnant civil_from_days：epoch day -> (年, 月, 日)，无平台时区依赖。 */
private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
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

private fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"
