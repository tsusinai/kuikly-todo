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
}
