package com.example.task1.data

/**
 * 自选代码 → 所属行业的本地兜底表。
 *
 * 腾讯 `~` 协议**没有行业字段**，自研后端目前也没提供，所以直连/离线路径下
 * [StockItem.industry] 恒为「未分类」——报告页「其他维度 · 行业」是空的，
 * 相关资讯还会写成「未分类板块成交温和，资金净流出居前」这种读不通的句子。
 *
 * 因此本地维护一份小表兜底（与后端 `ai/IndustryMap.kt`、[SampleStockApi] 里各票的行业三者同源）；
 * 后端一旦真给了 industry，[enrich] 会原样保留后端值，不会被这张表顶掉。
 * 接入真实行业数据源后，替换本表即可。
 */
object IndustryBook {

    /** 行业缺失时的统一占位值。集中一处，避免字符串在多文件里各自拼写。 */
    const val UNCLASSIFIED = "未分类"

    private val byCode: Map<String, String> = mapOf(
        "600519" to "白酒",
        "00700" to "互联网",
        "300750" to "电池",
        "002594" to "汽车",
        "601318" to "保险",
        "000858" to "白酒",
        "688981" to "半导体",
    )

    /** 查不到返回 null：调用方据此保留「未分类」，不臆造行业。 */
    fun of(code: String): String? = byCode[code]
}
