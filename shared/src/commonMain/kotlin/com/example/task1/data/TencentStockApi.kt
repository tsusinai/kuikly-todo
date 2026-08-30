package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 腾讯实时行情。用框架级 NetworkModule.requestGet 拉 qt.gtimg.cn。
 * 自选代码固定,本地维护 市场前缀 + code→name 映射(规避跨端 GBK 解码)。
 * 行情字段按腾讯 `~` 分隔协议映射;价位元→分、市值亿→元。AI 画像由 deriveAiProfile 推导。
 */
class TencentStockApi(
    private val aiProvider: AiProfileProvider = RuleEngineAiProvider,
    private val network: () -> NetworkModule,
) : StockApi {

    // 自选代码表(本地固定):code -> (marketPrefix, name)
    private val codebook: List<Pair<String, Pair<String, String>>> = listOf(
        "600519" to ("sh" to "贵州茅台"),
        "00700" to ("hk" to "腾讯控股"),
        "300750" to ("sz" to "宁德时代"),
        "002594" to ("sz" to "比亚迪"),
        "601318" to ("sh" to "中国平安"),
        "000858" to ("sz" to "五粮液"),
        "688981" to ("sh" to "中芯国际"),
    )

    override suspend fun fetchWatchlist(): List<StockItem> {
        val query = codebook.joinToString(",") { (code, m) -> "${m.first}${code}" }
        val url = "http://qt.gtimg.cn/q=$query"
        val raw = requestRaw(url)
        val items = mutableListOf<StockItem>()
        for ((code, market) in codebook) {
            val name = market.second
            val body = extractBody(raw, market.first, code) ?: continue
            val f = body.split("~")
            if (f.size <= 24) continue
            val price = fen(f[3])
            val change = fen(f[21])
            val changePct = f.getOrElse(22) { "0" }.toDoubleOrNull() ?: 0.0
            val high = fen(f.getOrElse(23) { "0" })
            val low = fen(f.getOrElse(24) { "0" })
            val open = fen(f.getOrElse(5) { "0" })
            val marketCap = yi(f.getOrElse(35) { "0" })
            val floatCap = yi(f.getOrElse(34) { "0" })
            val pe = f.getOrElse(29) { "0" }.toDoubleOrNull() ?: 0.0
            val item = StockItem(
                id = code, name = name, code = code,
                price = price, change = change, changePct = changePct,
                aiEnabled = false, aiBrief = "",
                high = high, low = low, open = open,
                marketCap = marketCap, floatCap = floatCap,
                pe = pe, etfRatio = 0.0,
                aiProfile = AiProfile("持股观望", "低位企稳", 60, "继续持有"), // 占位,下面重填
            )
            val profile = aiProvider.profileFor(item)
            val enabled = profile.score >= 80
            items.add(item.copy(aiEnabled = enabled, aiBrief = briefText(profile), aiProfile = profile))
        }
        return items
    }

    // 非 JSON 回包被包为 {"data":"原始内容"},取 optString("data") 得原始文本
    private suspend fun requestRaw(url: String): String? {
        return suspendCoroutine { cont ->
            val nm = network()
            nm.requestGet(url, JSONObject()) { data, success, errorMsg, _ ->
                if (success) cont.resume(data.optString("data")) else cont.resume(null)
            }
        }
    }

    /** 从原始文本中取 `v_sh600519="..."` 的引号内内容。 */
    private fun extractBody(raw: String?, market: String, code: String): String? {
        if (raw == null) return null
        val key = "v_${market}${code}="
        val i = raw.indexOf(key)
        if (i < 0) {
            println("TencentStockApi: no key '$key' in response body (HK prefix or format changed?)")
            return null
        }
        val start = raw.indexOf('"', i)
        if (start < 0) return null
        val end = raw.indexOf('"', start + 1)
        if (end < 0) return null
        return raw.substring(start + 1, end)
    }

    private fun fen(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L)
    private fun yi(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 1_0000_0000L) } ?: 0L)

    private fun briefText(p: AiProfile): String = "${p.signal},${p.action}(${p.score}分)"

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis = SampleStockApi.fetchAiAnalysis(code)
    override suspend fun fetchStock(code: String): StockItem? = fetchWatchlist().find { it.code == code }
    override suspend fun fetchGlobalAdvice(): String = ""
}
