package com.example.task1.backend.service

import com.example.task1.backend.ai.AiAnalysisProvider
import com.example.task1.backend.ai.AiThresh
import com.example.task1.backend.ai.IndustryMap
import com.example.task1.backend.ai.RuleSummaryProvider
import com.example.task1.backend.ai.SummaryProvider
import com.example.task1.backend.ai.deriveAnalysis
import com.example.task1.backend.ai.deriveBenchmarkDelta
import com.example.task1.backend.ai.deriveSummary
import com.example.task1.backend.ai.deriveTags
import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.dto.AiAnalysisDto
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.dto.AiSummaryDto
import com.example.task1.backend.dto.StockItemDto
import com.example.task1.backend.dto.WatchlistResponseDto
import com.example.task1.backend.model.RawStock
import com.example.task1.backend.parse.QuoteParser

/** 整体上游(腾讯)网络失败 → 由 route 映射为 502。 */
class UpstreamUnavailable(cause: Throwable? = null) : RuntimeException(cause)

/** 各市场基准指数;tokens 市场去重后追加到腾讯请求。 */
private val INDEX_BY_MARKET = mapOf("sh" to "sh000001", "sz" to "sz399001", "hk" to "hkHSI")

private fun nowMillis(): Long = System.currentTimeMillis()   // 后端是 JVM 项目,不引入实验性 Clock

/**
 * 编排:校验 codes 拆 token → client 拉原文 → 逐股 parse → AiEngine.profileForAll →
 * 组装 WatchlistResponseDto。单股失败 missing++,保留成功项。整体网络失败抛 [UpstreamUnavailable]。
 */
class WatchlistService(
    private val client: TencentClient,
    private val aiEngine: AiAnalysisProvider,
    private val summaryProvider: SummaryProvider = RuleSummaryProvider,
) {
    suspend fun fetchWatchlist(codes: String): WatchlistResponseDto {
        val tokens = codes.split(",")
        val market = tokens.map { it.substring(0, 2) }   // "sh"/"sz"/"hk"
        val codesOnly = tokens.map { it.substring(2) }
        val markets = market.distinct().filter { INDEX_BY_MARKET.containsKey(it) }
        val idxTokens = markets.map { INDEX_BY_MARKET.getValue(it) }
        val query = if (idxTokens.isEmpty()) codes else codes + "," + idxTokens.joinToString(",")

        val raw = try {
            client.fetch(query)
        } catch (e: Throwable) {
            throw UpstreamUnavailable(e)
        }

        val indexPct: Map<String, Double?> = markets.associateWith { m ->
            val t = INDEX_BY_MARKET.getValue(m)
            QuoteParser.parse(QuoteParser.extractBody(raw, t.substring(0, 2), t.substring(2)), t.substring(0, 2))?.changePct
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
        require(stocks.size == profiles.size) {
            "AiEngine returned ${profiles.size} profiles for ${stocks.size} stocks"
        }
        val profileByCode = stocks.zip(profiles).associate { it.first.code to it.second }

        val dtos = stocks.map { r ->
            val p = profileByCode[r.code]!!
            val delta = deriveBenchmarkDelta(r, indexPct[r.market])
            StockItemDto(
                turnover = r.turnover,
                volumeRatio = r.volumeRatio,
                amplitude = r.amplitude,
                amount = r.amount,
                outer = r.outer,
                inner = r.inner,
                industry = IndustryMap.of(r.code),
                benchmarkDelta = delta,
                tags = deriveTags(r, delta),
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
        val generatedAt = nowMillis()
        val base = deriveSummary(stocks, generatedAt)
        val anyIndexMissing = stocks.isNotEmpty() && markets.any { indexPct[it] == null }
        val fallbackText = if (anyIndexMissing) base.text + "大盘基准数据缺失。" else base.text
        val text = summaryProvider.summarize(buildSummaryContext(dtos), fallbackText)
        return WatchlistResponseDto(dtos, missing, AiSummaryDto(text, generatedAt, stale = false))
    }

    suspend fun fetchAnalysis(token: String): AiAnalysisDto? {
        if (token.length < 3) return null
        val market = token.substring(0, 2)
        val code = token.substring(2)
        val idxToken = INDEX_BY_MARKET[market]
        val query = if (idxToken == null) token else "$token,$idxToken"
        val raw = try {
            client.fetch(query)
        } catch (e: Throwable) {
            throw UpstreamUnavailable(e)
        }
        val r = QuoteParser.parse(QuoteParser.extractBody(raw, market, code), market) ?: return null
        val indexPct = idxToken?.let {
            QuoteParser.parse(QuoteParser.extractBody(raw, it.substring(0, 2), it.substring(2)), it.substring(0, 2))?.changePct
        }
        val p = aiEngine.profileForAll(listOf(r)).first()
        return deriveAnalysis(r, p, deriveBenchmarkDelta(r, indexPct))
    }
}
/** 给 LLM 的精简行情上下文:最多 20 只,单行 `名称 涨跌幅%(PE,量比,振幅,行业)`。 */
private fun buildSummaryContext(dtos: List<StockItemDto>): String = buildString {
    append("自选${dtos.size}只:")
    dtos.take(20).forEach { d ->
        append("${d.name} ${d.changePct}%(PE${d.pe},量比${d.volumeRatio},振幅${d.amplitude},${d.industry});")
    }
}
