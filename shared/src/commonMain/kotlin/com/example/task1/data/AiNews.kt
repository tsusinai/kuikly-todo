package com.example.task1.data

import com.example.task1.base.Utils

/**
 * 报告页「相关资讯」的条目。
 *
 * [meta] 是来源/口径标签，不是时间戳——此前写死的「今日 09:30 / 昨日 18:00」是假时间，
 * 页面刷新也不会变，反而暴露是编的。宁可标口径，不编时间。
 */
data class NewsItem(
    val title: String,
    val meta: String,
)

/**
 * 相关资讯 mock：**按个股确定性生成**，替代此前所有股票共用的两条硬编码文案
 * （港股也会被套上「年度分红方案落地，股息率维持高位」这种 A 股语境）。
 *
 * 素材全部取自已有字段（行业 / 涨跌幅 / 量比 / 大盘基准差），
 * 因此同一只票的资讯与它的行情、结论自洽，不同票之间也不重样。
 * 接真实资讯源后，替换本函数即可。
 */
fun mockNews(item: StockItem): List<NewsItem> {
    val heavyVolume = item.volumeRatio >= FactorThresh.TAG_VOLUME_RATIO
    val volumeTag = if (heavyVolume) "成交明显放大" else "成交温和"
    val flow = if (item.changePct >= 0) "资金净流入居前" else "资金净流出居前"

    val delta = item.benchmarkDelta
    val vsIndex = when {
        delta == null -> "大盘对比数据暂缺"
        delta > 0.0 -> "今日跑赢大盘 ${Utils.formatPercent(delta)}"
        delta < 0.0 -> "今日跑输大盘 ${Utils.formatPercentNoSign(-delta)}"
        else -> "今日与大盘基本持平"
    }

    // 行业缺失时不硬凑板块名:直连腾讯路径曾把标题写成「未分类板块成交温和」这种读不通的句子
    val sector = item.industry.takeIf { it.isNotBlank() && it != IndustryBook.UNCLASSIFIED }
    // 必须写 `${sector}`：Kotlin 允许 CJK 作标识符，`$sector板块` 会被整个当成变量名
    val sectorTitle = if (sector == null) "${item.name}$volumeTag，$flow" else "${sector}板块$volumeTag，$flow"

    return listOf(
        NewsItem(sectorTitle, if (sector == null) "个股动态" else "板块动态"),
        NewsItem("${item.name}$vsIndex", "大盘对比"),
    )
}
