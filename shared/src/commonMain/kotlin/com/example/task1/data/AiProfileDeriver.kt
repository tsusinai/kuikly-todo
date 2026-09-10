package com.example.task1.data

/**
 * 由实时行情推导 AI 画像(演示模型,纯函数,阈值取自 [AiThresh] / [AiLabels])。
 * 腾讯接口不含 AI,故本地规则替代;真正 AI 通过 [AiProfileProvider] 接入。
 */
fun deriveAiProfile(item: StockItem): AiProfile {
    val changePct = item.changePct
    val pe = item.pe

    // 操作建议
    val action = when {
        changePct > AiThresh.ACTION_FOCUS_PCT -> AiLabels.ACTION_FOCUS
        changePct in AiThresh.ACTION_DIP_PCT..AiThresh.ACTION_FOCUS_PCT -> AiLabels.ACTION_DIP
        changePct > AiThresh.ACTION_AVOID_PCT -> AiLabels.ACTION_HOLD
        else -> AiLabels.ACTION_AVOID
    }
    // 信号(用涨幅 + 市盈率启发)
    val signal = when {
        changePct > AiThresh.SIGNAL_SURGE_PCT && pe <= 0 -> AiLabels.SIGNAL_VOLUME
        changePct > AiThresh.SIGNAL_SURGE_PCT -> AiLabels.SIGNAL_MACD
        pe in 1.0..AiThresh.PE_GOOD -> AiLabels.SIGNAL_BOTTOM
        else -> AiLabels.SIGNAL_OVERSOLD
    }
    // 评分 0-100
    val (momentumScore, valueScore, riskScore) = scoreParts(changePct, pe)
    val score = (50 + momentumScore + valueScore + riskScore).coerceIn(0, 100)

    // 场景
    val scenario = when {
        action == AiLabels.ACTION_FOCUS && score >= AiThresh.SCORE_HIGH -> AiLabels.SCENARIO_ADD
        action == AiLabels.ACTION_DIP -> AiLabels.SCENARIO_BUILD
        action == AiLabels.ACTION_HOLD -> AiLabels.SCENARIO_KEEP
        else -> AiLabels.SCENARIO_CUT
    }
    return AiProfile(action, signal, score, scenario)
}

/** 评分三部分(momentum/value/risk)。推导逻辑与 deriveAiProfile 原内联一致。 */
private fun scoreParts(changePct: Double, pe: Double): Triple<Int, Int, Int> = Triple(
    (changePct.coerceIn(-5.0, 8.0) / 8.0 * 60.0).toInt(),                     // momentum 0-60
    if (pe in 1.0..AiThresh.PE_GOOD) 25 else if (pe > AiThresh.PE_BAD) 10 else 15, // value 0-25
    when {                                                                     // risk 0-8
        changePct < -2.0 -> 0
        changePct < 0.0 -> 5
        else -> 8
    },
)

/**
 * 由个股画像 + 实时行情派生弹层 AiAnalysis(粗版)。
 * 解决「点哪只都显示同一句看涨85分」的固定 mock 脱节:trendLabel 来自涨跌幅、
 * score 用画像 score、目标/止损价用现价±比例([AiThresh.TARGET_RATIO]/[AiThresh.STOP_RATIO])。
 * 等真 LLM 再替换本实现。
 */
fun deriveAiAnalysis(item: StockItem): AiAnalysis {
    val p = item.aiProfile
    val pct = item.changePct
    val parts = scoreParts(pct, item.pe)
    val trendLabel = when {
        pct > AiThresh.ACTION_FOCUS_PCT -> "短期看涨信号明显"
        pct > 0.0 -> "短期震荡偏强"
        pct > -2.0 -> "短期窄幅整理"
        else -> "短期承压回落"
    }
    val riskLevel = when {
        p.score >= AiThresh.SCORE_HIGH -> "低风险"
        p.score >= AiThresh.SCORE_MID -> "中低风险"
        else -> "中高风险"
    }
    val riskText = "当前评级:$riskLevel"
    val trendText = "驱动${p.action}(${p.score}分),${p.signal};今日涨跌幅${pct}%。"
    // 目标/止损价:现价(分)±比例
    val targetPrice = (item.price + item.price * AiThresh.TARGET_RATIO).toLong()
    val stopLossPrice = (item.price - item.price * AiThresh.STOP_RATIO).toLong()
    return AiAnalysis(trendLabel, trendText, riskLevel, riskText, p.score, targetPrice, stopLossPrice, factors = AiFactors(parts.first, parts.second, parts.third, item.industry, benchmarkDelta = item.benchmarkDelta))
}
