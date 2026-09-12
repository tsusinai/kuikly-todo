package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.data.AiAnalysis
import com.example.task1.data.StockItem
import com.example.task1.data.buildReasoning
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * AI 信心度标签：基于因子数据完整度给出「信心 N%」。
 * 纯展示层估算（非后端精确值）：三因子分齐全 → 高信心，缺一 → 中，缺二 → 低。
 */
@Composable
fun AiConfidenceBadge(analysis: AiAnalysis, modifier: Modifier = Modifier) {
    val f = analysis.factors
    val confidence = when {
        f.momentum > 0 && f.value > 0 && f.risk > 0 -> 92
        f.momentum > 0 || f.value > 0 || f.risk > 0 -> 78
        else -> 60
    }
    Box(
        modifier = modifier.background(AppColors.AiBadgeBg, AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = "信心 $confidence%", color = AppColors.RiskText, fontSize = AppTypography.Tiny)
    }
}

/**
 * AI 推理链折叠区：默认收起，点击展开「涨势/风险/买入」三组推理要点，
 * 让用户看到 AI 结论背后的思考步骤（可解释性）。
 */
@Composable
fun AiReasoning(item: StockItem, analysis: AiAnalysis, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val steps = remember(item, analysis) { buildReasoning(item, analysis) }
    Column(modifier = modifier.fillMaxWidth()) {
        // 折叠开关
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = AppSpacing.Sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "收起 AI 推理过程" else "展开 AI 推理过程",
                color = AppColors.SubGray,
                fontSize = AppTypography.BodySmall,
            )
            Text(
                text = if (expanded) " ▴" else " ▾",
                color = AppColors.SubGray,
                fontSize = AppTypography.BodySmall,
            )
        }
        // 推理要点
        AnimatedVisibility(visible = expanded, enter = expandVertically(animationSpec = tween(220))) {
            Column(modifier = Modifier.fillMaxWidth().background(AppColors.PageBg, AppShapes.Card).padding(AppSpacing.Md)) {
                steps.forEachIndexed { idx, step ->
                    if (idx > 0) Spacer(modifier = Modifier.height(AppSpacing.Md))
                    Text(text = step.title, color = AppColors.MainText, fontSize = AppTypography.BodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    step.points.forEach { point ->
                        Text(
                            text = "· $point",
                            color = AppColors.SubGray,
                            fontSize = AppTypography.Caption,
                        )
                    }
                }
            }
        }
    }
}
