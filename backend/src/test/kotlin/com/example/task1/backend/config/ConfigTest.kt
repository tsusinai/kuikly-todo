package com.example.task1.backend.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun `defaults when env empty`() {
        val cfg = Config.load(mapOf())
        assertEquals(8080, cfg.port)
        assertEquals("http://qt.gtimg.cn/q=", cfg.tencentQuoteUrl)
        assertEquals(5000L, cfg.tencentTimeoutMs)
        assertEquals("rule", cfg.aiProvider)
    }

    @Test
    fun `env overrides defaults`() {
        val cfg = Config.load(
            mapOf(
                "PORT" to "9000",
                "TENCENT_QUOTE_URL" to "http://example.com/q=",
                "TENCENT_TIMEOUT_MS" to "3000",
                "AI_PROVIDER" to "llm",
            )
        )
        assertEquals(9000, cfg.port)
        assertEquals("http://example.com/q=", cfg.tencentQuoteUrl)
        assertEquals(3000L, cfg.tencentTimeoutMs)
        assertEquals("llm", cfg.aiProvider)
    }
}
