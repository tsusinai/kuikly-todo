package com.example.task1.backend.parse

import com.example.task1.backend.model.RawStock
import kotlin.math.roundToLong

/**
 * 腾讯 `~` 分隔协议解析。纯函数:字符串 → [RawStock]。
 * 字段位号(2026-09-09 对照真实响应核实;与客户端 TencentStockApi 一致):
 * change(31)/changePct(32)/high(33)/low(34)/pe(39,A股与HK均为39;40恒为空)/floatCap(44,亿)/marketCap(45,亿)。
 * outer(7)/inner(8)/amount(37,A股万→×1e4元,HK原值为元·小数)/turnover(A股38,HK59)/amplitude(43)/volumeRatio(49,A股;HK该位为52周区间→恒0)。
 * ⚠️ 勿用 21..24(买卖五档)或 32..35(错位到涨跌%/最高/最低/“价格·成交量·成交额”复合字段)。
 */
object QuoteParser {
    const val NAME = 1
    const val CODE = 2
    const val PRICE = 3
    const val OPEN = 5
    const val CHANGE = 31
    const val CHANGE_PCT = 32
    const val HIGH = 33
    const val LOW = 34
    const val PE_A = 39
    const val PE_HK = 39   // 实测 hk 的 PE 也在 39,40 恒为空
    const val FLOAT_CAP = 44   // 亿
    const val MARKET_CAP = 45  // 亿
    const val OUTER = 7             // 外盘(手)
    const val INNER = 8             // 内盘(手)
    const val AMOUNT = 37           // 成交额(A股单位:万×1e4;HK 原值为元,含小数)
    const val TURNOVER_A = 38       // 换手率 %(A股)
    const val AMPLITUDE = 43        // 振幅 %
    const val VOLUME_RATIO = 49     // 量比(A股;HK 该位是 52 周区间,非量比 → 恒 0)
    const val TURNOVER_HK = 59      // 换手率 %(HK)

    /** 从整体原始文本取 `v_<market><code>="…"` 的引号内体;无此段返回 null。 */
    fun extractBody(raw: String?, market: String, code: String): String? {
        if (raw == null) return null
        val key = "v_${market}${code}="
        val i = raw.indexOf(key)
        if (i < 0) return null
        val start = raw.indexOf('"', i)
        if (start < 0) return null
        val end = raw.indexOf('"', start + 1)
        if (end < 0) return null
        return raw.substring(start + 1, end)
    }

    /** 解析单只股票 `~` 体;关键位缺失返回 null(该股计入 missing)。 */
    fun parse(body: String?, market: String): RawStock? {
        if (body == null) return null
        val f = body.split("~")
        if (f.size <= LOW) return null
        fun at(i: Int) = if (i < f.size) f[i] else "0"
        val pe = at(if (market == "hk") PE_HK else PE_A)
        return RawStock(
            code = at(CODE),
            market = market,
            name = at(NAME),
            price = fen(at(PRICE)),
            change = fen(at(CHANGE)),
            changePct = at(CHANGE_PCT).toDoubleOrNull() ?: 0.0,
            high = fen(at(HIGH)),
            low = fen(at(LOW)),
            open = fen(at(OPEN)),
            marketCap = yi(at(MARKET_CAP)),
            floatCap = yi(at(FLOAT_CAP)),
            pe = pe.toDoubleOrNull() ?: 0.0,
            turnover = at(if (market == "hk") TURNOVER_HK else TURNOVER_A).toDoubleOrNull() ?: 0.0,
            volumeRatio = if (market == "hk") 0.0 else (at(VOLUME_RATIO).toDoubleOrNull() ?: 0.0),
            amplitude = at(AMPLITUDE).toDoubleOrNull() ?: 0.0,
            amount = if (market == "hk") (at(AMOUNT).toDoubleOrNull()?.roundToLong() ?: 0L)
                     else (at(AMOUNT).toDoubleOrNull()?.let { (it * 10_000.0).roundToLong() } ?: 0L),
            outer = at(OUTER).toLongOrNull() ?: 0L,
            inner = at(INNER).toLongOrNull() ?: 0L,
        )
    }

    // 价位 元→分 ×100
    private fun fen(s: String): Long = s.toDoubleOrNull()?.let { (it * 100.0).roundToLong() } ?: 0L
    // 市值 亿→元 ×1e8
    private fun yi(s: String): Long = s.toDoubleOrNull()?.let { (it * 100_000_000.0).roundToLong() } ?: 0L
}
