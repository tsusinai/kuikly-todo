package com.example.task1.backend.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 夹具全部取自 2026-09-12 对腾讯接口的真实响应(已裁剪为前几根)。
 *
 * 实测结论(勿漂移):
 * - A 股传 `qfq` 时键名带前缀(`qfqday`/`qfqweek`/`qfqmonth`);**港股即使传 qfq 仍是 `day`** → 键名须逐个探测。
 * - 港股行会多一个分红/回购字典(第 7 个元素)→ 按位取值并忽略多余元素。
 * - 行序是 `[日期, 开, 收, 高, 低, 量]`,**不是** OHLC。
 * - 年 K 端点只回 1 根(不可用)→ 由月 K 聚合。
 * - 分时的量是**累计**值(画柱须差分);`prec` 是「首根之前」的收盘价,不能当昨收;昨收取 `qt[4]`。
 */
class ChartParserTest {

    private val dayJson = """{"code":0,"msg":"","data":{"sz300750":{"qfqday":[["2026-09-09","332.480","336.840","337.660","327.100","390240.000"],["2026-09-10","335.900","338.060","340.800","330.500","291184.000"],["2026-09-11","333.060","330.510","335.660","328.300","276600.000"]],"prec":"335.490","version":"18"}}}"""

    private val hkDayJson = """{"code":0,"msg":"","data":{"hk00700":{"day":[["2026-09-10","430.000","425.600","430.800","425.000","22333165.000",{"cqr":"2026-09-10","FHcontent":"","HGcontent":"回购23.50万股"}],["2026-09-11","419.400","428.400","430.800","419.400","15628379.000",{"cqr":"2026-09-11","FHcontent":"","HGcontent":""}]],"prec":"434.000","version":"16"}}}"""

    private val weekJson = """{"code":0,"msg":"","data":{"sz300750":{"qfqweek":[["2026-09-04","366.600","351.000","367.330","347.550","1228823.000"],["2026-09-11","351.050","330.510","351.800","326.000","1724363.000"]],"version":"18"}}}"""

    private val monthJson = """{"code":0,"msg":"","data":{"sz300750":{"qfqmonth":[["2026-08-31","391.198","363.550","408.598","357.300","4821907.000"],["2026-09-11","362.490","330.51","363.560","326.000","2690762.000"]],"version":"18"}}}"""

    private val minuteJson = """{"code":0,"msg":"","data":{"sz300750":{"data":{"data":["0930 333.06 5328 177454368.00","0931 335.22 16386 545610452.56","0932 333.00 20544 684168685.66"],"date":"20260911"},"qt":{"v_ff_sz300750":[],"sz300750":["51","宁德时代","300750","330.51","338.06","333.06","276600"]}}}}"""

    /** 实测 hk00700 2026-09-12 原文:量以「股」计、额以港元计,与 A 股口径差一个 100。 */
    private val hkMinuteJson = """{"code":0,"msg":"","data":{"hk00700":{"data":{"data":["0930 419.400 1340974 564579788.800","0931 422.200 1805825 760723409.200"],"date":"20260912"},"qt":{"v_ff_hk00700":[],"hk00700":["100","腾讯控股","00700","428.400","425.600","419.400","15628379.0","0"]}}}}"""

    @Test
    fun `day bars use date open close high low volume order and yuan to fen`() {
        val bars = requireNotNull(ChartParser.parseKline(dayJson, "sz300750", "day"))
        assertEquals(3, bars.size)
        val b = bars[0]
        assertEquals("09-09", b.label)
        assertEquals(33248L, b.open)     // 332.480 元 -> 33248 分
        assertEquals(33684L, b.close)
        assertEquals(33766L, b.high)
        assertEquals(32710L, b.low)
        assertEquals(390240L, b.volume)
        assertEquals("09-11", bars[2].label)
        assertEquals(33051L, bars[2].close)
    }

    @Test
    fun `hong kong bars fall back to unprefixed key and tolerate extra dividend column`() {
        val bars = requireNotNull(ChartParser.parseKline(hkDayJson, "hk00700", "day"))
        assertEquals(2, bars.size)
        assertEquals("09-10", bars[0].label)
        assertEquals(43000L, bars[0].open)
        assertEquals(42560L, bars[0].close)
        assertEquals(43080L, bars[0].high)
        assertEquals(42500L, bars[0].low)
        assertEquals(22333165L, bars[0].volume)
        assertEquals(42840L, bars[1].close)
    }

