package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 自研后端(task1-backend)这一层。
 *
 * 三个方法在「后端不可达 / 非 2xx / 无数据」时统一返回 null,**不做任何本地兜底**——
 * 兜底是 [RoutedStockApi] 的职责。这样「后端挂了」与「后端答了但没这只票」在路由层可区分,
 * 页面也能诚实地显示「当前是离线样例」而不是假装成功。
 *
 * 客户端零解析:腾讯 `~` 协议、GBK 名称、行业、因子、四维画像全部由后端产出,
 * 这里只做「JSON → 领域模型」的字段搬运。
 */
class BackendApi(private val network: () -> NetworkModule) {

    private val http = BackendHttp(network)

    suspend fun watchlist(): WatchlistBundle? {
        val json = http.getJson("/watchlist?codes=${WatchlistCodes.query}") ?: return null
        val items = json.optJSONArray("stocks").toStocks()
        if (items.isEmpty()) return null
        return WatchlistBundle(
            stocks = items,
            fetchedAt = nowMillis(),
            source = DataSource.LIVE,
            summary = json.optJSONObject("summary").toSummary(),
            missing = json.optInt("missing"),
        )
    }

    suspend fun analysis(code: String): AiAnalysis? {
        val token = WatchlistCodes.tokenOf(code) ?: return null
        return http.getJson("/analysis/$token")?.toAnalysis()
    }

    suspend fun chart(code: String, period: ChartPeriod): StockChartData? {
        val token = WatchlistCodes.tokenOf(code) ?: return null
        val json = http.getJson("/chart/$token?period=${period.wire}") ?: return null
        val candles = json.optJSONArray("bars").toCandles()
        if (candles.isEmpty()) return null
        val avgSource = json.optJSONArray("avgPrice")
        return StockChartData(
            period = period,
            candles = candles,
            prevClose = json.optLong("prevClose"),
            avgPrice = if (avgSource == null) emptyList() else List(avgSource.length()) { avgSource.optLong(it) },
            // 各周期口径一致:均线都在客户端按收盘序列算,后端契约不带 ma
            ma = movingAverages(candles),
        )
    }
}

private fun JSONArray?.toStocks(): List<StockItem> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.toStockItem() }
}

private fun JSONArray?.toCandles(): List<Candle> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val o = optJSONObject(i) ?: return@mapNotNull null
        Candle(
            label = o.optString("label"),
            open = o.optLong("open"),
            high = o.optLong("high"),
            low = o.optLong("low"),
            close = o.optLong("close"),
            volume = o.optLong("volume"),
        )
    }
}

private fun JSONObject.toStockItem(): StockItem? {
    val code = optString("code")
    if (code.isEmpty()) return null
    val profile = optJSONObject("aiProfile")
    return StockItem(
        id = code,
        name = optString("name"),
        code = code,
        price = optLong("price"),
        change = optLong("change"),
        changePct = optDouble("changePct", 0.0),
        aiEnabled = optBoolean("aiEnabled", false),
        aiBrief = optString("aiBrief"),
        aiProfile = AiProfile(
            action = profile?.optString("action") ?: AiLabels.ACTION_HOLD,
            signal = profile?.optString("signal") ?: AiLabels.SIGNAL_BOTTOM,
            score = profile?.optInt("score") ?: 0,
            scenario = profile?.optString("scenario") ?: AiLabels.SCENARIO_KEEP,
        ),
        high = optLong("high"),
        low = optLong("low"),
        open = optLong("open"),
        marketCap = optLong("marketCap"),
        floatCap = optLong("floatCap"),
        pe = optDouble("pe", 0.0),
        // ETF 占比是本地展示字段,后端契约不提供 → 保持 0,不臆造
        etfRatio = 0.0,
        turnover = optDouble("turnover", 0.0),
        volumeRatio = optDouble("volumeRatio", 0.0),
        amplitude = optDouble("amplitude", 0.0),
        amount = optLong("amount"),
        outer = optLong("outer"),
        inner = optLong("inner"),
        industry = optString("industry").ifEmpty { IndustryBook.UNCLASSIFIED },
        benchmarkDelta = nullableDouble("benchmarkDelta"),
        tags = optJSONArray("tags").toStringList(),
    )
}

private fun JSONObject?.toSummary(): AiSummary {
    if (this == null) return AiSummary("", 0L)
    return AiSummary(
        text = optString("text"),
        generatedAt = optLong("generatedAt"),
        stale = optBoolean("stale", false),
    )
}

private fun JSONObject.toAnalysis(): AiAnalysis? {
    if (optString("code").isEmpty()) return null
    val f = optJSONObject("factors")
    return AiAnalysis(
        trendLabel = optString("trendLabel"),
        trendText = optString("trendText"),
        riskLevel = optString("riskLevel"),
        riskText = optString("riskText"),
        score = optInt("score"),
        targetPrice = optLong("targetPrice"),
        stopLossPrice = optLong("stopLossPrice"),
        factors = AiFactors(
            momentum = f?.optInt("momentum") ?: 0,
            value = f?.optInt("value") ?: 0,
            risk = f?.optInt("risk") ?: 0,
            industry = f?.optString("industry") ?: IndustryBook.UNCLASSIFIED,
            industryRank = f?.optInt("industryRank") ?: -1,
            benchmarkDelta = f?.nullableDouble("benchmarkDelta"),
        ),
    )
}

/** 可空数值:Kotlin 侧需与 JSON `null` 区分,故先读哨兵再判空。 */
private fun JSONObject.nullableDouble(name: String): Double? =
    optDouble(name, Double.NaN).takeIf { !it.isNaN() }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it) }.filter { it.isNotEmpty() }
}
