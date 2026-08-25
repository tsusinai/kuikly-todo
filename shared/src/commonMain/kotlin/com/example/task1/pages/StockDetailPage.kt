package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.components.AppIcon
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
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
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page

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
        // 进入页面时先把 Compose 内容设置到容器里
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

    LazyColumn(modifier = Modifier.fillMaxSize().background(AppColors.PageBg)) {
        val item = stock
        if (item != null) {
            item { Header(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { PriceBlock(item) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { HighLowOpenBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { MetricsBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { EtfBlock(item) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { AiBlock(item) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { TabRow(tabs = tabs, selectedTab = selectedTab, onSelect = { selectedTab = it }) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { ChartPlaceholder() }
        } else {
            item { LoadingPlaceholder() }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(item: StockItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = item.name, color = AppColors.MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.code, color = AppColors.SubGray, fontSize = 13.sp)
    }
}

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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChangeBadge(item.changePct)
        }
    }
}

@Composable
private fun ChangeBadge(changePct: Double) {
    Box(
        modifier = Modifier
            .background(AppColors.RiseBadgeBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = Utils.formatPercent(changePct),
            color = if (changePct >= 0) AppColors.RiseRed else AppColors.Green,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

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

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, color = AppColors.SubGray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EtfBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "食品饮料ETF 0.22%", color = AppColors.SubGray, fontSize = 12.sp)
        Text(text = "含${item.name} ${Utils.formatPercentNoSign(item.etfRatio)}%", color = AppColors.SubGray, fontSize = 12.sp)
    }
}

@Composable
private fun AiBlock(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(AppColors.AiLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon("sparkles", modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.aiBrief, color = AppColors.Green, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TabRow(tabs: List<String>, selectedTab: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            Text(
                text = tab,
                color = if (selected) AppColors.MainText else AppColors.SubGray,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable { onSelect(tab) },
            )
        }
    }
}

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
        Text(text = "走势图表（预留接口）", color = AppColors.SubGray, fontSize = 13.sp)
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "加载中…", color = AppColors.SubGray, fontSize = 14.sp)
    }
}
