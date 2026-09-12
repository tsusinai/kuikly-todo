package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.task1.base.Utils
import com.example.task1.components.core.Badge
import com.example.task1.components.core.SectionDivider
import com.example.task1.components.core.SectionHeader
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiNarrative
import com.example.task1.data.AiThresh
import com.example.task1.data.FactorThresh
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
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
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuikly.compose.ui.text.withStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 可复用的 AI 分析 section 组件集合（供 AiBottomSheet / AiReportPage 等复用）。
 * 视觉与动画行为保持与原 AiBottomSheet 私有实现一致。
 */

/** 风险等级 → 提醒色（低=绿 / 中=橙 / 高=红），让评级一眼可读。 */
private fun riskLevelColor(level: String): Color = when {
    level.contains("高") -> AppColors.RiseRed
    level.contains("中") -> AppColors.RiskOrange
    level.contains("低") -> AppColors.Green
    else -> AppColors.RiskOrange
}

/** 趋势标签 → 提醒色（看涨=红 / 看跌=绿 / 中性=灰），红涨绿跌习惯。 */
private fun trendLabelColor(label: String): Color = when {
    label.contains("涨") || label.contains("上") -> AppColors.RiseRed
    label.contains("跌") || label.contains("下") -> AppColors.Green
    else -> AppColors.SubGray
}

/** 迷你股票卡：AI 弹层/报告页顶部的精简个股信息（名称+代码 + 现价 + 涨跌幅徽章）。stock 为 null 时不渲染。 */
@Composable
fun MiniStockCard(
    stock: StockItem?,
    modifier: Modifier = Modifier,
) {
    if (stock == null) return
    Row(
        modifier = modifier.fillMaxWidth().border(1.dp, AppColors.Border, AppShapes.Card).padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = stock.name, color = AppColors.MainText, fontSize = AppTypography.Title, fontWeight = FontWeight.Medium)
            Text(text = stock.code, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = Utils.formatPrice2(stock.price), color = if (stock.changePct >= 0) AppColors.RiseRed else AppColors.Green, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(AppSpacing.Sm))
        Badge(
            text = Utils.formatPercent(stock.changePct),
            color = if (stock.changePct >= 0) AppColors.RiseRed else AppColors.Green,
            bgColor = if (stock.changePct >= 0) AppColors.RiseBadgeBg else AppColors.Green.copy(alpha = 0.12f),
        )
    }
}

/**
 * 涨势分析 section：标题 + 近5日走势特征 + sparkline 曲线 + 趋势文字。
 * [shown] 驱动 sparkline 的「画线进度」补间动画（0→1）。正文用叙事化文案，关键结论高亮。
 */
@Composable
fun TrendSection(
    analysis: AiAnalysis,
    stock: StockItem?,
    shown: Boolean,
    modifier: Modifier = Modifier,
    annotate: Boolean = false,
) {
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(220))
    // AI 标注：画线完成后浮现（值驱动 alpha，铁律：不用 fadeIn）
    val annotateP by animateFloatAsState(if (annotate && shown && p >= 1f) 1f else 0f, tween(300))
    Column(modifier = modifier.fillMaxWidth().padding(AppSpacing.Md)) {
        SectionHeader(icon = "ic-trend", title = "涨势分析", rightText = analysis.trendLabel, rightTextColor = trendLabelColor(analysis.trendLabel))
        Spacer(modifier = Modifier.height(AppSpacing.Md))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "近5日走势特征", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "MACD金叉形成", color = AppColors.Green, fontSize = AppTypography.BodySmall)
        }
        Spacer(modifier = Modifier.height(AppSpacing.Sm))
        Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            RiseSparkline(modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomStart), progress = p)
            if (stock != null) {
                // AI 标注 chip：贴曲线右上方，「AI 标注·信号」靛蓝底
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).alpha(annotateP)
                        .background(AppColors.AiBadgeBg, RoundedCornerShape(9.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(6.dp).background(AppColors.AiLight, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "AI 标注·" + stock.aiProfile.signal, color = AppColors.RiskText, fontSize = AppTypography.Tiny)
                }
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.Md))
        SectionDivider()
        Spacer(modifier = Modifier.height(AppSpacing.Md))
        if (stock != null) {
            // 叙事化正文：结论（趋势标签）加粗高亮
            val trend = analysis.trendLabel
            Text(
                text = buildAnnotatedString {
                    val body = AiNarrative.trend(stock, analysis)
                    val idx = body.indexOf(trend)
                    if (idx >= 0) {
                        append(body.substring(0, idx))
                        withStyle(SpanStyle(color = AppColors.RiseRed, fontWeight = FontWeight.Bold)) { append(trend) }
                        append(body.substring(idx + trend.length))
                    } else {
                        append(body)
                    }
                },
                color = AppColors.MainText,
                fontSize = AppTypography.BodySmall,
            )
        } else {
            Text(text = analysis.trendText, color = AppColors.MainText, fontSize = AppTypography.BodySmall)
        }
    }
}

