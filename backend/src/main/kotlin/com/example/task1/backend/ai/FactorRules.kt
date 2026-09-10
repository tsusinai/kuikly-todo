package com.example.task1.backend.ai

import com.example.task1.backend.dto.AiSummaryDto
import com.example.task1.backend.model.RawStock

/** D1/D2 阈值:单一事实源(与客户端 FactorThresh 镜像)。 */
object FactorThresh {
    const val TAG_VOLUME_RATIO = 1.5
    const val TAG_AMPLITUDE_PCT = 4.0
    const val TAG_LEAD_PCT = 3.0
    const val STALE_MS = 5 * 60_000L
}

/** 大盘基准差:指数缺/失败返回 null。 */
fun deriveBenchmarkDelta(stock: RawStock, indexChangePct: Double?): Double? =
    indexChangePct?.let { stock.changePct - it }

/** D2 动态标签:纯函数,规则与客户端逐条镜像;0 值字段自动不触发。benchmarkDelta 由调用方(未来的 WatchlistService)传入。 */
fun deriveTags(stock: RawStock, benchmarkDelta: Double?): List<String> {
    val t = mutableListOf<String>()
    benchmarkDelta?.let { if (it > 0) t += "强于大盘" }
    if (stock.volumeRatio >= FactorThresh.TAG_VOLUME_RATIO) {
        t += "量比异动"
        if (stock.changePct > 0) t += "放量上攻"
    }
    if (stock.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) t += "振幅放大"
    if (stock.changePct >= FactorThresh.TAG_LEAD_PCT) t += "领涨"
    if (stock.pe in 1.0..AiThresh.PE_GOOD && stock.changePct < 0) t += "低位企稳"
    return t
}

/** D1 摘要:涨跌家数 + 最强/最弱 + 一句倾向。文本逐字镜像客户端 deriveSummary。 */
fun deriveSummary(stocks: List<RawStock>, generatedAt: Long): AiSummaryDto {
    if (stocks.isEmpty()) return AiSummaryDto("暂无行情数据,下拉刷新重试", generatedAt)
    val up = stocks.count { it.changePct > 0 }
    val down = stocks.count { it.changePct < 0 }
    val best = stocks.maxByOrNull { it.changePct }
    val worst = stocks.minByOrNull { it.changePct }
    val avg = stocks.map { it.changePct }.average()
    val tone = if (avg >= 0) "今日整体偏强,关注领涨股" else "今日偏弱,控制仓位"
    val text = "自选${up}涨${down}跌;最强「${best?.name}」${best?.changePct}%,最弱「${worst?.name}」${worst?.changePct}%。$tone。"
    return AiSummaryDto(text, generatedAt)
}