    @Test
    fun `week and month use their own prefixed keys and label formats`() {
        val week = requireNotNull(ChartParser.parseKline(weekJson, "sz300750", "week"))
        assertEquals(listOf("09-04", "09-11"), week.map { it.label })
        assertEquals(35100L, week[0].close)
        assertEquals(36733L, week[0].high)

        val month = requireNotNull(ChartParser.parseKline(monthJson, "sz300750", "month"))
        assertEquals(listOf("26-08", "26-09"), month.map { it.label })
        assertEquals(36355L, month[0].close)
    }

    @Test
    fun `year bars are aggregated from month bars`() {
        val year = requireNotNull(ChartParser.parseKline(monthJson, "sz300750", "year"))
        assertEquals(1, year.size)
        val y = year[0]
        assertEquals("2026", y.label)
        assertEquals(39120L, y.open)                       // 首月开
        assertEquals(33051L, y.close)                      // 末月收
        assertEquals(40860L, y.high)                       // 月高最大
        assertEquals(32600L, y.low)                        // 月低最小
        assertEquals(4821907L + 2690762L, y.volume)        // 月量求和
    }

    @Test
    fun `minute bars diff the cumulative volume and read prev close from qt field 4`() {
        val series = requireNotNull(ChartParser.parseMinute(minuteJson))
        assertEquals(33806L, series.prevClose)             // qt[4] = 338.06 元
        assertEquals(3, series.bars.size)

        val first = series.bars[0]
        assertEquals("09:30", first.label)
        assertEquals(33306L, first.close)
        assertEquals(33806L, first.open)                   // 首根以昨收为开
        assertEquals(33806L, first.high)
        assertEquals(33306L, first.low)
        assertEquals(5328L, first.volume)                  // 首根 = 累计值本身

        val second = series.bars[1]
        assertEquals("09:31", second.label)
        assertEquals(33522L, second.close)
        assertEquals(33306L, second.open)                  // 上一分钟收盘
        assertEquals(33522L, second.high)
        assertEquals(33306L, second.low)
        assertEquals(16386L - 5328L, second.volume)        // 累计差分,不是 16386

        assertEquals(3, series.avgPrice.size)
        assertEquals(33306L, series.avgPrice[0])           // 177454368.00 元 / 5328 手
        assertEquals(33297L, series.avgPrice[1])           // 545610452.56 元 / 16386 手
        assertEquals(33303L, series.avgPrice[2])           // 684168685.66 元 / 20544 手
    }

    @Test
    fun `hk minute volume counts shares so avg price must be scaled to fen`() {
        // 不做 ×100 的话 564579788.8 / 1340974 = 421 分(4.21 元),
        // Y 轴会被撑成 4~847,整幅分时压成一条线——正是线上看到的样子。
        val series = requireNotNull(ChartParser.parseMinute(hkMinuteJson, ChartParser.SHARES_PER_HK_UNIT))

        assertEquals(42560L, series.prevClose)
        assertEquals(41940L, series.bars[0].close)
        assertEquals(1340974L, series.bars[0].volume)
        assertEquals(1805825L - 1340974L, series.bars[1].volume)
        assertEquals(listOf(42102L, 42126L), series.avgPrice)
    }

    @Test
    fun `missing or empty payload yields null`() {
        assertNull(ChartParser.parseKline("""{"code":0,"msg":"","data":{}}""", "sz300750", "day"))
        assertNull(ChartParser.parseKline("""{"code":0,"msg":"","data":{"sz300750":{"qfqday":[]}}}""", "sz300750", "day"))
        assertNull(ChartParser.parseKline("not json at all", "sz300750", "day"))
        assertNull(ChartParser.parseMinute("""{"code":0,"msg":"","data":{}}"""))
        assertNull(ChartParser.parseMinute("not json at all"))
    }

    @Test
    fun `pre adjusted bars that fall below zero are dropped`() {
        // 实测 sz300750 2018 年前复权后 open=-542;若保留会把 Y 轴范围撑爆。
        val json = """{"code":0,"data":{"sz300750":{"qfqmonth":[["2018-01-31","-5.42","18.82","30.64","-5.42","1000.000"],["2026-08-31","391.198","363.550","408.598","357.300","4821907.000"],["2026-09-11","362.490","330.51","363.560","326.000","2690762.000"]]}}}"""
        val year = requireNotNull(ChartParser.parseKline(json, "sz300750", "year"))
        assertEquals(1, year.size)
        assertEquals("2026", year[0].label)
        assertEquals(39120L, year[0].open)
        assertEquals(33051L, year[0].close)
    }
}
