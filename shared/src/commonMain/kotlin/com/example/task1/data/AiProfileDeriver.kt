package com.example.task1.data

/** 由实时行情推导 AI 画像(纯函数,阈值可调)。腾讯接口不含 AI,故本地规则替代。 */
fun deriveAiProfile(item: StockItem): AiProfile {
    val changePct = item.changePct
    val pe = item.pe

    // 操作建议
    val action = when {
        changePct > 3.0 -> "重点关注"
        changePct in 1.0..3.0 -> "低吸关注"
        changePct > -1.0 -> "持股观望"
        else -> "建议回避"
    }
    // 信号(用涨幅 + 市盈率启发)
    val signal = when {
        changePct > 2.0 && pe <= 0 -> "量能放大"
        changePct > 2.0 -> "MACD金叉"
        pe in 1.0..20.0 -> "低位企稳"
        else -> "超跌反弹"
    }
    // 评分 0-100
    val momentumScore = (changePct.coerceIn(-5.0, 8.0) / 8.0 * 60.0).toInt()
    val valueScore = if (pe in 1.0..20.0) 25 else if (pe > 40.0) 10 else 15
    val riskScore = when {
        changePct < -2.0 -> 0
        changePct < 0.0 -> 5
        else -> 8
    }
    val score = (50 + momentumScore + valueScore + riskScore).coerceIn(0, 100)

    // 场景
    val scenario = when {
        action == "重点关注" && score >= 85 -> "建议加自选"
        action == "低吸关注" -> "建议建仓"
        action == "持股观望" -> "继续持有"
        else -> "建议减仓"
    }
    return AiProfile(action, signal, score, scenario)
}
