package com.example.task1.backend.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DtosTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `aiProfileDto serializes with exactly the four fields`() {
        val text = json.encodeToString(AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选"))
        /* 契约要求字段名: action/signal/score/scenario */
        assertTrue(text.contains("\"action\":\"重点关注\""))
        assertTrue(text.contains("\"signal\":\"MACD金叉\""))
        assertTrue(text.contains("\"score\":95"))
        assertTrue(text.contains("\"scenario\":\"建议加自选\""))
    }

    @Test
    fun `stockItemDto round-trips`() {
        val dto = StockItemDto(
            code = "600519", name = "贵州茅台",
            price = 185600, change = 1230, changePct = 0.67,
            high = 190000, low = 182000, open = 183000,
            marketCap = 2_340_000_000_000L, floatCap = 1_000_000_000_000L,
            pe = 30.1, aiProfile = AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选"),
            aiEnabled = true, aiBrief = "MACD金叉,重点关注(95分)",
        )
        val decoded = json.decodeFromString<StockItemDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
        assertEquals("600519", decoded.code)
        assertEquals(30.1, decoded.pe)
    }

    @Test
    fun `watchlistResponse decodes with missing`() {
        val text = """{"stocks":[],"missing":0}"""
        val dto = json.decodeFromString<WatchlistResponseDto>(text)
        assertEquals(0, dto.missing)
        assertTrue(dto.stocks.isEmpty())
    }

    @Test
    fun `error and health dto shapes`() {
        val e = json.decodeFromString<ErrorDto>("""{"error":"invalid_codes"}""")
        assertEquals("invalid_codes", e.error)
        val h = json.decodeFromString<HealthDto>("""{"status":"ok"}""")
        assertEquals("ok", h.status)
    }

    @Test
    fun `stockItemDto round-trips with factor fields`() {
        val dto = StockItemDto(
            code = "600519", name = "贵州茅台",
            price = 185600, change = 1230, changePct = 0.67,
            high = 190000, low = 182000, open = 183000,
            marketCap = 2_340_000_000_000L, floatCap = 1_000_000_000_000L,
            pe = 30.1, aiProfile = AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选"),
            aiEnabled = true, aiBrief = "MACD金叉,重点关注(95分)",
            turnover = 1.35, volumeRatio = 2.1, amplitude = 3.2,
            amount = 1_234_560_000L, outer = 100_001L, inner = 99_999L,
            industry = "白酒", benchmarkDelta = 0.42,
            tags = listOf("量比异动", "低位企稳"),
        )
        val decoded = json.decodeFromString<StockItemDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
        assertEquals("量比异动", decoded.tags[0])
        assertEquals("白酒", decoded.industry)
    }

    @Test
    fun `stockItemDto old payload still decodes (backward compat)`() {
        val text = """{"code":"600519","name":"贵州茅台","price":185600,"change":1230,"changePct":0.67,"high":190000,"low":182000,"open":183000,"marketCap":2340000000000,"floatCap":1000000000000,"pe":30.1,"aiProfile":{"action":"重点关注","signal":"MACD金叉","score":95,"scenario":"建议加自选"},"aiEnabled":true,"aiBrief":"MACD金叉,重点关注(95分)"}"""
        val decoded = json.decodeFromString<StockItemDto>(text)
        assertEquals("未分类", decoded.industry)
        assertTrue(decoded.tags.isEmpty())
        assertEquals(0.0, decoded.turnover)
        assertEquals(null, decoded.benchmarkDelta)
    }

    @Test
    fun `watchlistResponseDto round-trips with summary`() {
        val dto = WatchlistResponseDto(
            stocks = emptyList(), missing = 0,
            summary = AiSummaryDto(text = "白酒领涨", generatedAt = 123456, stale = false),
        )
        val decoded = json.decodeFromString<WatchlistResponseDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
        assertEquals("白酒领涨", decoded.summary?.text)

        val noSummary = json.decodeFromString<WatchlistResponseDto>("""{"stocks":[],"missing":0}""")
        assertEquals(0, noSummary.missing)
        assertEquals(null, noSummary.summary)
    }

    @Test
    fun `aiAnalysisDto round-trips with factors`() {
        val dto = AiAnalysisDto(
            code = "688981", name = "中芯国际",
            trendLabel = "短期看涨信号明显", trendText = "近5日连续上涨,成交量放大,MACD金叉形成。",
            riskLevel = "中低风险", riskText = "当前评级:中低风险",
            score = 85, targetPrice = 192000, stopLossPrice = 180000,
            factors = AiFactorsDto(momentum = 38, value = 15, risk = 8, industry = "半导体", industryRank = 2, benchmarkDelta = -0.3),
        )
        val decoded = json.decodeFromString<AiAnalysisDto>(json.encodeToString(dto))
        assertEquals(dto, decoded)
        assertEquals(38, decoded.factors.momentum)
        assertEquals(-0.3, decoded.factors.benchmarkDelta)
    }
}
