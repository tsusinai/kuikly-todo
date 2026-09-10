package com.example.task1.backend.ai

import com.example.task1.backend.model.RawStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactorRulesTest {
    private fun stock(
        changePct: Double = 0.0,
        pe: Double = 0.0,
        volumeRatio: Double = 0.0,
        amplitude: Double = 0.0,
        name: String = "贵州茅台",
    ): RawStock = RawStock(
        code = "600519", market = "sh", name = name,
        price = 185600, change = 0L, changePct = changePct,
        high = 190000, low = 182000, open = 183000,
        marketCap = 2_340_000_000_000L, floatCap = 1_000_000_000_000L, pe = pe,
        volumeRatio = volumeRatio, amplitude = amplitude,
    )

    @Test
    fun `deriveBenchmarkDelta mirrors client`() {
        assertEquals(1.73, deriveBenchmarkDelta(stock(changePct = 2.35), 0.62))
        assertEquals(null, deriveBenchmarkDelta(stock(changePct = 2.35), null))
    }

    @Test
    fun `tag 强于大盘 when benchmarkDelta above 0`() {
        assertEquals(listOf("强于大盘"), deriveTags(stock(), 0.5))
    }

    @Test
    fun `tag 量比异动 at volumeRatio threshold`() {
        assertEquals(listOf("量比异动"), deriveTags(stock(volumeRatio = 1.5), null))
    }

    @Test
    fun `tag 放量上攻 when volumeRatio threshold and changePct above 0`() {
        val tags = deriveTags(stock(changePct = 0.5, volumeRatio = 1.6), null)
        assertTrue("量比异动" in tags)
        assertTrue("放量上攻" in tags)
    }

    @Test
    fun `tag 振幅放大 at amplitude threshold`() {
        assertEquals(listOf("振幅放大"), deriveTags(stock(amplitude = 4.0), null))
    }

    @Test
    fun `tag 领涨 at changePct threshold`() {
        assertEquals(listOf("领涨"), deriveTags(stock(changePct = 3.0), null))
    }

    @Test
    fun `tag 低位企稳 when pe between 1 and 20 and changePct negative`() {
        assertEquals(listOf("低位企稳"), deriveTags(stock(changePct = -0.5, pe = 12.0), null))
    }

    @Test
    fun `zero values and null benchmarkDelta trigger no tags`() {
        assertEquals(emptyList(), deriveTags(stock(), null))
    }

    @Test
    fun `deriveSummary empty list returns refresh hint`() {
        val s = deriveSummary(emptyList(), 100L)
        assertEquals("暂无行情数据,下拉刷新重试", s.text)
        assertEquals(100L, s.generatedAt)
    }

    @Test
    fun `deriveSummary all down tone 偏弱`() {
        val s = deriveSummary(
            listOf(stock(changePct = -1.0, name = "五粮液"), stock(changePct = -2.0, name = "比亚迪")),
            100L,
        )
        assertTrue(s.text.contains("偏弱"))
        assertEquals("今日偏弱,控制仓位。", s.text.substringAfter("。"))
    }

    @Test
    fun `deriveSummary one up one down mirrors client text`() {
        val s = deriveSummary(
            listOf(stock(changePct = 2.0, name = "贵州茅台"), stock(changePct = -1.0, name = "五粮液")),
            100L,
        )
        assertEquals(
            "自选1涨1跌;最强「贵州茅台」2.0%,最弱「五粮液」-1.0%。今日整体偏强,关注领涨股。",
            s.text,
        )
    }

    @Test
    fun `industry map covers 7 samples and unknown fallback`() {
        assertEquals("白酒", IndustryMap.of("600519"))
        assertEquals("白酒", IndustryMap.of("000858"))
        assertEquals("互联网", IndustryMap.of("00700"))
        assertEquals("电池", IndustryMap.of("300750"))
        assertEquals("汽车", IndustryMap.of("002594"))
        assertEquals("保险", IndustryMap.of("601318"))
        assertEquals("半导体", IndustryMap.of("688981"))
        assertEquals("未分类", IndustryMap.of("999999"))
    }
}