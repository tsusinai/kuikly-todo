package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.core.Badge
import com.example.task1.components.core.ChartPeriodTabs
import com.example.task1.components.core.ChevronBack
import com.example.task1.components.core.ErrorStateBox
import com.example.task1.components.core.LoadingStateBox
import com.example.task1.components.core.SectionDivider
import com.example.task1.components.core.StockChart
import com.example.task1.data.ChartPeriod
import com.example.task1.data.StockApis
import com.example.task1.data.StockChartData
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 股票详情页。
 *
 * 通过路由名 "stockDetail" 注册：自选列表页在展开卡上点「查看详情 ›」后携带 `code` 参数进入本页。
 * 布局自顶向下：标题(name+code) → 价格块(现价/涨跌/涨跌幅) → 高/低/开 → 市值/流通/市盈 → ETF 行
 * → AI 推介行 → 周期分段(分时/日K/周K/月K/年K) → 标准走势图 [StockChart]。
 *
 * 「code」从页面参数读取。行情与走势都走 [StockApis] 的降级链(自研后端 → 直连腾讯 → 本地样例),
 * 取数失败显示可重试的失败态,而不是永久转圈。
 */
@Page("stockDetail")
class StockDetailPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent { StockDetailScreen() }
    }
}


@Composable
fun StockDetailScreen() {
    val activity = LocalActivity.current
    // 数据源链:自研后端 → 直连腾讯 → 本地样例;图表同链路(后端 /chart → 样例走势)
    fun network(): NetworkModule = activity.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val stockApi = remember { StockApis.stocks { network() } }
    val chartApi = remember { StockApis.chart { network() } }

    var stock by remember { mutableStateOf<StockItem?>(null) }
    var period by remember { mutableStateOf(ChartPeriod.INTRADAY) }
    // 进页面就把全部周期拉下来(并行),切周期直接命中缓存,不再回落到加载骨架
    var charts by remember { mutableStateOf<Map<ChartPeriod, StockChartData>>(emptyMap()) }
    // 三态:加载(默认) / 成功 / 失败可重试。判定放在取数之后,不靠超时猜测
    var stockFailed by remember { mutableStateOf(false) }
    var chartFailed by remember { mutableStateOf<Set<ChartPeriod>>(emptySet()) }
    var reloadKey by remember { mutableStateOf(0) }
    // 读取路由参数 code（页面传参，PagerManager.getCurrentPager().pageData.params）
    val code = LocalConfiguration.current.pageData.params.optString("code")
    // 返回出口:本页由自选页进入,系统返回键在部分端不可靠,给一个显式返回按钮
    fun closeDetail() {
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    // 左右滑动切周期:端点上继续滑不循环
    val stepPeriod: (Int) -> Unit = { step ->
        val order = ChartPeriod.entries
        val current = order.indexOf(period)
        val next = (current + step).coerceIn(0, order.size - 1)
        if (next != current) period = order[next]
    }

    LaunchedEffect(code, reloadKey) {
        stockFailed = false
        stock = null
        val loaded = stockApi.fetchStock(code)
        stock = loaded
        stockFailed = loaded == null
    }
    // 各周期之间没有依赖,并行拉:首屏「分时」通常先到,其余周期在后台补齐
    LaunchedEffect(code, reloadKey) {
        charts = emptyMap()
        chartFailed = emptySet()
        coroutineScope {
            for (target in ChartPeriod.entries) {
                launch {
                    val loaded = try {
                        chartApi.fetchChart(code, target)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        null
                    }
                    if (loaded == null || loaded.candles.isEmpty()) {
                        chartFailed = chartFailed + target
                    } else {
                        charts = charts + (target to loaded)
                    }
                }
            }
        }
    }

    // 沉浸式:HeaderBg 一直铺到屏幕顶(状态栏压在它上面),状态栏高度在 Header 内部让位;
    // 内容末尾再让出导航栏高度,最后一项不会被手势条遮住
    val configuration = LocalConfiguration.current
    val statusBarHeight = configuration.statusBarHeight
    val navigationBarHeight = configuration.navigationBarHeight
    LazyColumn(modifier = Modifier.fillMaxSize().background(AppColors.PageBg)) {
        val item = stock
        if (item != null) {
            item { Header(item, topInset = statusBarHeight, onBack = { closeDetail() }) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { PriceBlock(item) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Divider() }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { HighLowOpenBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { MetricsBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { EtfBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { Divider() }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { AiBlock(item) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Divider() }
            item {
                ChartPeriodTabs(
                    current = period,
                    onSelect = { period = it },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            item {
                val data = charts[period]
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    when {
                        data != null -> StockChart(data = data, onPeriodStep = stepPeriod)
                        period in chartFailed -> ErrorStateBox(
                            onRetry = { reloadKey++ },
                            title = "走势加载失败",
                            hint = "未能取到该股的走势数据,点重试重新拉取",
                            minHeight = 240.dp,
                        )
                        else -> ChartSkeleton()
                    }
                }
            }
        } else {
            item {
                if (stockFailed) {
                    ErrorStateBox(
                        onRetry = { reloadKey++ },
                        title = "行情加载失败",
                        hint = "未能取到该股的实时行情,请检查网络后重试",
                        minHeight = 260.dp,
                    )
                } else {
                    LoadingStateBox(minHeight = 260.dp)
                }
            }
        }
        item { Spacer(modifier = Modifier.height((navigationBarHeight + 24f).dp)) }
    }
}

/** 页头:股票名称 + 代码居中,左侧返回按钮,HeaderBg 一直顶到屏幕边缘(状态栏压在它上面)。 */
@Composable
private fun Header(item: StockItem, topInset: Float, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = AppColors.HeaderBg)
            .padding(start = 12.dp, end = 12.dp, top = (topInset + 10f).dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White, AppShapes.Pill)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            ChevronBack(modifier = Modifier.size(16.dp))
        }
        // 右侧等宽占位,标题在整行里保持视觉居中
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = item.name, color = AppColors.MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
        }
        Spacer(modifier = Modifier.width(32.dp))
    }
}

/** 价格块：现价大字 + 涨跌额 + 涨跌幅徽章，居中。 */
@Composable
private fun PriceBlock(item: StockItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Utils.formatPrice2(item.price),
            color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Utils.formatSignedPrice2(item.change),
                color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
                fontSize = AppTypography.Body,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                text = Utils.formatPercent(item.changePct),
                color = if (item.changePct >= 0) AppColors.RiseRed else AppColors.Green,
            )
        }
    }
}

