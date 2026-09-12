package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule

/**
 * 数据源路由:按「自研后端 → 直连腾讯 → 本地样例」逐级降级。
 *
 * 降级判据是「这一层有没有取到数据」,而不是异常——网络失败/解析失败在数据层已被吸收成空结果,
 * 所以这里没有 try/catch 森林;但 [kotlinx.coroutines.CancellationException] 必须放行,否则协程取消会被吃掉。
 *
 * 命中层级由各数据源自己盖章进 [WatchlistBundle.source],页面据此显示「实时 / 缓存 / 离线」。
 */
class RoutedStockApi(
    private val backend: BackendApi?,
    private val live: StockApi,
    private val fallback: StockApi = SampleStockApi,
) : StockApi {

    override suspend fun fetchWatchlist(): WatchlistBundle {
        if (backend != null) {
            backend.watchlist()?.let { return it }
        }
        val liveBundle = live.fetchWatchlist()
        if (liveBundle.stocks.isNotEmpty()) return liveBundle
        return fallback.fetchWatchlist()
    }

    override suspend fun fetchStock(code: String): StockItem? {
        if (backend != null) {
            backend.watchlist()?.stocks?.find { it.code == code }?.let { return it }
        }
        live.fetchStock(code)?.let { return it }
        return fallback.fetchStock(code)
    }

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis {
        if (backend != null) {
            backend.analysis(code)?.let { return it }
        }
        // 后端不可用:在真实行情上用本地规则引擎推导,保证「报告页数值与行情一致」
        live.fetchStock(code)?.let { return deriveAiAnalysis(it) }
        return fallback.fetchAiAnalysis(code)
    }

    override suspend fun fetchGlobalAdvice(): String = fallback.fetchGlobalAdvice()
}

/** 走势数据源路由:后端不可用/无数据时回退样例走势,保证图表区恒有内容。 */
class RoutedChartApi(
    private val backend: BackendApi?,
    private val fallback: ChartApi = SampleChartApi,
) : ChartApi {

    override suspend fun fetchChart(code: String, period: ChartPeriod): StockChartData {
        val live = backend?.chart(code, period)
        if (live != null && live.candles.isNotEmpty()) return live
        return fallback.fetchChart(code, period)
    }
}

/**
 * 页面侧的唯一装配入口:一行拿到带降级的行情源与走势源。
 *
 * 需要 [NetworkModule] 才能构造(Kuikly 的网络模块依赖 Activity),因此由 Composable 注入,
 * 数据层本身不持有任何 UI 概念。
 */
object StockApis {

    /** 后端开关。演示后端时置 true;只想看直连腾讯/离线样例时置 false。 */
    var backendEnabled: Boolean = true

    fun stocks(network: () -> NetworkModule): StockApi = RoutedStockApi(
        backend = if (backendEnabled) BackendApi(network) else null,
        live = TencentStockApi(network = network),
    )

    fun chart(network: () -> NetworkModule): ChartApi = RoutedChartApi(
        backend = if (backendEnabled) BackendApi(network) else null,
    )
}
