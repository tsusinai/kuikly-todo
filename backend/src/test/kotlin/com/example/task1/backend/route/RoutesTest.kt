package com.example.task1.backend.route

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.config.Config
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock
import com.example.task1.backend.service.ChartService
import com.example.task1.backend.service.WatchlistService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StubClient : TencentClient {
    override suspend fun fetch(query: String): String {
        if (query.contains("300750")) throw RuntimeException("upstream")   // 整体失败
        val f = Array(46) { "0" }
        f[1] = "贵州茅台"; f[2] = "600519"; f[3] = "1856.00"; f[5] = "1830.00"
        f[31] = "12.30"; f[32] = "0.67"; f[33] = "1900.00"; f[34] = "1820.00"
        f[39] = "30.1"; f[44] = "10000"; f[45] = "23400"
        return "v_sh600519=\"${f.joinToString("~")}\";"
    }

    override suspend fun fetchUrl(url: String): String {
        if (url.contains("300750")) throw RuntimeException("upstream")
        return if (url.contains("/minute/")) MINUTE_BODY else DAY_BODY
    }
}

private class StubAi : AiAnalysisProvider {
    override suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto> =
        items.map { AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选") }
}

private const val DAY_BODY =
    """{"code":0,"data":{"sh600519":{"qfqday":[["2026-09-10","1830.00","1850.00","1860.00","1820.00","12000.000"],["2026-09-11","1851.00","1856.00","1872.00","1845.00","9800.000"]]}}}"""

/** 成交额与量自洽:1851.00 元 × 1200 手 × 100 股 = 222120000 元,故均价 = 185100 分。 */
private const val MINUTE_BODY =
    """{"code":0,"data":{"sh600519":{"data":{"data":["0930 1851.00 1200 222120000.00","0931 1855.00 3000 555600000.00"],"date":"20260911"},"qt":{"v_ff_sh600519":[],"sh600519":["1","贵州茅台","600519","1856.00","1830.00","1851.00"]}}}}"""

private val STUB_CONFIG = Config(
    port = 8080,
    tencentQuoteUrl = "http://qt.gtimg.cn/q=",
    tencentTimeoutMs = 5000L,
    aiProvider = "rule",
)

/** 与 Application.kt 的 modules() 一致:安装 plugins + 路由。 */
private fun io.ktor.server.application.Application.installRoutes(
    watchlist: WatchlistService,
    chart: ChartService,
) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    routing { apiRoutes(watchlist, chart) }
}

/** 起一个与 main 等价的测试应用(桩上游 + 规则引擎)。 */
private fun ApplicationTestBuilder.boot() {
    application {
        installRoutes(WatchlistService(StubClient(), StubAi()), ChartService(StubClient(), STUB_CONFIG))
    }
}

class RoutesTest {

    @Test
    fun `health returns ok`() = testApplication {
        boot()
        val r = client.get("/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"status":"ok"}""", r.bodyAsText())
    }

    @Test
    fun `missing codes returns 400 invalid_codes`() = testApplication {
        boot()
        val r = client.get("/watchlist")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("invalid_codes"))
    }

    @Test
    fun `upstream failure returns 502`() = testApplication {
        boot()
        val r = client.get("/watchlist?codes=sz300750")
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertTrue(r.bodyAsText().contains("upstream_unavailable"))
    }

    @Test
    fun `watchlist success returns 200 with stocks`() = testApplication {
        boot()
        val r = client.get("/watchlist?codes=sh600519")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"code\":\"600519\""))
        assertTrue(text.contains("\"missing\":0"))
    }

    @Test
    fun `analysis unknown returns 404`() = testApplication {
        boot()
        val r = client.get("/analysis/sz000001")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertTrue(r.bodyAsText().contains("not_found"))
    }

    @Test
    fun `analysis upstream failure returns 502`() = testApplication {
        boot()
        val r = client.get("/analysis/sz300750")
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertTrue(r.bodyAsText().contains("upstream_unavailable"))
    }

    @Test
    fun `watchlist response includes summary`() = testApplication {
        boot()
        val r = client.get("/watchlist?codes=sh600519")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"summary\""))
        assertTrue(text.contains("\"text\""))
    }

    @Test
    fun `watchlist without index body still 200 with summary`() = testApplication {
        boot()
        val r = client.get("/watchlist?codes=sh600519")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"summary\""))
        assertTrue(text.contains("\"benchmarkDelta\":null"))
        assertTrue(text.contains("大盘基准数据缺失"))
        val a = client.get("/analysis/sh600519")
        assertEquals(HttpStatusCode.OK, a.status)
        assertTrue(a.bodyAsText().contains("\"factors\""))
    }

    // —— /chart ——

    @Test
    fun `chart without period defaults to day`() = testApplication {
        boot()
        val r = client.get("/chart/sh600519")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"period\":\"day\""))
        assertTrue(text.contains("\"bars\""))
        assertTrue(text.contains("\"label\":\"09-11\""))
        assertTrue(text.contains("\"prevClose\":185000"))    // 倒数第二根收盘 1850.00 元
    }

    @Test
    fun `chart intraday returns avg price series`() = testApplication {
        boot()
        val r = client.get("/chart/sh600519?period=intraday")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"period\":\"intraday\""))
        assertTrue(text.contains("\"avgPrice\":[185100,185200]"))   // 累计额 / 累计量
        assertTrue(text.contains("\"label\":\"09:30\""))
    }

    @Test
    fun `chart invalid period returns 400 invalid_period`() = testApplication {
        boot()
        val r = client.get("/chart/sh600519?period=hourly")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("invalid_period"))
    }

    @Test
    fun `chart invalid token returns 400 invalid_codes`() = testApplication {
        boot()
        val r = client.get("/chart/600519")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("invalid_codes"))
    }

    @Test
    fun `chart upstream failure returns 502`() = testApplication {
        boot()
        val r = client.get("/chart/sz300750")
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertTrue(r.bodyAsText().contains("upstream_unavailable"))
    }
}
