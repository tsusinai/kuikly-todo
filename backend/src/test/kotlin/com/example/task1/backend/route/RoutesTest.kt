package com.example.task1.backend.route

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock
import com.example.task1.backend.service.WatchlistService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StubClient : TencentClient {
    override suspend fun fetch(query: String): String {
        if (query.contains("300750")) throw RuntimeException("upstream")   // 整体失败
        val f = Array(46) { "0" }
        f[1] = "贵州茅台"; f[2] = "600519"; f[3] = "1856.00"; f[5] = "1830.00"
        f[32] = "12.30"; f[33] = "0.67"; f[34] = "1900.00"; f[35] = "1820.00"
        f[39] = "30.1"; f[44] = "10000"; f[45] = "23400"
        return "v_sh600519=\"${f.joinToString("~")}\";"
    }
}

private class StubAi : AiAnalysisProvider {
    override suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto> =
        items.map { AiProfileDto("重点关注", "MACD金叉", 95, "建议加自选") }
}

/** 与 Application.kt 的 modules() 一致:安装 plugins + 路由。 */
private fun io.ktor.server.application.Application.installRoutes(service: WatchlistService) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    routing { watchlistRoutes(service) }
}

class RoutesTest {
    private fun service() = WatchlistService(StubClient(), StubAi())

    @Test
    fun `health returns ok`() = testApplication {
        application { installRoutes(service()) }
        val r = client.get("/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"status":"ok"}""", r.bodyAsText())
    }

    @Test
    fun `missing codes returns 400 invalid_codes`() = testApplication {
        application { installRoutes(service()) }
        val r = client.get("/watchlist")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("invalid_codes"))
    }

    @Test
    fun `upstream failure returns 502`() = testApplication {
        application { installRoutes(service()) }
        val r = client.get("/watchlist?codes=sz300750")
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertTrue(r.bodyAsText().contains("upstream_unavailable"))
    }

    @Test
    fun `watchlist success returns 200 with stocks`() = testApplication {
        application { installRoutes(service()) }
        val r = client.get("/watchlist?codes=sh600519")
        assertEquals(HttpStatusCode.OK, r.status)
        val text = r.bodyAsText()
        assertTrue(text.contains("\"code\":\"600519\""))
        assertTrue(text.contains("\"missing\":0"))
    }

    @Test
    fun `analysis unknown returns 404`() = testApplication {
        application { installRoutes(service()) }
        val r = client.get("/analysis/sz000001")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertTrue(r.bodyAsText().contains("not_found"))
    }
}
