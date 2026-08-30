package com.example.task1.backend.route

import com.example.task1.backend.dto.ErrorDto
import com.example.task1.backend.dto.HealthDto
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

/** 装配 plugins + 路由。供 main 与测试共用。 */
fun Application.modules(service: WatchlistService) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = false })
    }
    routing {
        watchlistRoutes(service)
    }
}

fun io.ktor.server.routing.Route.watchlistRoutes(service: WatchlistService) {
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
        val ok = codes.split(",").all { it.matches(Regex("^(sh|sz|hk)[0-9]+\$")) }
        if (!ok) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        try {
            call.respond(service.fetchWatchlist(codes))
        } catch (e: UpstreamUnavailable) {
            call.respond(HttpStatusCode.BadGateway, ErrorDto("upstream_unavailable"))
        } catch (e: Throwable) {
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal_error"))
        }
    }

    get("/analysis/{token}") {
        val token = call.parameters["token"]
        if (token == null || !token.matches(Regex("^(sh|sz|hk)[0-9]+\$"))) {
            call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_codes"))
            return@get
        }
        try {
            val dto = service.fetchAnalysis(token)
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
