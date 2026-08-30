package com.example.task1.backend.client

import com.example.task1.backend.config.Config
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TencentClientTest {
    private val cfg = Config(
        port = 8080,
        tencentQuoteUrl = "http://qt.gtimg.cn/q=",
        tencentTimeoutMs = 5000L,
        aiProvider = "rule",
    )

    @Test
    fun `fetch builds url quotebase plus query and passes timeout`() {
        var seenUrl: String? = null
        var seenTimeout: Long? = null
        val client = HttpTencentClient(cfg) { url, timeout ->
            seenUrl = url; seenTimeout = timeout; "raw-text"
        }
        val text = runBlocking { client.fetch("sh600519,hk00700") }
        assertEquals("http://qt.gtimg.cn/q=sh600519,hk00700", seenUrl)
        assertEquals(5000L, seenTimeout)
        assertEquals("raw-text", text)
    }

    @Test
    fun `fetch uses configured quote url`() {
        val cfg2 = cfg.copy(tencentQuoteUrl = "http://mirror/q=")
        val client = HttpTencentClient(cfg2) { url, _ -> url }
        assertEquals("http://mirror/q=sh600519", runBlocking { client.fetch("sh600519") })
    }
}
