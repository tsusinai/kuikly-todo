package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.BuySection
import com.example.task1.base.Utils
import com.example.task1.components.MiniStockCard
import com.example.task1.components.RiskSection
import com.example.task1.components.TrendSection
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiFactors
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page

/**
 * AI 完整分析报告页（占位落地页）。
 *
 * 通过路由名 "aiReport" 注册，作为 AI 弹层里「查看完整报告」的目标页面。
 * 设计稿未定义该页，这里复用弹层的 AI 区块（MiniStockCard / TrendSection / RiskSection / BuySection）
 * 拼装成整页布局；标题「完整分析报告」，后续再细化。
 */
@Page("aiReport")
class AiReportPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        // 进入页面时先把 Compose 内容设置到容器里
        setContent { AiReportScreen() }
    }
}

@Composable
fun AiReportScreen() {
    var stock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    // 进入页面时取自选列表第一只股票，并拉取它的 AI 分析数据（内置样例，后续可切换真实行情）
    LaunchedEffect(Unit) {
        val first = SampleStockApi.fetchWatchlist().stocks.firstOrNull()
        stock = first
        analysis = first?.let { SampleStockApi.fetchAiAnalysis(it.code) }
    }
    Column(modifier = Modifier.fillMaxSize().background(AppColors.PageBg).padding(16.dp)) {
        Text(text = "完整分析报告", color = AppColors.MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (analysis != null && stock != null) {
            MiniStockCard(stock)
            Spacer(modifier = Modifier.height(16.dp))
            TrendSection(analysis!!, shown = true)
            Spacer(modifier = Modifier.height(16.dp))
            RiskSection(analysis!!, shown = true)
            Spacer(modifier = Modifier.height(16.dp))
            BuySection(analysis!!, shown = true)
            Spacer(modifier = Modifier.height(16.dp))
            FactorSection(analysis!!.factors)
        }
    }
}

/** D4 因子明细区:动量/价值/风险分 + 行业 + 强于大盘基准差。 */
@Composable
private fun FactorSection(f: AiFactors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionRow("动量分", f.momentum.toString())
        SectionRow("价值分", f.value.toString())
        SectionRow("风险分", f.risk.toString())
        val rank = if (f.industryRank >= 0) "（组内第 ${f.industryRank + 1}）" else ""
        SectionRow("行业", f.industry + rank)
        SectionRow("强于大盘", f.benchmarkDelta?.let { Utils.formatPercent(it) } ?: "—")
    }
}

@Composable
private fun SectionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = AppColors.SubGray, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(text = value, color = AppColors.MainText, fontSize = 13.sp)
    }
}
