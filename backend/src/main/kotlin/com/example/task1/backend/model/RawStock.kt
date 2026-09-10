package com.example.task1.backend.model

/** 中间值:已解析的单股行情 + code/name/market。是 AiEngine 输入,非外部契约(不 @Serializable)。 */
data class RawStock(
    val code: String,
    val market: String,     // "sh"|"sz"|"hk"
    val name: String,
    val price: Long,        // 分
    val change: Long,       // 分
    val changePct: Double,  // %
    val high: Long,         // 分
    val low: Long,          // 分
    val open: Long,         // 分
    val marketCap: Long,    // 元
    val floatCap: Long,     // 元
    val pe: Double,
    val turnover: Double = 0.0,      // 换手率 %
    val volumeRatio: Double = 0.0,   // 量比 (HK 无 → 0)
    val amplitude: Double = 0.0,     // 振幅 %
    val amount: Long = 0L,           // 成交额 元
    val outer: Long = 0L,            // 外盘(手)
    val inner: Long = 0L,            // 内盘(手)
)
