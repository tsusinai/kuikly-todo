package com.example.task1.data

/** D1/D2 阈值:单一事实源(与后端 AiThresh 镜像)。 */
object FactorThresh {
    const val TAG_VOLUME_RATIO = 1.5
    const val TAG_AMPLITUDE_PCT = 4.0
    const val TAG_LEAD_PCT = 3.0
    const val STALE_MS = 5 * 60_000L
}

/** 大盘基准差:指数缺/失败返回 null。mock 阶段指数由 MockBenchmark 提供。 */
fun deriveBenchmarkDelta(item: StockItem, indexChangePct: Double?): Double? =
    indexChangePct?.let { item.changePct - it }

/**
 * 当日振幅 %：`(最高 − 最低) / 昨收`。
 *
 * 直连腾讯的降级路径没有振幅字段（[StockItem.amplitude] 恒为 0），报告页会显示
 * 「振幅 0.00% 波动可控」这种废话——0 振幅被当成「波动可控」是假信息，不是缺信息。
 * 三个输入（最高/最低/涨跌额）其实都已解析出来，昨收 = 现价 − 涨跌额 即可反推。
 * 已有值（后端返回）时原样保留。
 */
fun deriveAmplitude(item: StockItem): Double {
    if (item.amplitude > 0.0) return item.amplitude
    val prevClose = item.price - item.change
    if (prevClose <= 0L || item.high <= 0L || item.low <= 0L) return item.amplitude
    return (item.high - item.low).toDouble() / prevClose * 100.0
}

/**
 * 补全单个条目缺失的派生字段：振幅 → 行业 → 大盘基准差 → 动态标签。
 *
 * 为什么下沉到数据层：列表页、报告页、详情页走的是不同入口（fetchWatchlist / fetchStock），
 * 此前补全只写在 WatchlistViewModel 里，于是同一只票在列表页挂着「强于大盘」标签、
 * 报告页「强于大盘」却是「—」，报告页的振幅也是 0.00%——数据不一致的根因就在这里。
 *
 * 顺序有依赖：振幅要先补，[deriveTags] 的「振幅放大」才可能命中；
 * 每个字段都是「已有值则保留」，后端（含 LLM）的产出不会被本地规则悄悄顶掉。
 */
fun enrich(item: StockItem): StockItem {
    val withAmplitude = item.copy(amplitude = deriveAmplitude(item))
    val withIndustry = if (withAmplitude.industry.isBlank() || withAmplitude.industry == IndustryBook.UNCLASSIFIED) {
        withAmplitude.copy(industry = IndustryBook.of(withAmplitude.code) ?: withAmplitude.industry)
    } else {
        withAmplitude
    }
    val withDelta = if (withIndustry.benchmarkDelta == null) {
        withIndustry.copy(
            benchmarkDelta = deriveBenchmarkDelta(
                withIndustry,
                MockBenchmark.changePctByMarket[MockBenchmark.of(withIndustry.code)],
            ),
        )
    } else {
        withIndustry
    }
    return withDelta.copy(tags = withDelta.tags.ifEmpty { deriveTags(withDelta) })
}

/** D2 动态标签:纯函数,规则与后端逐条镜像;0 值字段自动不触发。 */
fun deriveTags(item: StockItem): List<String> {
    val t = mutableListOf<String>()
    item.benchmarkDelta?.let { if (it > 0) t += "强于大盘" }
    if (item.volumeRatio >= FactorThresh.TAG_VOLUME_RATIO) {
        t += "量比异动"
        if (item.changePct > 0) t += "放量上攻"
    }
    if (item.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) t += "振幅放大"
    if (item.changePct >= FactorThresh.TAG_LEAD_PCT) t += "领涨"
    if (item.pe in 1.0..AiThresh.PE_GOOD && item.changePct < 0) t += "低位企稳"
    return t
}

/** D1 摘要:涨跌家数 + 最强/最弱 + 一句倾向。stale 由渲染端按 now-fetchedAt 判定。 */
fun deriveSummary(stocks: List<StockItem>, fetchedAt: Long): AiSummary {
    if (stocks.isEmpty()) return AiSummary("暂无行情数据,下拉刷新重试", fetchedAt)
    val up = stocks.count { it.changePct > 0 }
    val down = stocks.count { it.changePct < 0 }
    val best = stocks.maxByOrNull { it.changePct }
    val worst = stocks.minByOrNull { it.changePct }
    val avg = stocks.map { it.changePct }.average()
    val tone = if (avg >= 0) "今日整体偏强,关注领涨股" else "今日偏弱,控制仓位"
    val text = "自选${up}涨${down}跌;最强「${best?.name}」${best?.changePct}%,最弱「${worst?.name}」${worst?.changePct}%。$tone。"
    return AiSummary(text, fetchedAt)
}

/** mock 阶段的大盘指数(固定值,模拟指数行情)。 */
object MockBenchmark {
    val changePctByMarket: Map<String, Double> = mapOf("sh" to 0.62, "sz" to -0.18, "hk" to 0.35)
    fun of(code: String): String = when {
        code.length == 5 -> "hk"                       // 港股 5 位:00700 / 09988
        code.startsWith("6") -> "sh"
        code.startsWith("0") || code.startsWith("3") -> "sz"
        else -> "hk"
    }
}
