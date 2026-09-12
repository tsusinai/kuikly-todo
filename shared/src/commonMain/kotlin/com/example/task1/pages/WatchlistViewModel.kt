package com.example.task1.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.task1.data.AiSummary
import com.example.task1.data.DataSource
import com.example.task1.data.GroupDimension
import com.example.task1.data.MockBenchmark
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockApi
import com.example.task1.data.StockGroup
import com.example.task1.data.StockItem
import com.example.task1.data.WatchlistBundle
import com.example.task1.data.deriveBenchmarkDelta
import com.example.task1.data.deriveSummary
import com.example.task1.data.deriveTags
import com.example.task1.data.groupStocks
import com.tencent.kuikly.lifecycle.ViewModel
import com.tencent.kuikly.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自选列表页的 ViewModel：承载「行情数据加载 + 分组摘要 + 智窗建议」等业务逻辑，
 * 从 [WatchlistScreen] 的 Composable 中抽离，UI 只负责渲染与用户交互。
 *
 * 依赖 [api]（实时行情源）由调用方在 Composable 里创建并注入（因其需 [NetworkModule]，
 * 而 NetworkModule 依赖 Activity，属 UI 层）。ViewModel 自身不持有 Activity / 路由 / 触觉等 UI 概念。
 */
class WatchlistViewModel(
    private val api: StockApi,
) : ViewModel() {

    // —— 列表数据状态 ——
    var stocks by mutableStateOf<List<StockItem>>(emptyList())
        private set
    var summary by mutableStateOf<AiSummary?>(null)
        private set
    var dataSource by mutableStateOf(DataSource.OFFLINE)
        private set
    var fetchedAt by mutableStateOf(0L)
        private set
    var missingStock by mutableStateOf(0)
        private set

    // —— 智窗两态 ——
    var thinking by mutableStateOf(true)
        private set
    var advice by mutableStateOf("")
        private set

    // —— 分组维度 ——
    var dimension by mutableStateOf(GroupDimension.ACTION)

    // 分组值：随 stocks/dimension 变化重算
    val groups: List<StockGroup>
        get() = groupStocks(stocks, dimension)

    /** 触发一次加载（首次进入 / 点重试）。 */
    fun load(showThinking: Boolean = true) {
        viewModelScope.launch {
            applyFetch(showThinking)
            refreshAdvice()
        }
    }

    /** 下拉刷新：不触发「思考中」动画。 */
    fun refresh() {
        viewModelScope.launch {
            applyFetch(showThinking = false)
            refreshAdvice()
        }
    }

    /** 切换分组维度后重算智窗建议。 */
    fun onDimensionChange(d: GroupDimension) {
        dimension = d
        refreshAdvice()
    }

    // —— 内部逻辑 ——

    /** 分组摘要文案：按当前维度对分组生成「重点N只」式覆盖描述（无摘要时的回退文案）。 */
    private fun buildAdvice(groups: List<StockGroup>): String =
        if (groups.isEmpty()) "暂无自选股"
        else groups.joinToString("、") { g -> "${g.title.replace("股票建议", "")}${g.stocks.size}只" }

    /** LIVE 契约尚未携带 tags/行业/基准；本地按与 SampleStockApi 相同口径补全，保证联网时 D1/D2 可见。 */
    private fun enrich(bundle: WatchlistBundle): WatchlistBundle {
        val items = bundle.stocks.map { item ->
            val delta = item.benchmarkDelta
                ?: deriveBenchmarkDelta(item, MockBenchmark.changePctByMarket[MockBenchmark.of(item.code)])
            val withDelta = item.copy(benchmarkDelta = delta)
            withDelta.copy(tags = deriveTags(withDelta))
        }
        return bundle.copy(stocks = items, summary = deriveSummary(items, bundle.fetchedAt))
    }

    /** 拉取自选：优先实时；实时成功→LIVE，失败有缓存→CACHE，首载无缓存→OFFLINE 兜底。 */
    private suspend fun fetch(): WatchlistBundle = try {
        val live = api.fetchWatchlist()
        if (live.stocks.isNotEmpty()) {
            missingStock = (api as? com.example.task1.data.TencentStockApi)?.lastMissing ?: 0
            enrich(live).copy(source = DataSource.LIVE)
        } else {
            missingStock = 0
            if (stocks.isNotEmpty()) WatchlistBundle(stocks, fetchedAt, DataSource.CACHE)
            else SampleStockApi.fetchWatchlist()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        missingStock = 0
        if (stocks.isNotEmpty()) WatchlistBundle(stocks, fetchedAt, DataSource.CACHE)
        else SampleStockApi.fetchWatchlist()
    }

    private suspend fun applyFetch(showThinking: Boolean) {
        if (showThinking) thinking = true
        val bundle = fetch()
        stocks = bundle.stocks
        summary = bundle.summary.takeIf { it.text.isNotBlank() }
        fetchedAt = bundle.fetchedAt
        dataSource = bundle.source
        if (showThinking) {
            delay(800)
            thinking = false
        }
    }

    /** 智窗「全盘 AI 建议」优先复用摘要文本（与摘要卡同源），为空时回退分组拼接。 */
    private fun refreshAdvice() {
        advice = summary?.text?.takeIf { it.isNotBlank() } ?: buildAdvice(groups)
    }
}
