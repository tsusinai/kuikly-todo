package com.example.task1.data

import com.example.task1.base.Utils

/**
 * AI 分析「叙事化」纯函数集合。
 *
 * 定位：把 `AiAnalysis` 里的模板字段改写成**连贯的自然语言**，供弹层/报告页展示，
 * 让用户觉得「AI 在对他说话」，而非「数据库字段被读出来」。
 *
 * 注意：本文件**只改表述方式，不改推导逻辑**（推导仍在 `AiProfileDeriver` / `FactorDeriver`）。
 * 接真 LLM 后，这些叙事函数可由 LLM 直接产出，本文件作为「无 LLM 时的兜底文案」保留。
 */
object AiNarrative {

    /**
     * 趋势叙事：把「趋势标签 + 信号 + 涨跌幅」拼成一句人话。
     * 例：「贵州茅台今日涨 2.35%，近 5 日持续放量上行，MACD 已形成金叉；短期看涨信号明显。」
     */
    fun trend(item: StockItem, analysis: AiAnalysis): String {
        val pct = Utils.formatPercent(item.changePct)
        val direction = when {
            item.changePct > 0 -> "涨 $pct"
            item.changePct < 0 -> "跌 ${Utils.formatPercentNoSign(-item.changePct)}"
            else -> "平盘"
        }
        val signalDesc = signalToSentence(item.aiProfile.signal)
        return "${item.name}今日$direction，近 5 日走势$signalDesc；${analysis.trendLabel}。"
    }

    /**
     * 风险叙事：一句话评级 + 三要素解释（振幅波动 / 估值 / 趋势方向）。
     */
    fun risk(item: StockItem, analysis: AiAnalysis): String {
        val volatility = if (item.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) "振幅 ${Utils.formatDouble2(item.amplitude)}% 波动较大" else "振幅 ${Utils.formatDouble2(item.amplitude)}% 波动可控"
        val valuation = when {
            item.pe <= 0 -> "市盈率为负，注意基本面"
            item.pe <= AiThresh.PE_GOOD -> "PE ${Utils.formatDouble2(item.pe)} 估值偏低"
            item.pe <= AiThresh.PE_BAD -> "PE ${Utils.formatDouble2(item.pe)} 估值适中"
            else -> "PE ${Utils.formatDouble2(item.pe)} 估值偏高"
        }
        val direction = if (item.changePct >= 0) "趋势向上" else "短期承压"
        return "$volatility · $valuation · $direction"
    }

    /**
     * 买入叙事：操作建议 + 评分 + 目标/止损价，连成一句可执行的话。
     */
    fun buy(item: StockItem, analysis: AiAnalysis): String {
        val action = item.aiProfile.action
        val target = Utils.formatPriceWhole(analysis.targetPrice)
        val stop = Utils.formatPriceWhole(analysis.stopLossPrice)
        
        // 措施词随操作建议变化，避免「持股观望/建议回避」却写「可分批建仓」的矛盾
        val plan = when {
            action.contains("回避") -> "建议回避，暂不建仓"
            action.contains("观望") -> "持股观望，目标价 $target，跌破 $stop 再评估"
            else -> "可分批建仓，目标价位 $target，止损位 $stop"
        }
        return "综合建议：$action（推荐指数 ${analysis.score}/100）。$plan。"
    }

    /** 信号字段 → 人话短语。 */
    private fun signalToSentence(signal: String): String = when (signal) {
        AiLabels.SIGNAL_VOLUME -> "量能持续放大"
        AiLabels.SIGNAL_MACD -> "MACD 已形成金叉"
        AiLabels.SIGNAL_BOTTOM -> "低位逐步企稳"
        AiLabels.SIGNAL_OVERSOLD -> "超跌后出现反弹迹象"
        else -> "整体平稳"
    }
}

/** 推理链中的一组步骤。 */
data class ReasonStep(
    val title: String,
    val points: List<String>,
)

/**
 * 可解释推理链：把 AI 结论背后的「思考步骤」拆成三组（涨势/风险/买入），
 * 每组若干「① ② ③」式要点。数据全部来自 [item] 与 [analysis] 的已有字段，不改推导逻辑。
 */
fun buildReasoning(item: StockItem, analysis: AiAnalysis): List<ReasonStep> {
    val trendPoints = listOf(
        "近 5 日涨跌幅 ${Utils.formatPercent(item.changePct)}，${if (item.changePct >= 0) "方向向上" else "方向向下"}",
        "信号：${item.aiProfile.signal}（涨跌幅 ${Utils.formatPercent(item.changePct)}）",
        "趋势结论：${analysis.trendLabel}",
    )
    val riskPoints = listOf(
        "振幅 ${Utils.formatDouble2(item.amplitude)}% ${if (item.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) "≥ 4%，波动偏大" else "< 4%，波动可控"}",
        "市盈率 ${Utils.formatDouble2(item.pe)}，${when { item.pe <= AiThresh.PE_GOOD -> "偏低，价值有支撑"; item.pe <= AiThresh.PE_BAD -> "适中"; else -> "偏高，需留意估值风险" }}",
        "评级：${analysis.riskLevel}",
    )
    val buyPoints = listOf(
        "操作建议：${item.aiProfile.action}",
        "评分 ${analysis.score}/100（${scoreBand(analysis.score)}）",
        "目标价 ${Utils.formatPriceWhole(analysis.targetPrice)}（+${(AiThresh.TARGET_RATIO * 100).toInt()}%），止损 ${Utils.formatPriceWhole(analysis.stopLossPrice)}（-${(AiThresh.STOP_RATIO * 100).toInt()}%）",
    )
    return listOf(
        ReasonStep("涨势推理", trendPoints),
        ReasonStep("风险推理", riskPoints),
        ReasonStep("买入推理", buyPoints),
    )
}

private fun scoreBand(score: Int): String = when {
    score >= AiThresh.SCORE_HIGH -> "高分推荐"
    score >= AiThresh.SCORE_MID -> "中分观察"
    else -> "低分慎入"
}
