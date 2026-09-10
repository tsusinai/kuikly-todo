package com.example.task1.backend.ai

import com.example.task1.backend.config.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SummaryProviderTest {
    private fun config(
        llmBaseUrl: String = "",
        llmApiKey: String = "",
        llmModel: String = "",
        llmTimeoutMs: Long = 4500L,
    ): Config = Config(
        port = 8080,
        tencentQuoteUrl = "http://qt.gtimg.cn/q=",
        tencentTimeoutMs = 5000L,
        aiProvider = "llm",
        llmBaseUrl = llmBaseUrl,
        llmApiKey = llmApiKey,
        llmModel = llmModel,
        llmTimeoutMs = llmTimeoutMs,
    )

    @Test
    fun `rule provider returns fallback`() {
        val out = runBlocking { RuleSummaryProvider.summarize("ctx", "兜底文案") }
        assertEquals("兜底文案", out)
    }

    @Test
    fun `llm without baseUrl falls back and does not call http`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = ""),
            httpPost = { _, _, _, _ -> throw AssertionError("should not be called") },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("兜底文案", out)
    }

    @Test
    fun `llm falls back when http throws`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> throw RuntimeException("boom") },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("兜底文案", out)
    }

    @Test
    fun `llm falls back when response has no text`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> """{"choices":[]}""" },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("兜底文案", out)
    }

    @Test
    fun `llm returns text from JSON response`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> """{"text":"白酒领涨,量能配合"}""" },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("白酒领涨,量能配合", out)
    }

    @Test
    fun `llm posts to chat completions with model`() {
        var capturedUrl = ""
        var capturedBody = ""
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1/", llmApiKey = "sk-x", llmModel = "m1"),
            httpPost = { url, _, body, _ ->
                capturedUrl = url
                capturedBody = body
                """{"text":"ok"}"""
            },
        )
        val out = runBlocking { provider.summarize("自选3涨1跌", "兜底文案") }
        assertEquals("http://x/v1/chat/completions", capturedUrl)
        assertEquals("ok", out)
        assertTrue(capturedBody.contains("\"model\":\"m1\""), "body should carry model: $capturedBody")
        assertTrue(capturedBody.contains("自选3涨1跌"), "body should carry context: $capturedBody")
    }
    @Test
    fun `llm reads choices message content`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> """{"choices":[{"message":{"content":"白酒领涨,量能配合"}}]}""" },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("白酒领涨,量能配合", out)
    }
    @Test
    fun `llm reads nested text json inside content`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> """{"choices":[{"message":{"content":"{\"text\":\"白酒领涨\"}"}}]}""" },
        )
        val out = runBlocking { provider.summarize("ctx", "兜底文案") }
        assertEquals("白酒领涨", out)
    }
    @Test
    fun `llm propagates cancellation`() {
        val provider = LlmSummaryProvider(
            config(llmBaseUrl = "http://x/v1", llmModel = "m1"),
            httpPost = { _, _, _, _ -> throw CancellationException("cancelled") },
        )
        assertFailsWith<CancellationException> {
            runBlocking { provider.summarize("ctx", "兜底文案") }
        }
    }
}