/**
 * 风险评估 section：标题 + 三段式风险仪表（低/中/高）+ 当前评级文字 + 评级原因小字。
 * [shown] 驱动三段仪表依次错峰展开（grow 补间）。
 */
@Composable
fun RiskSection(
    analysis: AiAnalysis,
    stock: StockItem?,
    shown: Boolean,
    modifier: Modifier = Modifier,
) {
    val g0 by animateFloatAsState(if (shown) 1f else 0f, tween(280))
    val g1 by animateFloatAsState(if (shown) 1f else 0f, tween(280, delayMillis = 40))
    val g2 by animateFloatAsState(if (shown) 1f else 0f, tween(280, delayMillis = 80))
    Column(modifier = modifier.fillMaxWidth().padding(AppSpacing.Md)) {
        SectionHeader(icon = "ic-fengkong", title = "风险评估", rightText = analysis.riskLevel, rightTextColor = riskLevelColor(analysis.riskLevel))
        Spacer(modifier = Modifier.height(AppSpacing.Lg))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // å®ä½ç¹è·éå®éç­çº§ï¼ä½âç»¿æ®µï¼ä¸­âæ©æ®µï¼é«âçº¢æ®µ
            val lvl = analysis.riskLevel
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.Green, showDot = lvl.contains("ä½"), grow = g0)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.RiskOrange, showDot = !lvl.contains("ä½") && !lvl.contains("é«"), grow = g1)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.GaugeHigh, showDot = lvl.contains("é«"), grow = g2)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "低风险", color = AppColors.SubGray, fontSize = AppTypography.Caption)
            Text(text = analysis.riskText, color = AppColors.RiskText, fontSize = AppTypography.Caption)
            Text(text = "高风险", color = AppColors.SubGray, fontSize = AppTypography.Caption)
        }
        if (stock != null) {
            // 评级原因：让「中低风险」有解释（波动/估值/方向三要素）
            Spacer(modifier = Modifier.height(AppSpacing.Sm))
            Text(
                text = AiNarrative.risk(stock, analysis),
                color = AppColors.SubGray,
                fontSize = AppTypography.Caption,
            )
        }
    }
}

/**
 * 买入建议 section：标题 + 推荐指数进度条 + 建仓建议文字（目标价/止损位）。
 * [shown] 驱动进度条从 0 填充到 `score/100`。正文用叙事化文案。
 */
@Composable
fun BuySection(
    analysis: AiAnalysis,
    stock: StockItem?,
    shown: Boolean,
    modifier: Modifier = Modifier,
) {
    val frac by animateFloatAsState(if (shown) (analysis.score / 100f).coerceIn(0f, 1f) else 0f, tween(300))
    Column(modifier = modifier.fillMaxWidth().padding(AppSpacing.Md)) {
        SectionHeader(icon = "ic-shouyilv", title = "买入建议", rightText = "推荐指数 ${analysis.score}/100", rightTextColor = AppColors.RecommendPurple)
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(AppColors.Border, RoundedCornerShape(5.dp))) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).background(
                Brush.horizontalGradient(listOf(AppColors.AiLight, AppColors.CtaBg)),
                RoundedCornerShape(5.dp),
            )) {
                // ç«¯ç¹åç¹ï¼ç½å¿éèç¯ï¼æ è®°å½ååä½
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).size(14.dp)
                        .background(Color.White, RoundedCornerShape(7.dp))
                        .border(2.dp, AppColors.CtaBg, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {}
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.Lg))
        if (stock != null) {
            Text(
                text = AiNarrative.buy(stock, analysis),
                color = AppColors.MainText,
                fontSize = AppTypography.Body,
            )
        } else {
            Text(
                text = "建议分批建仓，目标价位 ${Utils.formatPriceWhole(analysis.targetPrice)}，止损位 ${Utils.formatPriceWhole(analysis.stopLossPrice)}。",
                color = AppColors.MainText,
                fontSize = AppTypography.Body,
            )
        }
    }
}

