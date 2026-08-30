package com.example.task1.backend.parse

import com.example.task1.backend.model.RawStock
import kotlin.math.roundToLong

/**
 * 腾讯 `~` 分隔协议解析。纯函数:字符串 → [RawStock]。
 * 字段位号(已对照真实响应核对;与客户端 TencentStockApi 一致)。
 * ⚠️ 勿用 change(21)/changePct(22)/high(23)/low(24)——那些是买卖五档。
 */
object QuoteParser {
    const val NAME = 1
    const val CODE = 2
    const val PRICE = 3
    const val OPEN = 5
    const val CHANGE = 32
    const val CHANGE_PCT = 33
    const val HIGH = 34
    const val LOW = 35
    const val PE_A = 39
    const val PE_HK = 40
    const val FLOAT_CAP = 44   // 亿
    const val MARKET_CAP = 45  // 亿

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
        )
    }

    // 价位 元→分 ×100
    private fun fen(s: String): Long = s.toDoubleOrNull()?.let { (it * 100.0).roundToLong() } ?: 0L
    // 市值 亿→元 ×1e8
    private fun yi(s: String): Long = s.toDoubleOrNull()?.let { (it * 100_000_000.0).roundToLong() } ?: 0L
}
