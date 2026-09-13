package com.example.task1.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.task1.data.AiSummary
import com.example.task1.data.DataSource
import com.example.task1.data.GroupDimension
import com.example.task1.data.MarketOverview
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockApi
import com.example.task1.data.StockGroup
import com.example.task1.data.StockItem
import com.example.task1.data.WatchlistBundle
import com.example.task1.data.deriveSummary
import com.example.task1.data.deriveMarketOverview
import com.example.task1.data.enrich
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

    /**
     * 顶部大盘摘要：与列表**同源**（同一次拉取的 stocks 推导），下拉刷新后一起更新。
     * 列表为空（首载未回）时给 null，由组件渲染占位，避免先把 0 当成真数据画出来。
     */
    val overview: MarketOverview?
        get() = if (stocks.isEmpty()) null else deriveMarketOverview(stocks, fetchedAt)

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

    /**
     * 列表级补全:条目级字段(振幅/行业/基准差/标签)交给数据层的 [enrich]——
     * 报告页/详情页读的是同一个函数,同一只票在两处必须给出同一份数值。
     * 这里只负责列表级的 summary 兜底:后端给了摘要就不再用本地规则重算。
     */
    private fun enrichBundle(bundle: WatchlistBundle): WatchlistBundle {
        val items = bundle.stocks.map { enrich(it) }
        val summary = bundle.summary.takeIf { it.text.isNotBlank() } ?: deriveSummary(items, bundle.fetchedAt)
        return bundle.copy(stocks = items, summary = summary)
    }

    /**
     * 拉取自选。降级链(后端 → 直连腾讯 → 离线样例)已在数据层完成,这里只负责区分
     * 「拿到了新数据」与「这一轮没拿到、沿用上次」——后者盖章 CACHE,让角标如实反映数据年龄。
     */
    private suspend fun fetch(): WatchlistBundle = try {
        val bundle = api.fetchWatchlist()
        when {
            // 真数据(自研后端 / 直连腾讯):直接用,诚信标注 missing
            bundle.source != DataSource.OFFLINE -> {
                missingStock = bundle.missing
                enrichBundle(bundle)
            }
            // 只有离线样例可用:已持有真数据就沿用旧的并盖章 CACHE(角标会显示更新时间),
            // 否则首载即离线,直接接受样例
            stocks.isNotEmpty() -> {
                missingStock = 0
                WatchlistBundle(stocks, fetchedAt, DataSource.CACHE)
            }
            else -> {
                missingStock = 0
                bundle
            }
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
