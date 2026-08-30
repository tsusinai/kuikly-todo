package com.example.task1.backend.service

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

/** 直接构造一只有效 `~` 体,封装成腾讯整体原文(取体即成功)。 */
private fun rawFor(code: String): String {
    val f = Array(46) { "0" }
    f[1] = "贵州茅台"; f[2] = code; f[3] = "1856.00"; f[5] = "1830.00"
    f[32] = "12.30"; f[33] = "0.67"; f[34] = "1900.00"; f[35] = "1820.00"
    f[39] = "30.1"; f[44] = "10000"; f[45] = "23400"
    val body = f.joinToString("~")
    return "v_sh${code}=\"${body}\";"
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
}
