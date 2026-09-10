package com.example.task1.backend.service

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.ai.SummaryProvider
import com.example.task1.backend.ai.scoreParts
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 记录收到的 query,返回预置原始串的 fake。 */
private class FakeClient(private val raw: String? = "v_sh600519=\"a\";") : TencentClient {
    var lastQuery: String? = null
    override suspend fun fetch(query: String): String {
        lastQuery = query
        return raw ?: throw RuntimeException("boom")
    }
}

/** 记录收到的 items,返回固定 profile(score 95)的 fake。 */
private class FixedAiEngine : AiAnalysisProvider {
    var got: List<RawStock> = emptyList()
    override suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto> {
        got = items
        return items.map { AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选") }
    }
}
/** 记录收到的 context,返回固定文案的摘要 fake。 */
private class RecordingSummaryProvider : SummaryProvider {
    var gotContext: String? = null
    override suspend fun summarize(context: String, fallback: String): String {
        gotContext = context
        return "LLM文案"
    }
}

/** 直接构造一只有效 `~` 体,封装成腾讯整体原文(取体即成功)。 */
private fun rawFor(code: String): String = rawQuote("sh", code)

/** 同族构造器:可覆盖关键位号,未给的位号保持 "0"(即解析后的默认值)。 */
private fun rawQuote(
    market: String,
    code: String,
    changePct: String = "0.67",
    turnover: String = "0",
    volumeRatio: String = "0",
    amplitude: String = "0",
    amountWan: String = "0",
    outer: String = "0",
    inner: String = "0",
): String {
    val f = Array(50) { "0" }
    f[1] = "贵州茅台"; f[2] = code; f[3] = "1856.00"; f[5] = "1830.00"
    f[7] = outer; f[8] = inner
    f[31] = "12.30"; f[32] = changePct; f[33] = "1900.00"; f[34] = "1820.00"
    f[37] = amountWan; f[38] = turnover
    f[39] = "30.1"; f[43] = amplitude; f[44] = "10000"; f[45] = "23400"
    f[49] = volumeRatio
    val body = f.joinToString("~")
    return "v_${market}${code}=\"${body}\";"
}

class WatchlistServiceTest {
    private val profile = AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选")

    @Test
    fun `success builds one StockItemDto per code`() {
        val service = WatchlistService(FakeClient(rawFor("600519")), FixedAiEngine())
        val resp = runBlocking { service.fetchWatchlist("sh600519") }
        assertEquals(0, resp.missing)
        assertEquals(1, resp.stocks.size)
        val s = resp.stocks[0]
        assertEquals("600519", s.code)
        assertEquals("贵州茅台", s.name)
        assertEquals(185600L, s.price)
        assertEquals(2_340_000_000_000L, s.marketCap)
        assertEquals(30.1, s.pe)
        assertEquals("MACD金叉,重点关注(95分)", s.aiBrief)
        assertEquals(true, s.aiEnabled)   // score 95 >= 80
    }

    @Test
    fun `partial failure bumps missing and keeps successes`() {
        // 第二只 code 的取体失败(extractBody 找不到 v_sz300750=) -> 计入 missing
        val raw = rawFor("600519") + ";v_sz300750=\"...\""   // 300750 无有效 v_ 引号段 -> missing
        val service = WatchlistService(FakeClient(raw), FixedAiEngine())
        val resp = runBlocking { service.fetchWatchlist("sh600519,sz300750") }
        assertEquals(1, resp.missing)
        assertEquals(1, resp.stocks.size)
        assertEquals("600519", resp.stocks[0].code)
    }

    @Test
    fun `overall upstream failure throws UpstreamUnavailable`() {
        val service = WatchlistService(FakeClient(null), FixedAiEngine())
        assertFailsWith<UpstreamUnavailable> { runBlocking { service.fetchWatchlist("sh600519") } }
    }

    @Test
    fun `fetchAnalysis derives from parsed quote`() {
        val service = WatchlistService(FakeClient(rawFor("600519")), FixedAiEngine())
        val a = runBlocking { service.fetchAnalysis("sh600519") }!!
        assertEquals("600519", a.code)
        assertEquals("贵州茅台", a.name)
        assertEquals("短期震荡偏强", a.trendLabel)   // changePct 0.67 -> 非聚焦,>0
        assertEquals((185600 + 185600 * 0.08).toLong(), a.targetPrice)
    }