/** 今日高/低/开三指标，横向均分（红涨绿跌）。 */
@Composable
private fun HighLowOpenBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(label = "今日最高", value = Utils.formatPrice2(item.high), valueColor = AppColors.RiseRed)
        StatItem(label = "今日最低", value = Utils.formatPrice2(item.low), valueColor = AppColors.Green)
        StatItem(label = "今开", value = Utils.formatPrice2(item.open), valueColor = AppColors.RiseRed)
    }
}

/** 总市值 / 流通市值 / 市盈率三指标，横向均分。 */
@Composable
private fun MetricsBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(label = "总市值", value = Utils.formatMarketCapYuan(item.marketCap), valueColor = AppColors.MainText)
        StatItem(label = "流通市值", value = Utils.formatMarketCapYuan(item.floatCap), valueColor = AppColors.MainText)
        StatItem(label = "市盈率", value = Utils.formatDouble2(item.pe), valueColor = AppColors.MainText)
    }
}

/** 单个指标（竖排）：浅色标签在上、数值在下。 */
@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 区块分隔线：复用 core 的 SectionDivider。 */
@Composable
private fun Divider() {
    SectionDivider()
}

/** ETF 关联行：关联 ETF 名称 + 该股在其中的占比（占位演示数据）。 */
@Composable
private fun EtfBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "食品饮料ETF 0.22%", color = AppColors.SubGray, fontSize = AppTypography.Caption)
        Text(text = "含${item.name} ${Utils.formatPercentNoSign(item.etfRatio)}%", color = AppColors.SubGray, fontSize = AppTypography.Caption)
    }
}

/** AI 推介行：AI 徽章 + 一句话建议（绿色）。 */
@Composable
private fun AiBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiIconBadge(size = AiIconSize.Small)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.aiBrief, color = AppColors.Green, fontSize = AppTypography.BodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 图表加载骨架：切周期时数据未到，用与图表同高的占位避免布局跳动。 */
@Composable
private fun ChartSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "图表加载中", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
    }
}

