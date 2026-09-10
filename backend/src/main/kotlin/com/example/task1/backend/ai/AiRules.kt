package com.example.task1.backend.ai

import com.example.task1.backend.dto.AiAnalysisDto
import com.example.task1.backend.dto.AiFactorsDto
import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock

/** 四维画像枚举值:单一事实源,与客户端 `AiLabels` 对齐。改一处漏一处会丢分组。 */
object AiLabels {
    const val ACTION_FOCUS = "重点关注"
    const val ACTION_DIP = "低吸关注"
    const val ACTION_HOLD = "持股观望"
    const val ACTION_AVOID = "建议回避"
    const val SIGNAL_VOLUME = "量能放大"
    const val SIGNAL_MACD = "MACD金叉"
    const val SIGNAL_BOTTOM = "低位企稳"
    const val SIGNAL_OVERSOLD = "超跌反弹"
    const val SCENARIO_ADD = "建议加自选"
    const val SCENARIO_BUILD = "建议建仓"
    const val SCENARIO_CUT = "建议减仓"
    const val SCENARIO_KEEP = "继续持有"
}

/** 阈值:单一事实源,与客户端 `AiThresh` 对齐。 */
object AiThresh {
    const val SCORE_HIGH = 85
    const val SCORE_MID = 70
    const val AI_ENABLED = 80
    const val ACTION_FOCUS_PCT = 3.0
    const val ACTION_DIP_PCT = 1.0
    const val ACTION_AVOID_PCT = -1.0
    const val SIGNAL_SURGE_PCT = 2.0
    const val PE_GOOD = 20.0
    const val PE_BAD = 40.0
    const val TARGET_RATIO = 0.08
    const val STOP_RATIO = 0.05
}

/** 评分三部分(momentum/value/risk);口径逐字镜像客户端 `AiProfileDeriver.scoreParts`。 */
internal fun scoreParts(changePct: Double, pe: Double): Triple<Int, Int, Int> = Triple(
    (changePct.coerceIn(-5.0, 8.0) / 8.0 * 60.0).toInt(),                          // momentum 0-60
    if (pe in 1.0..AiThresh.PE_GOOD) 25 else if (pe > AiThresh.PE_BAD) 10 else 15,  // value 0-25
    when {                                                                          // risk 0-8
        changePct < -2.0 -> 0
        changePct < 0.0 -> 5
        else -> 8
    },
)

/** 由实时行情推导四维画像(纯函数,复刻客户端 deriveAiProfile)。 */
fun deriveProfile(r: RawStock): AiProfileDto {
    val changePct = r.changePct
    val pe = r.pe
    val action = when {
        changePct > AiThresh.ACTION_FOCUS_PCT -> AiLabels.ACTION_FOCUS
        changePct in AiThresh.ACTION_DIP_PCT..AiThresh.ACTION_FOCUS_PCT -> AiLabels.ACTION_DIP
        changePct > AiThresh.ACTION_AVOID_PCT -> AiLabels.ACTION_HOLD
        else -> AiLabels.ACTION_AVOID
    }
    val signal = when {
        changePct > AiThresh.SIGNAL_SURGE_PCT && pe <= 0 -> AiLabels.SIGNAL_VOLUME
        changePct > AiThresh.SIGNAL_SURGE_PCT -> AiLabels.SIGNAL_MACD
        pe in 1.0..AiThresh.PE_GOOD -> AiLabels.SIGNAL_BOTTOM
        else -> AiLabels.SIGNAL_OVERSOLD
    }
    val (momentumScore, valueScore, riskScore) = scoreParts(changePct, pe)
    val score = (50 + momentumScore + valueScore + riskScore).coerceIn(0, 100)
    val scenario = when {
        action == AiLabels.ACTION_FOCUS && score >= AiThresh.SCORE_HIGH -> AiLabels.SCENARIO_ADD
        action == AiLabels.ACTION_DIP -> AiLabels.SCENARIO_BUILD
        action == AiLabels.ACTION_HOLD -> AiLabels.SCENARIO_KEEP
        else -> AiLabels.SCENARIO_CUT
    }
    return AiProfileDto(action, signal, score, scenario)
}

/** 弹层分析(复刻客户端 deriveAiAnalysis)。 */
fun deriveAnalysis(r: RawStock, p: AiProfileDto, benchmarkDelta: Double? = null): AiAnalysisDto {
    val pct = r.changePct
    val parts = scoreParts(pct, r.pe)
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
    val trendText = "驱动${p.action}(${p.score}分),${p.signal};今日涨跌幅${pct}%。"
    return AiAnalysisDto(
        code = r.code,
        name = r.name,
        trendLabel = trendLabel,
        trendText = trendText,
        riskLevel = riskLevel,
        riskText = "当前评级:$riskLevel",
        score = p.score,
        targetPrice = (r.price + r.price * AiThresh.TARGET_RATIO).toLong(),
        stopLossPrice = (r.price - r.price * AiThresh.STOP_RATIO).toLong(),
        factors = AiFactorsDto(parts.first, parts.second, parts.third, IndustryMap.of(r.code), benchmarkDelta = benchmarkDelta),
    )
}
