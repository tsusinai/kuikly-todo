package com.example.task1.backend.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteParserTest {
    /** 构造一个 46 字段的 `~` 串,仅填指定下标;其余置 "0"。下标语义对照腾讯协议。 */
    private fun body(vararg pairs: Pair<Int, String>): String {
        val f = Array(46) { "0" }
        f[0] = "1"   // 通常为股票规格,不读
        f[1] = "贵州茅台"
        f[2] = "600519"
        f[3] = "1856.00"
        f[5] = "1830.00"
        f[32] = "12.30"
        f[33] = "0.67"
        f[34] = "1900.00"
        f[35] = "1820.00"
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
    fun `hk uses pe index 40 not 39`() {
        // A 股读 39=30.1;HK 读 40=18.6,39 应被忽略
        val r = QuoteParser.parse(body(39 to "999.0", 40 to "18.6"), "hk")!!
        assertEquals("hk", r.market)
        assertEquals(18.6, r.pe)
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
