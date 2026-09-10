package com.example.task1.backend.ai

/** 静态行业表:与客户端 SampleStockApi 的 7 只样本一致;未知 → "未分类"。 */
object IndustryMap {
    private val map = mapOf(
        "600519" to "白酒", // 贵州茅台
        "000858" to "白酒", // 五粮液
        "00700" to "互联网", // 腾讯控股
        "300750" to "电池", // 宁德时代
        "002594" to "汽车", // 比亚迪
        "601318" to "保险", // 中国平安
        "688981" to "半导体", // 中芯国际
    )
    fun of(code: String): String = map[code] ?: "未分类"
}