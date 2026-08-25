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

    /** 2.35 -> "+2.35%", -0.80 -> "-0.80%". */
    fun formatPercent(changePct: Double): String {
        val sign = if (changePct >= 0) "+" else ""
        val abs = (kotlin.math.abs(changePct) * 100.0).roundToLong()
        return sign + formatChange(abs)
    }

    private fun formatChange(absHundredths: Long): String {
        val whole = absHundredths / 100
        val frac = (absHundredths % 100).toInt()
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        return "$whole.$fracStr%"
    }

}