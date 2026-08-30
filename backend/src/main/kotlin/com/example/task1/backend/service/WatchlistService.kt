package com.example.task1.backend.service

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.ai.AiThresh
import com.example.task1.backend.ai.deriveAnalysis
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.dto.AiAnalysisDto
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.dto.StockItemDto
import com.example.task1.backend.dto.WatchlistResponseDto
import com.example.task1.backend.model.RawStock
import com.example.task1.backend.parse.QuoteParser

/** 整体上游(腾讯)网络失败 → 由 route 映射为 502。 */
class UpstreamUnavailable(cause: Throwable? = null) : RuntimeException(cause)

/**
 * 编排:校验 codes 拆 token → client 拉原文 → 逐股 parse → AiEngine.profileForAll →
 * 组装 WatchlistResponseDto。单股失败 missing++,保留成功项。整体网络失败抛 [UpstreamUnavailable]。
 */
class WatchlistService(
    private val client: TencentClient,
    private val aiEngine: AiAnalysisProvider,
) {
    suspend fun fetchWatchlist(codes: String): WatchlistResponseDto {
        val tokens = codes.split(",")
        val market = tokens.map { it.substring(0, 2) }   // "sh"/"sz"/"hk"
        val codesOnly = tokens.map { it.substring(2) }

        val raw = try {
            client.fetch(codes)
        } catch (e: Throwable) {
            throw UpstreamUnavailable(e)
        }

        val stocks = mutableListOf<RawStock>()
        var missing = 0
        for ((i, mkt) in market.withIndex()) {
            val body = QuoteParser.extractBody(raw, mkt, codesOnly[i])
            val r = QuoteParser.parse(body, mkt)
            if (r == null) { missing++; continue }
            stocks.add(r)
        }

        val profiles = if (stocks.isEmpty()) emptyList() else aiEngine.profileForAll(stocks)
        val profileByCode = stocks.zip(profiles).associate { it.first.code to it.second }

        val dtos = stocks.map { r ->
            val p = profileByCode[r.code]!!
            StockItemDto(
                code = r.code,
                name = r.name,
                price = r.price,
                change = r.change,
                changePct = r.changePct,
                high = r.high,
                low = r.low,
                open = r.open,
                marketCap = r.marketCap,
                floatCap = r.floatCap,
                pe = r.pe,
                aiProfile = p,
                aiEnabled = p.score >= AiThresh.AI_ENABLED,
                aiBrief = "${p.signal},${p.action}(${p.score}分)",
            )
        }
        return WatchlistResponseDto(dtos, missing)
    }

    suspend fun fetchAnalysis(token: String): AiAnalysisDto? {
        if (token.length < 3) return null
        val market = token.substring(0, 2)
        val code = token.substring(2)
        val raw = try {
            client.fetch(token)
        } catch (e: Throwable) {
            return null    // 单 code 分析:网络失败视为找不到,交 route → 404
        }
        val r = QuoteParser.parse(QuoteParser.extractBody(raw, market, code), market) ?: return null
        val p = aiEngine.profileForAll(listOf(r)).first()
        return deriveAnalysis(r, p)
    }
}
