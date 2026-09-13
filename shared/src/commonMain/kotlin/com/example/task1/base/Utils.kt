package com.example.task1.base

import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.manager.BridgeManager
import com.tencent.kuikly.core.manager.PagerManager
import kotlin.math.roundToLong

internal object Utils : BaseObject() {

    fun bridgeModule(pager: String): BridgeModule {
        return PagerManager.getPager(pager).acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    }

    fun logToNative(pagerId: String, content: String) {
        // logToNaive
        bridgeModule(pagerId).log(content)
    }

    fun currentBridgeModule(): BridgeModule {
        return PagerManager.getPager(BridgeManager.currentPageId).acquireModule<BridgeModule>(
            BridgeModule.MODULE_NAME
        )
    }

    fun logToNative(content: String) {
        bridgeModule(BridgeManager.currentPageId).log(content)
    }

    fun convertToPriceStr(price: Long): String {
        return (price / 100f).toString()
    }

    /** cents -> "1856.00" (always 2 decimals). */
    fun formatPrice2(price: Long): String {
        val yuan = price / 100
        val fen = (price % 100).toInt()
        val fenStr = if (fen < 10) "0$fen" else "$fen"
        return "$yuan.$fenStr"
    }

    /** signed cents -> "+4.82" / "-0.80" (absolute change amount). */
    fun formatSignedPrice2(cents: Long): String {
        val sign = if (cents >= 0) "+" else "-"
        val abs = kotlin.math.abs(cents)
        return sign + formatPrice2(abs)
    }

    /** cents -> "1920" (whole yuan, no decimals). */
    fun formatPriceWhole(price: Long): String = (price / 100).toString()

    /**
     * 2.35 -> "+2.35%", -0.80 -> "0.80%"。
     *
     * 负值**不带**负号:这个格式化器服务于涨跌徽章,方向由颜色(红涨绿跌)表达,
     * 再加一个「-」是重复信息。正因如此,**彩色语境之外不要用它**——
     * 无颜色提示的纯文本行会把跌 1.40% 显示成「1.40%」,读起来是反的。
     * 那种场合请用 [formatSignedPercent]。
     */
    fun formatPercent(changePct: Double): String {
        val sign = if (changePct >= 0) "+" else ""
        val abs = (kotlin.math.abs(changePct) * 100.0).roundToLong()
        return sign + formatChange(abs)
    }

    /**
     * 带符号百分数：1.40 -> "+1.40%"，-1.40 -> "-1.40%"。
     *
     * 用于没有颜色兜底的文本(如「相对大盘」这类基准差),避免方向读反。
     */
    fun formatSignedPercent(percent: Double): String {
        val sign = if (percent >= 0) "+" else "-"
        val abs = (kotlin.math.abs(percent) * 100.0).roundToLong()
        return sign + formatChange(abs)
    }

    private fun formatChange(absHundredths: Long): String {
        val whole = absHundredths / 100
        val frac = (absHundredths % 100).toInt()
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        return "$whole.$fracStr%"
    }

    /** 总市值(元) -> "2.30万亿" / "1.63万亿" / "5800.0亿"。 */
    fun formatMarketCapYuan(yuan: Long): String {
        val trillion = 1_0000_0000_0000L   // 1 万亿(元)
        val hundredMillion = 1_0000_0000L   // 1 亿(元)
        return when {
            yuan >= trillion -> formatDecimal(yuan / trillion.toDouble(), 2) + "万亿"
            yuan >= hundredMillion -> formatDecimal(yuan / hundredMillion.toDouble(), 1) + "亿"
            else -> "${yuan}元"
        }
    }

    /**
     * 带符号的金额(元) -> "+1246.0亿" / "-72.6亿" / "+3200元"。
     *
     * 用于「合计市值变动」这类**有方向**的金额：方向除了颜色还有符号，避免在无颜色语境下读反。
     * 0 也带 "+"，与 `formatSignedPrice2` 的约定一致。
     */
    fun formatSignedYuan(yuan: Long): String {
        val sign = if (yuan >= 0) "+" else "-"
        return sign + formatMarketCapYuan(kotlin.math.abs(yuan))
    }

    /** 市盈率 -> 固定两位小数，如 32.0 -> "32.00"。 */
    fun formatDouble2(v: Double): String {
        val scaled = (v * 100).roundToLong()
        val whole = scaled / 100
        val frac = (scaled % 100).toInt()
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        return "$whole.$fracStr"
    }

    /** 占比/比例 -> 不带正号的百分比，最多两位小数、末尾去零：18.8->"18.8%"、0.22->"0.22%"、5->"5%"。 */
    fun formatPercentNoSign(percent: Double): String {
        val scaled = (percent * 100).roundToLong()   // 万分位
        val whole = scaled / 100
        val hundredth = (scaled % 100).toInt()
        return when {
            hundredth == 0 -> "$whole%"
            hundredth % 10 == 0 -> "$whole.${hundredth / 10}%"
            else -> {
                val fracStr = if (hundredth < 10) "0$hundredth" else "$hundredth"
                "$whole.$fracStr%"
            }
        }
    }

    /** 按 [decimals] 位小数格式化浮点数（用于市值展示）。 */
    private fun formatDecimal(value: Double, decimals: Int): String {
        if (decimals <= 0) return value.roundToLong().toString()
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val scaled = (value * factor).roundToLong()
        val whole = scaled / factor
        val frac = (scaled % factor).toInt()
        return "$whole.${frac.toString().padStart(decimals, '0')}"
    }

}
