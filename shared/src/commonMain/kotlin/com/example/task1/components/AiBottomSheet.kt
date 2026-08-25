package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.data.AiAnalysis
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun AiBottomSheet(analysis: AiAnalysis, stock: StockItem?, onDismiss: () -> Unit, onViewReport: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        // handle
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp).background(Color(0xFFCBCBCB), RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
        // header
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(AppColors.AiLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    AppIcon("sparkles", modifier = Modifier.size(16.dp))
                }
                Text(text = "智能分析", color = AppColors.MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.background(AppColors.AiBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(text = "Power by Ai模型", color = AppColors.RiskText, fontSize = 10.sp)
                }
            }
            Box(
                modifier = Modifier.size(28.dp).background(AppColors.PageBg, RoundedCornerShape(14.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon("x-circle", modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        MiniStockCard(stock)
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedVisibility(visible = shown, enter = expandVertically(animationSpec = tween(260, delayMillis = 0), expandFrom = Alignment.Top)) {
            TrendSection(analysis, shown)
        }
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedVisibility(visible = shown, enter = expandVertically(animationSpec = tween(260, delayMillis = 80), expandFrom = Alignment.Top)) {
            RiskSection(analysis, shown)
        }
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedVisibility(visible = shown, enter = expandVertically(animationSpec = tween(260, delayMillis = 160), expandFrom = Alignment.Top)) {
            BuySection(analysis, shown)
        }
        Spacer(modifier = Modifier.height(20.dp))
        // CTA
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(AppColors.CtaBg, RoundedCornerShape(24.dp)).clickable(onClick = onViewReport).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "查看完整报告", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            AppIcon("arrow-right", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun MiniStockCard(stock: StockItem?) {
    if (stock == null) return
    Row(modifier = Modifier.fillMaxWidth().border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = stock.name, color = AppColors.MainText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = stock.code, color = AppColors.SubGray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = Utils.formatPrice2(stock.price), color = AppColors.RiseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.background(AppColors.RiseBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text = Utils.formatPercent(stock.changePct), color = AppColors.RiseRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TrendSection(analysis: AiAnalysis, shown: Boolean) {
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(220))
    SectionCard {
        SectionHeader(icon = "trending-up", title = "涨势分析", rightText = analysis.trendLabel, rightTextColor = AppColors.SubGray)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "近5日走势特征", color = AppColors.SubGray, fontSize = 13.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "MACD金叉形成", color = AppColors.Green, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        RiseSparkline(modifier = Modifier.fillMaxWidth().height(48.dp), progress = p)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = analysis.trendText, color = AppColors.MainText, fontSize = 13.sp)
    }
}

@Composable
fun RiskSection(analysis: AiAnalysis, shown: Boolean) {
    val g0 by animateFloatAsState(if (shown) 1f else 0f, tween(280))
    val g1 by animateFloatAsState(if (shown) 1f else 0f, tween(280, delayMillis = 40))
    val g2 by animateFloatAsState(if (shown) 1f else 0f, tween(280, delayMillis = 80))
    SectionCard {
        SectionHeader(icon = "shield-alert", title = "风险评估", rightText = analysis.riskLevel, rightTextColor = AppColors.RiskOrange)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.Green, grow = g0)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.RiskOrange, showDot = true, grow = g1)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.GaugeHigh, grow = g2)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "低风险", color = AppColors.SubGray, fontSize = 12.sp)
            Text(text = analysis.riskText, color = AppColors.RiskText, fontSize = 12.sp)
            Text(text = "高风险", color = AppColors.SubGray, fontSize = 12.sp)
        }
    }
}

@Composable
fun BuySection(analysis: AiAnalysis, shown: Boolean) {
    val frac by animateFloatAsState(if (shown) (analysis.score / 100f).coerceIn(0f, 1f) else 0f, tween(300))
    SectionCard {
        SectionHeader(icon = "award", title = "买入建议", rightText = "推荐指数 ${analysis.score}/100", rightTextColor = AppColors.RecommendPurple)
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(9.dp).background(AppColors.Border, RoundedCornerShape(5.dp))) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(9.dp).background(AppColors.AiLight, RoundedCornerShape(5.dp)))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "建议分批建仓，目标价位 ${Utils.formatPriceWhole(analysis.targetPrice)}，止损位 ${Utils.formatPriceWhole(analysis.stopLossPrice)}。",
            color = AppColors.MainText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) { content() }
}

@Composable
private fun SectionHeader(icon: String, title: String, rightText: String, rightTextColor: Color = AppColors.MainText) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, color = AppColors.MainText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = rightText, color = rightTextColor, fontSize = 13.sp)
    }
}

@Composable
private fun GaugeSegment(modifier: Modifier, color: Color, showDot: Boolean = false, grow: Float = 1f) {
    Box(modifier = modifier.graphicsLayer { scaleX = grow }.height(8.dp).background(color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        if (showDot) {
            Box(modifier = Modifier.size(13.dp).background(Color.White, RoundedCornerShape(7.dp)))
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
}
