package com.example.task1.data

import com.example.task1.data.StockItem

data class AiProfile(
    val action: String,   // 操作建议: 重点关注/低吸关注/持股观望/建议回避
    val signal: String,   // 信号: MACD金叉/量能放大/低位企稳/超跌反弹
    val score: Int,       // 评分 0-100
    val scenario: String, // 场景: 建议加自选/建议建仓/建议减仓/继续持有
)

enum class GroupDimension(val label: String) {
    ACTION("操作建议"),
    SIGNAL("信号题材"),
    SCORE("评分分层"),
    SCENARIO("操作场景"),
}

data class StockGroup(val title: String, val stocks: List<StockItem>)

private fun scoreTier(score: Int): Pair<String, Int> = when {
    score >= 85 -> "高分推荐" to 0
    score in 70..84 -> "中分观察" to 1
    else -> "低分慎入" to 2
}

/** 按 [dimension] 分组:ACTION/SIGNAL/SCENARIO 按画像字段分桶,SCORE 按分数分档;组内按 score 降序(并列按 changePct 降序)。 */
fun groupStocks(stocks: List<StockItem>, dimension: GroupDimension): List<StockGroup> {
    // 每个分组的规范顺序(组间排序);未命中的组追加在后
    val order: Map<String, Int> = when (dimension) {
        GroupDimension.ACTION -> mapOf("重点关注" to 0, "低吸关注" to 1, "持股观望" to 2, "建议回避" to 3)
        GroupDimension.SIGNAL -> mapOf("量能放大" to 0, "MACD金叉" to 1, "低位企稳" to 2, "超跌反弹" to 3)
        GroupDimension.SCORE -> mapOf("高分推荐" to 0, "中分观察" to 1, "低分慎入" to 2)
        GroupDimension.SCENARIO -> mapOf("建议加自选" to 0, "建议建仓" to 1, "建议减仓" to 2, "继续持有" to 3)
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
