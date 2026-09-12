package com.example.task1.backend.route

import com.example.task1.backend.dto.ErrorDto
import com.example.task1.backend.dto.HealthDto
import com.example.task1.backend.model.ChartPeriod
import com.example.task1.backend.service.ChartService
import com.example.task1.backend.service.UpstreamUnavailable
import com.example.task1.backend.service.WatchlistService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** 标的 token 形如 sh600519 / sz300750 / hk00700。 */
private val TOKEN_PATTERN = Regex("^(sh|sz|hk)[0-9]+\$")

/** 装配 plugins + 路由。供 main 与测试共用。 */
fun Application.modules(watchlist: WatchlistService, chart: ChartService) {
    // Ktor 2.x 的 CallLogging 默认级别是 TRACE,INFO 下什么都不打;联调时要一行一请求。
    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        json(Json { prettyPrint = false })
    }
    routing {
        apiRoutes(watchlist, chart)
    }
}

fun io.ktor.server.routing.Route.apiRoutes(watchlist: WatchlistService, chart: ChartService) {
    get("/health") {
        call.respond(HealthDto("ok"))
    }

    get("/watchlist") {
        val codes = call.request.queryParameters["codes"]
        if (codes.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        // 格式校验:每段须为 市场前缀 + 数字
        val ok = codes.split(",").all { it.matches(TOKEN_PATTERN) }
        if (!ok) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        try {
            call.respond(watchlist.fetchWatchlist(codes))
        } catch (e: UpstreamUnavailable) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto("upstream_unavailable"))
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal_error"))
        }
    }

    get("/analysis/{token}") {
        val token = call.parameters["token"]
        if (token == null || !token.matches(TOKEN_PATTERN)) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        try {
            val dto = watchlist.fetchAnalysis(token)
            if (dto == null) {
                call.respond(HttpStatusCode.NotFound, ErrorDto("not_found"))
            } else {
                call.respond(dto)
            }
        } catch (e: UpstreamUnavailable) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto("upstream_unavailable"))
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal_error"))
        }
    }

    get("/chart/{token}") {
        val token = call.parameters["token"]
        if (token == null || !token.matches(TOKEN_PATTERN)) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        // 缺省日 K;非法周期在此拦下,service 只接受枚举
        val period = ChartPeriod.of(call.request.queryParameters["period"] ?: ChartPeriod.DAY.wire)
        if (period == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_period"))
            return@get
        }
        try {
            val dto = chart.fetchChart(token, period)
            if (dto == null) {
                call.respond(HttpStatusCode.NotFound, ErrorDto("not_found"))
            } else {
                call.respond(dto)
            }
        } catch (e: UpstreamUnavailable) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto("upstream_unavailable"))
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal_error"))
        }
    }
}