    @Test
    fun `fetchAnalysis of unknown code returns null`() {
        val service = WatchlistService(FakeClient(rawFor("600519")), FixedAiEngine())
        assertNull(runBlocking { service.fetchAnalysis("sz000001") })
    }

    @Test
    fun `fetchAnalysis of upstream failure throws UpstreamUnavailable`() {
        val service = WatchlistService(FakeClient(null), FixedAiEngine())
        assertFailsWith<UpstreamUnavailable> { runBlocking { service.fetchAnalysis("sh600519") } }
    }

    @Test
    fun `fetchWatchlist appends distinct index codes to query`() {
        val c1 = FakeClient(rawFor("600519"))
        runBlocking { WatchlistService(c1, FixedAiEngine()).fetchWatchlist("sh600519,sz300750,hk00700") }
        val parts1 = c1.lastQuery!!.split(",")
        assertTrue("sh000001" in parts1)
        assertTrue("sz399001" in parts1)
        assertTrue("hkHSI" in parts1)

        // 同市场多只:指数只追加一次
        val c2 = FakeClient(rawFor("600519"))
        runBlocking { WatchlistService(c2, FixedAiEngine()).fetchWatchlist("sh600519,sh600000") }
        assertEquals(1, c2.lastQuery!!.split(",").count { it == "sh000001" })
    }

    @Test
    fun `fetchWatchlist computes benchmarkDelta and tags from index body`() {
        val raw = rawQuote("sh", "600519", changePct = "0.67") + rawQuote("sh", "000001", changePct = "0.50")
        val service = WatchlistService(FakeClient(raw), FixedAiEngine())
        val resp = runBlocking { service.fetchWatchlist("sh600519") }
        val v = resp.stocks[0].benchmarkDelta
        assertEquals(0.17, v!!, 1e-9)
        assertTrue("强于大盘" in resp.stocks[0].tags)
    }

    @Test
    fun `fetchWatchlist when index body missing sets null delta and notes summary`() {
        val service = WatchlistService(FakeClient(rawFor("600519")), FixedAiEngine())
        val resp = runBlocking { service.fetchWatchlist("sh600519") }
        assertNull(resp.stocks[0].benchmarkDelta)
        assertNotNull(resp.summary)
        assertTrue(resp.summary!!.text.contains("基准数据缺失"))
    }

    @Test
    fun `fetchWatchlist populates new stock fields from RawStock`() {
        val raw = rawQuote(
            "sh", "600519", changePct = "0.67", turnover = "1.2", volumeRatio = "1.8",
            amplitude = "4.5", amountWan = "12345.6", outer = "111", inner = "222",
        )
        val service = WatchlistService(FakeClient(raw), FixedAiEngine())
        val s = runBlocking { service.fetchWatchlist("sh600519") }.stocks[0]
        assertEquals(1.2, s.turnover, 1e-9)
        assertEquals(1.8, s.volumeRatio, 1e-9)
        assertEquals(4.5, s.amplitude, 1e-9)
        assertEquals(123_456_000L, s.amount)
        assertEquals(111L, s.outer)
        assertEquals(222L, s.inner)
        assertEquals("白酒", s.industry)
        assertTrue("量比异动" in s.tags)
        assertTrue("振幅放大" in s.tags)
    }

    @Test
    fun `fetchAnalysis carries benchmarkDelta and factors`() {
        val raw = rawQuote("sh", "600519", changePct = "0.67") + rawQuote("sh", "000001", changePct = "0.50")
        val service = WatchlistService(FakeClient(raw), FixedAiEngine())
        val a = runBlocking { service.fetchAnalysis("sh600519") }!!
        assertNotNull(a.factors.benchmarkDelta)
        val parts = scoreParts(0.67, 30.1)
        assertEquals(parts.first, a.factors.momentum)
        assertEquals(parts.second, a.factors.value)
        assertEquals(parts.third, a.factors.risk)
        assertEquals("白酒", a.factors.industry)
    }
    @Test
    fun `summary text comes from injected provider`() {
        val fakeProvider = RecordingSummaryProvider()
        val service = WatchlistService(FakeClient(rawFor("600519")), FixedAiEngine(), fakeProvider)
        val resp = runBlocking { service.fetchWatchlist("sh600519") }
        assertEquals("LLM文案", resp.summary!!.text)
        assertTrue(fakeProvider.gotContext!!.isNotBlank())
    }
}
