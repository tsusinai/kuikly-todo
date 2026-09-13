package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.task1.base.Utils
import com.example.task1.components.core.Badge
import com.example.task1.components.core.SectionDivider
import com.example.task1.components.core.SectionHeader
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiLabels
import com.example.task1.data.AiNarrative
import com.example.task1.data.AiThresh
import com.example.task1.data.FactorThresh
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
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
import com.tencent.kuikly.compose.ui.unit.Dp

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

/**
 * 信号标签 → 卡内释义短语（「量能放大」→「量能放大」）。
 *
 * 存在意义是**不要硬编码某个具体信号**：个股信号是四选一，写死任一值都会在其余三种情况下自相矛盾。
 * 未知信号原样回显，保证 UI 不会因为多一个枚举值而空着。
 */
private fun signalPhrase(signal: String): String = when (signal) {
    AiLabels.SIGNAL_VOLUME -> "量能放大"
    AiLabels.SIGNAL_MACD -> "MACD 形成金叉"
    AiLabels.SIGNAL_BOTTOM -> "低位企稳"
    AiLabels.SIGNAL_OVERSOLD -> "超跌反弹"
    else -> signal
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
 * 涨势分析 section：标题 + 当日走势特征 + 走势 + 趋势文字。
 * [shown] 驱动走势的「画线进度」补间动画（0→1）。正文用叙事化文案，关键结论高亮。
 *
 * @param chartContent 走势区内容槽：报告页传入真实分时图（或它的加载失败重试块）。
 *   为 null（抽屉场景，无取数）时退回轻量 sparkline。用内容槽而不是直接收 StockChartData，
 *   是为了让「取数状态机」留在页面里——组件不需要知道什么叫加载中、什么叫失败。
 * @param contentPadding 左右/内边距：报告页传 0（由页面的 20dp 边距统一提供），抽屉用默认值保持不变。
 * @param showNarrative 走势图下方的趋势正文。报告页传 false：报告页顶部已有 AI 总结卡，
 *   同样的「今日跌 X%，低位企稳；短期窄幅整理」会整段重复；抽屉没有总结卡，保留正文。
 */
@Composable
fun TrendSection(
    analysis: AiAnalysis,
    stock: StockItem?,
    shown: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: Dp = AppSpacing.Md,
    showNarrative: Boolean = true,
    chartContent: (@Composable () -> Unit)? = null,
) {
    // 画线时长与段落舒展节奏对齐:让「展开到位 → 开始画线」成为可感知的两拍
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(520, easing = FastOutSlowInEasing))
    Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
        SectionHeader(icon = "ic-trend", title = "涨势分析", rightText = analysis.trendLabel, rightTextColor = trendLabelColor(analysis.trendLabel))
        Spacer(modifier = Modifier.height(AppSpacing.Md))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "当日走势特征", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            Spacer(modifier = Modifier.weight(1f))
            // 必须跟个股信号走：写死「MACD金叉形成」会出现「趋势承压回落 + MACD 金叉」这种自相矛盾的一行。
            // 取色同步用 trendLabelColor，与右上角趋势标签同源。
            stock?.let {
                Text(
                    text = signalPhrase(it.aiProfile.signal),
                    color = trendLabelColor(analysis.trendLabel),
                    fontSize = AppTypography.BodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.Sm))
        if (chartContent != null) {
            chartContent()
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                // 方向随涨跌：下跌股配一条上升曲线，与「短期承压回落」的结论直接矛盾
                RiseSparkline(
                    modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomStart),
                    progress = p,
                    up = (stock?.changePct ?: 0.0) >= 0.0,
                )
            }
        }
        if (showNarrative) {
            // 这条分隔线只能跟着正文一起出现：正文被隐藏时留下一条孤线，就成了页面上唯一
            // 一条带 20dp 内缩的「分割线」，与满幅的块间分隔线不是一套语言
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
    contentPadding: Dp = AppSpacing.Md,
) {
    val g0 by animateFloatAsState(if (shown) 1f else 0f, tween(420, easing = FastOutSlowInEasing))
    val g1 by animateFloatAsState(if (shown) 1f else 0f, tween(420, delayMillis = 60, easing = FastOutSlowInEasing))
    val g2 by animateFloatAsState(if (shown) 1f else 0f, tween(420, delayMillis = 120, easing = FastOutSlowInEasing))
    Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
        SectionHeader(icon = "ic-fengkong", title = "风险评估", rightText = analysis.riskLevel, rightTextColor = riskLevelColor(analysis.riskLevel))
        Spacer(modifier = Modifier.height(AppSpacing.Lg))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 定位点跟随实际等级：低→绿段，中→橙段，高→红段
            val lvl = analysis.riskLevel
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.Green, showDot = lvl.contains("低"), grow = g0)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.RiskOrange, showDot = !lvl.contains("低") && !lvl.contains("高"), grow = g1)
            GaugeSegment(modifier = Modifier.weight(1f), color = AppColors.GaugeHigh, showDot = lvl.contains("高"), grow = g2)
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
    contentPadding: Dp = AppSpacing.Md,
) {
    val frac by animateFloatAsState(if (shown) (analysis.score / 100f).coerceIn(0f, 1f) else 0f, tween(600, easing = FastOutSlowInEasing))
    Column(modifier = modifier.fillMaxWidth().padding(contentPadding)) {
        SectionHeader(icon = "ic-shouyilv", title = "买入建议", rightText = "推荐指数 ${analysis.score}/100", rightTextColor = AppColors.RecommendPurple)
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(AppColors.Border, RoundedCornerShape(5.dp))) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).background(
                Brush.horizontalGradient(listOf(AppColors.AiLight, AppColors.CtaBg)),
                RoundedCornerShape(5.dp),
            )) {
                // 端点圆点：白心靛蓝环，标记当前分位
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
            // 圆点直径 13dp > 条高 8dp,超出部分露在白卡上——没有描边就只是一道「白缺口」,
            // 看起来像渲染坏了;补一圈同色描边才读得出这是一个定位点
            Box(
                modifier = Modifier.size(13.dp)
                    .background(Color.White, RoundedCornerShape(7.dp))
                    .border(2.dp, color, RoundedCornerShape(7.dp)),
            )
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
 * 量价健康度 section：当日振幅 + 信号 + 当日方向。
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
        // 口径只说「当日」:信号原型只来自当日涨跌幅,写「近5日」是没有依据的拔高
        InfoRow("当日动量", if (stock.changePct >= 0) "方向向上" else "方向向下")
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
