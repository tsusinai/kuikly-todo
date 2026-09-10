package com.example.task1.backend.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteParserTest {
    /** 构造一个 60 字段的 `~` 串,仅填指定下标;其余置 "0"。下标语义对照腾讯协议(≥HK 换手率 59 位)。 */
    private fun body(vararg pairs: Pair<Int, String>): String {
        val f = Array(60) { "0" }
        f[0] = "1"   // 通常为股票规格,不读
        f[1] = "贵州茅台"
        f[2] = "600519"
        f[3] = "1856.00"
        f[5] = "1830.00"
        f[31] = "12.30"
        f[32] = "0.67"
        f[33] = "1900.00"
        f[34] = "1820.00"
        f[39] = "30.1"
        f[44] = "10000"       // 流通市值 亿
        f[45] = "23400"       // 总市值 亿
        for ((i, v) in pairs) f[i] = v
        return f.joinToString("~")
    }

    @Test
    fun `a股 parse field indices and units`() {
        val r = QuoteParser.parse(body(), "sh")!!
        assertEquals("600519", r.code)
        assertEquals("贵州茅台", r.name)
        assertEquals(185600L, r.price)      // 1856.00 元 -> 分
        assertEquals(1230L, r.change)       // 12.30 元 -> 分
        assertEquals(0.67, r.changePct)
        assertEquals(190000L, r.high)
        assertEquals(182000L, r.low)
        assertEquals(183000L, r.open)
        assertEquals(2_340_000_000_000L, r.marketCap)  // 23400 亿 -> 元
        assertEquals(1_000_000_000_000L, r.floatCap)   // 10000 亿 -> 元
        assertEquals(30.1, r.pe)
        assertEquals("sh", r.market)
    }

    @Test
    fun `hk pe also at index 39 like a-share`() {
        // 2026-09-09 真实样本核实:HK 的 PE 与 A 股同在 39 位,40 恒为空
        val r = QuoteParser.parse(body(39 to "18.6", 40 to "999.0"), "hk")!!
        assertEquals("hk", r.market)
        assertEquals(18.6, r.pe)
    }

    @Test
    fun `real captured sh600000 payload parses to known values`() {
        // 2026-09-09 qt.gtimg.cn/q=sh600000 真实响应(完整保留,回归防位号漂移)
        val real = "1~浦发银行~600000~9.23~9.28~9.25~505325~208325~297000~9.23~3065~9.22~5257~9.21~7823~9.20~11558~9.19~3762~9.24~143~9.25~2862~9.26~5292~9.27~3711~9.28~5240~~20260909161459~-0.05~-0.54~9.29~9.21~9.23/505325/467549947~505325~46755~0.15~6.00~~9.29~9.21~0.86~3074.13~3074.13~0.41~10.21~8.35~0.70~14217~9.25~4.97~6.15~~~0.01~46754.9947~5.0765~55~   A~GP-A~-23.21~-0.54~4.55~6.14~0.50~13.83~8.07~0.22~0.65~2.10~33305838300~33305838300~29.19~-17.52~33305838300~~~-32.13~0.11~~CNY~0~___D__F__N~9.18~4846~"
        val r = QuoteParser.parse(real, "sh")!!
        assertEquals("600000", r.code)
        assertEquals("浦发银行", r.name)
        assertEquals(923L, r.price)          // 9.23 元 -> 分
        assertEquals(-5L, r.change)          // -0.05 元 -> 分
        assertEquals(-0.54, r.changePct)
        assertEquals(929L, r.high)
        assertEquals(921L, r.low)
        assertEquals(925L, r.open)
        assertEquals(6.0, r.pe)
        assertEquals(307_413_000_000L, r.floatCap)   // 3074.13 亿 -> 元
        assertEquals(307_413_000_000L, r.marketCap)  // 3074.13 亿 -> 元
        assertEquals(0.15, r.turnover)
        assertEquals(0.86, r.amplitude)
        assertEquals(467_550_000L, r.amount)         // 46755 万 -> 元 (×1e4)
        assertEquals(0.70, r.volumeRatio)
        assertEquals(208_325L, r.outer)
        assertEquals(297_000L, r.inner)
    }

    @Test
    fun `real captured hk00700 payload parses to known values`() {
        // 2026-09-09 qt.gtimg.cn/q=hk00700 真实响应(完整保留,回归防位号漂移)
        // 关键位:37=7667747289.690(成交额,小数,不 ×1e4)、49=411.000(52 周区间,非量比)、59=0.19(换手率)
        val real = "100~腾讯控股~00700~434.000~435.400~436.200~17643957.0~0~0~434.000~0~0~0~0~0~0~0~0~0~434.000~0~0~0~0~0~0~0~0~0~17643957.0~2026/09/09 16:08:12~-1.400~-0.32~438.400~432.800~434.000~17643957.0~7667747289.690~0~15.87~~0~0~1.29~39507.7206~39507.7206~TENCENT~1.22~677.700~411.000~1.05~24.00~0~0~0~0~0~14.57~3.03~0.19~100~-26.90~-0.96~GP~20.41~11.00~-2.56~-5.98~-5.57~9103161440.00~9103161440.00~15.04~5.309~434.582~-28.93~HKD~1~50"
        val r = QuoteParser.parse(real, "hk")!!
        assertEquals("hk", r.market)
        assertEquals("00700", r.code)
        assertEquals(43400L, r.price)          // 434.000 港元 -> 分
        assertEquals(-140L, r.change)          // -1.400 港元 -> 分
        assertEquals(-0.32, r.changePct)
        assertEquals(43840L, r.high)
        assertEquals(43280L, r.low)
        assertEquals(15.87, r.pe)
        assertEquals(0.19, r.turnover)         // HK 取 59 位
        assertEquals(1.29, r.amplitude)
        assertEquals(7_667_747_290L, r.amount) // 7667747289.690 四舍五入;HK 不 ×1e4
        assertEquals(0.0, r.volumeRatio)       // HK 恒 0,不得取 49 位
        assertEquals(0L, r.outer)
        assertEquals(0L, r.inner)
        assertEquals(3_950_772_060_000L, r.floatCap)   // 39507.7206 亿 -> 元
        assertEquals(3_950_772_060_000L, r.marketCap)
    }

    @Test
    fun `missing fields returns null`() {
        assertNull(QuoteParser.parse("", "sh"))                 // 空体
        assertNull(QuoteParser.parse("a~b~c", "sh"))            // 字段不足
        assertNull(QuoteParser.parse(null, "sh"))               // null 体
    }

    @Test
    fun `extractBody finds v_token quotes and returns null when absent`() {
        val raw = "v_sh600519=\"a~b\";v_hk00700=\"c~d\";"
        assertEquals("a~b", QuoteParser.extractBody(raw, "sh", "600519"))
        assertEquals("c~d", QuoteParser.extractBody(raw, "hk", "00700"))
        assertNull(QuoteParser.extractBody(raw, "sz", "300750"))   // 不存在
        assertNull(QuoteParser.extractBody(null, "sh", "600519"))
    }
}
