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
import com.example.task1.components.core.SectionDivider
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
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
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import kotlinx.coroutines.FlowPreview

/**
 * 股票详情页。
 *
 * 通过路由名 "stockDetail" 注册：自选列表页在展开卡上点「查看详情 ›」后携带 `code` 参数进入本页。
 * 布局自顶向下：标题(name+code) → 价格块(现价/涨跌/涨跌幅) → 高/低/开 → 市值/流通/市盈 → ETF 行
 * → AI 推介行 → 标签行(分时/日K/周K/月K/更多) → 图表占位。
 *
 * 「code」从页面参数读取；图表区暂以占位 + 预留接口呈现（后续接真实 K 线/分时）。
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
    var stock by remember { mutableStateOf<StockItem?>(null) }
    var selectedTab by remember { mutableStateOf("分时") }
    // 读取路由参数 code（页面传参，PagerManager.getCurrentPager().pageData.params）
    val code = LocalConfiguration.current.pageData.params.optString("code")

    LaunchedEffect(code) { stock = SampleStockApi.fetchStock(code) }

    val tabs = listOf("分时", "日K", "周K", "月K", "更多")

    // 状态栏避让：背景铺满全屏（含状态栏区域），内容整体下移 statusBarHeight
    val statusBarHeight = LocalConfiguration.current.statusBarHeight
    LazyColumn(modifier = Modifier.fillMaxSize().background(AppColors.PageBg).padding(top = statusBarHeight.dp)) {
        val item = stock
        if (item != null) {
            item { Header(item) }
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
            item { TabRow(tabs = tabs, selectedTab = selectedTab, onSelect = { selectedTab = it }) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { ChartPlaceholder() }
        } else {
            item { LoadingPlaceholder() }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/** 页头：股票名称 + 代码，居中，HeaderBg 背景。 */
@Composable
private fun Header(item: StockItem) {
    Column(
        modifier = Modifier.fillMaxWidth().background(color = AppColors.HeaderBg).padding(top = 16.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = item.name, color = AppColors.MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.code, color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
    }
}

/** 价格块：现价大字 + 涨跌额 + 涨跌幅徽章，居中。 */
@Composable
private fun PriceBlock(item: StockItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = Utils.formatPrice2(item.price), color = AppColors.RiseRed, fontSize = 32.sp, fontWeight = FontWeight.Bold)
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

/** 图表周期切换行：分时/日K/周K/月K/更多，选中加粗。 */
@Composable
private fun TabRow(tabs: List<String>, selectedTab: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Text(
                text = tab,
                color = if (selected) AppColors.MainText else AppColors.SubGray,
                fontSize = AppTypography.Title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable { onSelect(tab) },
            )
        }
    }
}

/** 走势图占位框：预留接真实 K 线/分时接口。 */
@Composable
private fun ChartPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(240.dp)
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "走势图表（预留接口）", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
    }
}

/** 数据加载中的占位提示。 */
@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "加载中…", color = AppColors.SubGray, fontSize = AppTypography.Body)
    }
}
