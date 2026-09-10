package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 腾讯实时行情。用框架级 NetworkModule.requestGet 拉 qt.gtimg.cn。
 * 自选代码固定,本地维护 市场前缀 + code→name 映射(规避跨端 GBK 解码)。
 *
 * 【字段索引 - 已对照真实响应核对】
 * 腾讯 `~` 分隔协议:name(1)/code(2)/price(3)/open(5)/change(31)/changePct(32)/high(33)/low(34)/
 * pe(39,A股与HK均为39;40恒为空)/floatCap(44,亿)/marketCap(45,亿)。
 * ⚠️ 勿用 21..24(买卖五档)或 32..35(错位到涨跌%/最高/最低/“价格·成交量·成交额”复合字段)。
 *
 * 解析统一抽到 [RawQuote] 常量 + [parseQuote] 中间结构,StockItem 与页面不感知协议位号;
 * HK 与 A 股在 31..45 位号一致(pe 同为 39,40 恒为空)。
 *
 * 失败容错:单票解析/字段缺失跳过但计入 [lastMissing],页面据此做「部分成功」角标(不静默);
 * 整体网络失败返回空表,由页面整体回退离线。
 */
class TencentStockApi(
    private val aiProvider: AiProfileProvider = RuleEngineAiProvider,
    private val network: () -> NetworkModule,
) : StockApi {

    /** 腾讯 `~` 分隔字段位号(A股与 HK 一致)。 */
    private object RawQuote {
        const val NAME = 1
        const val CODE = 2
        const val PRICE = 3
        const val OPEN = 5
        const val CHANGE = 31
        const val CHANGE_PCT = 32
        const val HIGH = 33
        const val LOW = 34
        const val PE_A = 39
        const val PE_HK = 39   // 实测 HK 的 PE 也在 39,40 恒为空
        const val FLOAT_CAP = 44   // 单位:亿
        const val MARKET_CAP = 45  // 单位:亿
    }

    /** 行情仅用于派生 StockItem 与画像;StockItem/页面不依赖协议位号。 */
    private data class Quote(
        val price: Long, val change: Long, val changePct: Double,
        val high: Long, val low: Long, val open: Long,
        val marketCap: Long, val floatCap: Long, val pe: Double,
    )

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

    /** 上次 fetchWatchlist 因字段缺失未能返回的股票数(0=全成功)。供页面做部分失败角标。 */
    var lastMissing: Int = 0
        private set

    override suspend fun fetchWatchlist(): WatchlistBundle {
        val query = codebook.joinToString(",") { (code, m) -> "${m.first}${code}" }
        val url = "http://qt.gtimg.cn/q=$query"
        val raw = requestRaw(url)
        if (raw == null) {               // 整体网络失败 → 空表,由页面回退离线(缓存/固定数据)
            lastMissing = codebook.size
            return WatchlistBundle(emptyList(), nowMillis(), DataSource.OFFLINE)
        }
        val items = mutableListOf<StockItem>()
        var missing = 0
        for ((code, market) in codebook) {
            val q = parseQuote(extractBody(raw, market.first, code), market.first)
            if (q == null) { missing++; continue }
            val base = StockItem(
                id = code, name = market.second, code = code,
                price = q.price, change = q.change, changePct = q.changePct,
                aiEnabled = false, aiBrief = "",
                high = q.high, low = q.low, open = q.open,
                marketCap = q.marketCap, floatCap = q.floatCap,
                pe = q.pe, etfRatio = 0.0,
                aiProfile = AiProfile(AiLabels.ACTION_HOLD, AiLabels.SIGNAL_BOTTOM, 60, AiLabels.SCENARIO_KEEP), // 占位,下面重填
            )
            val profile = aiProvider.profileFor(base)
            val enabled = profile.score >= AiThresh.AI_ENABLED
            items.add(base.copy(aiEnabled = enabled, aiBrief = briefText(profile), aiProfile = profile))
        }
        lastMissing = missing
        return WatchlistBundle(items, nowMillis(), DataSource.LIVE)
    }

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis {
        val item = fetchWatchlist().stocks.find { it.code == code }
        return item?.let { deriveAiAnalysis(it) } ?: SampleStockApi.fetchAiAnalysis(code)
    }

    override suspend fun fetchStock(code: String): StockItem? = fetchWatchlist().stocks.find { it.code == code }
    override suspend fun fetchGlobalAdvice(): String = ""

    // 非 JSON 回包被包为 {"data":"原始内容"},取 optString("data") 得原始文本
    private suspend fun requestRaw(url: String): String? {
        return suspendCoroutine { cont ->
            val nm = network()
            nm.requestGet(url, JSONObject()) { data, success, errorMsg, _ ->
                if (success) cont.resume(data.optString("data")) else cont.resume(null)
            }
        }
    }

    /** 从原始文本中取 `v_<market><code>="..."` 的引号内内容。 */
    private fun extractBody(raw: String?, market: String, code: String): String? {
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

    /** 把单只股票的 `~` 字段解析为 [Quote];关键位缺失返回 null(该票计入 [lastMissing])。 */
    private fun parseQuote(body: String?, market: String): Quote? {
        if (body == null) return null
        val f = body.split("~")
        if (f.size <= RawQuote.LOW) return null
        fun at(i: Int) = if (i < f.size) f[i] else "0"
        val pe = at(if (market == "hk") RawQuote.PE_HK else RawQuote.PE_A)
        return Quote(
            price = fen(at(RawQuote.PRICE)),
            change = fen(at(RawQuote.CHANGE)),
            changePct = at(RawQuote.CHANGE_PCT).toDoubleOrNull() ?: 0.0,
            high = fen(at(RawQuote.HIGH)),
            low = fen(at(RawQuote.LOW)),
            open = fen(at(RawQuote.OPEN)),
            marketCap = yi(at(RawQuote.MARKET_CAP)),
            floatCap = yi(at(RawQuote.FLOAT_CAP)),
            pe = pe.toDoubleOrNull() ?: 0.0,
        )
    }

    private fun fen(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L)
    private fun yi(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 1_0000_0000L) } ?: 0L)

    private fun briefText(p: AiProfile): String = "${p.signal},${p.action}(${p.score}分)"
}