/** 风险仪表中的单段：可选的定位圆点（[showDot]），[grow] 驱动 scaleX 从 0 展开的错峰补间。 */
@Composable
private fun GaugeSegment(modifier: Modifier, color: Color, showDot: Boolean = false, grow: Float = 1f) {
    // 值参数 fillMaxWidth(grow) 驱动（graphicsLayer{lambda} 在 Kuikly 上不逐帧执行，动画假死）
    Box(
        modifier = modifier.fillMaxWidth(grow.coerceIn(0f, 1f)).height(8.dp).background(color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (showDot) {
            Box(modifier = Modifier.size(13.dp).background(Color.White, RoundedCornerShape(7.dp)))
        }
    }
}

/** 行估值结论：PE 阈值分档（与 AiNarrative.risk 同阈值）→ (label, color)。 */
private fun valuationLabel(pe: Double): Pair<String, Color> = when {
    pe <= 0 -> "亏损" to AppColors.RiskOrange
    pe <= AiThresh.PE_GOOD -> "估值偏低" to AppColors.Green
    pe <= AiThresh.PE_BAD -> "估值适中" to AppColors.RiskOrange
    else -> "估值偏高" to AppColors.RiseRed
}

/**
 * 估值画像 section：市盈率 + 估值结论（右侧彩色标签）。
 * [shown] 驱动内容淡入。
 */
@Composable
fun ValuationSection(stock: StockItem?, analysis: AiAnalysis, shown: Boolean, modifier: Modifier = Modifier) {
    if (stock == null) return
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(300))
    val (label, color) = valuationLabel(stock.pe)
    Column(modifier = modifier.fillMaxWidth().padding(AppSpacing.Md).alpha(fade)) {
        SectionHeader(icon = "ic-valuation", title = "估值画像", rightText = label, rightTextColor = color)
        Spacer(modifier = Modifier.height(AppSpacing.Sm))
        InfoRow("市盈率 PE", Utils.formatDouble2(stock.pe) + " 倍")
        InfoRow("估值结论", label)
        InfoRow("结论解释", if (label.contains("偏低")) "估值提供安全垫，适合中长线关注" else "注意估值与成长的匹配度")
    }
}

/**
 * 量价健康度 section：当日振幅 + 信号 + 近5日方向。
 * [shown] 驱动内容淡入。
 */
@Composable
fun VolPriceSection(stock: StockItem?, analysis: AiAnalysis, shown: Boolean, modifier: Modifier = Modifier) {
    if (stock == null) return
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(300))
    val amplitudeText = Utils.formatDouble2(stock.amplitude) + "% " + if (stock.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) "（波动较大）" else "（波动可控）"
    Column(modifier = modifier.fillMaxWidth().padding(AppSpacing.Md).alpha(fade)) {
        SectionHeader(icon = "ic-volprice", title = "量价健康度", rightText = stock.aiProfile.signal, rightTextColor = trendLabelColor(analysis.trendLabel))
        Spacer(modifier = Modifier.height(AppSpacing.Sm))
        InfoRow("当日振幅", amplitudeText)
        InfoRow("信号", stock.aiProfile.signal)
        InfoRow("近5日动量", if (stock.changePct >= 0) "方向向上" else "方向向下")
    }
}

/** 维度卡信息行：左标签 + 右值。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, color = AppColors.SubGray, fontSize = AppTypography.BodySmall, modifier = Modifier.width(96.dp))
        Text(text = value, color = AppColors.MainText, fontSize = AppTypography.BodySmall)
    }
}