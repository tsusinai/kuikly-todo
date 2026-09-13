package com.example.task1.data

import com.example.task1.data.StockItem

data class AiProfile(
    val action: String,   // 操作建议: 重点关注/低吸关注/持股观望/建议回避
    val signal: String,   // 信号: MACD金叉/量能放大/低位企稳/超跌反弹
    val score: Int,       // 评分 0-100
    val scenario: String, // 场景: 建议加自选/建议建仓/建议减仓/继续持有
)

/**
 * 四维画像枚举值的**单一事实源**。deriveAiProfile / groupStocks / SampleStockApi 统一引用,
 * 避免字符串散落多处、改一处漏三处(错拼一个值分组就丢进「未命中」)。
 */
object AiLabels {
    // action(操作建议)
    const val ACTION_FOCUS = "重点关注"
    const val ACTION_DIP = "低吸关注"
    const val ACTION_HOLD = "持股观望"
    const val ACTION_AVOID = "建议回避"
    // signal(信号)
    const val SIGNAL_VOLUME = "量能放大"
    const val SIGNAL_MACD = "MACD金叉"
    const val SIGNAL_BOTTOM = "低位企稳"
    const val SIGNAL_OVERSOLD = "超跌反弹"
    // scenario(场景)
    const val SCENARIO_ADD = "建议加自选"
    const val SCENARIO_BUILD = "建议建仓"
    const val SCENARIO_CUT = "建议减仓"
    const val SCENARIO_KEEP = "继续持有"
}

/**
 * 阈值**单一事实源**。scoreTier / aiEnabled / deriveAiProfile / deriveAiAnalysis 统一引用,
 * 避免同类语义(分数档位 / 涨跌阈值 / 目标价比例)在多处硬编码漂移。
 */
object AiThresh {
    const val SCORE_HIGH = 85           // 「高分推荐」分档下界
    const val SCORE_MID = 70            // 「中分观察」分档下界
    const val AI_ENABLED = 80           // aiEnabled 门限(值得重点关注)
    const val ACTION_FOCUS_PCT = 3.0    // 重点关注 涨跌幅上界
    const val ACTION_DIP_PCT = 1.0      // 低吸关注 涨跌幅下界
    const val ACTION_AVOID_PCT = -1.0   // 建议回避 涨跌幅上界(更跌)
    const val SIGNAL_SURGE_PCT = 2.0    // 量能放大 / MACD金叉 涨跌幅阈值
    const val PE_GOOD = 20.0            // 低位企稳 / 价值分: PE≤20 视为好
    const val PE_BAD = 40.0             // 高 PE 惩罚阈值
    const val TARGET_RATIO = 0.08       // 弹层目标价 = 现价 ×(1+8%)
    const val STOP_RATIO = 0.05         // 弹层止损价 = 现价 ×(1-5%)
}

/**
 * 分组维度。[desc] 是给用户的「这个维度到底怎么分」一句话，展示在维度选择弹层里，
 * 与 [label] 一起构成维度的**单一事实源**——UI 不再自己编词。
 */
enum class GroupDimension(val label: String, val desc: String) {
    ACTION("操作建议", "按 AI 给出的操作建议聚合"),
    SIGNAL("信号题材", "按技术信号与题材聚合"),
    SCORE("评分分层", "按 AI 评分档位聚合"),
    SCENARIO("操作场景", "按建议的持有场景聚合"),
}

data class StockGroup(val title: String, val stocks: List<StockItem>)

private fun scoreTier(score: Int): Pair<String, Int> = when {
    score >= AiThresh.SCORE_HIGH -> "高分推荐" to 0
    score in AiThresh.SCORE_MID..(AiThresh.SCORE_HIGH - 1) -> "中分观察" to 1
    else -> "低分慎入" to 2
}

/** 按 [dimension] 分组:ACTION/SIGNAL/SCENARIO 按画像字段分桶,SCORE 按分数分档;组内按 score 降序(并列按 changePct 降序)。 */
fun groupStocks(stocks: List<StockItem>, dimension: GroupDimension): List<StockGroup> {
    // 每个分组的规范顺序(组间排序);未命中的组追加在后
    val order: Map<String, Int> = when (dimension) {
        GroupDimension.ACTION -> mapOf(
            AiLabels.ACTION_FOCUS to 0, AiLabels.ACTION_DIP to 1, AiLabels.ACTION_HOLD to 2, AiLabels.ACTION_AVOID to 3)
        GroupDimension.SIGNAL -> mapOf(
            AiLabels.SIGNAL_VOLUME to 0, AiLabels.SIGNAL_MACD to 1, AiLabels.SIGNAL_BOTTOM to 2, AiLabels.SIGNAL_OVERSOLD to 3)
        GroupDimension.SCORE -> mapOf("高分推荐" to 0, "中分观察" to 1, "低分慎入" to 2)
        GroupDimension.SCENARIO -> mapOf(
            AiLabels.SCENARIO_ADD to 0, AiLabels.SCENARIO_BUILD to 1, AiLabels.SCENARIO_CUT to 2, AiLabels.SCENARIO_KEEP to 3)
    }
    fun categoryKey(item: StockItem): Pair<String, Int> = when (dimension) {
        GroupDimension.ACTION -> item.aiProfile.action to (order[item.aiProfile.action] ?: Int.MAX_VALUE)
        GroupDimension.SIGNAL -> item.aiProfile.signal to (order[item.aiProfile.signal] ?: Int.MAX_VALUE)
        GroupDimension.SCORE -> scoreTier(item.aiProfile.score)
        GroupDimension.SCENARIO -> item.aiProfile.scenario to (order[item.aiProfile.scenario] ?: Int.MAX_VALUE)
    }

    val buckets = LinkedHashMap<String, MutableList<StockItem>>()
    for (item in stocks) {
        val (key, _) = categoryKey(item)
        buckets.getOrPut(key) { mutableListOf() }.add(item)
    }
    return buckets.entries
        .sortedWith(compareBy { order[it.key] ?: Int.MAX_VALUE })
        .map { (title, list) ->
            StockGroup(
                title = "${title}股票建议",
                stocks = list.sortedWith(compareByDescending<StockItem> { it.aiProfile.score }.thenByDescending { it.changePct }),
            )
        }
}
