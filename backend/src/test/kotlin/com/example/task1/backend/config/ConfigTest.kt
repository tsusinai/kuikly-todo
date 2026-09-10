package com.example.task1.backend.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `invalid PORT throws`() {
        assertFailsWith<IllegalArgumentException> { Config.load(mapOf("PORT" to "abc")) }
    }

    @Test
    fun `invalid AI_PROVIDER throws`() {
        assertFailsWith<IllegalArgumentException> { Config.load(mapOf("AI_PROVIDER" to "wat")) }
    }
    @Test
    fun `llm fields default when env empty`() {
        val cfg = Config.load(mapOf())
        assertEquals("", cfg.llmBaseUrl)
        assertEquals("", cfg.llmApiKey)
        assertEquals("", cfg.llmModel)
        assertEquals(4500L, cfg.llmTimeoutMs)
    }
    @Test
    fun `llm env overrides defaults`() {
        val cfg = Config.load(
            mapOf(
                "LLM_BASE_URL" to "http://localhost:11434/v1",
                "LLM_API_KEY" to "sk-x",
                "LLM_MODEL" to "qwen2.5",
                "LLM_TIMEOUT_MS" to "9000",
            )
        )
        assertEquals("http://localhost:11434/v1", cfg.llmBaseUrl)
        assertEquals("sk-x", cfg.llmApiKey)
        assertEquals("qwen2.5", cfg.llmModel)
        assertEquals(9000L, cfg.llmTimeoutMs)
    }
    @Test
    fun `invalid LLM_TIMEOUT_MS throws`() {
        assertFailsWith<IllegalArgumentException> { Config.load(mapOf("LLM_TIMEOUT_MS" to "abc")) }
    }
}
