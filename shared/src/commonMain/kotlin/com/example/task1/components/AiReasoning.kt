package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiFactors
import com.example.task1.data.StockItem
import com.example.task1.data.buildReasoning
import com.example.task1.components.core.ChevronDown
import com.example.task1.components.core.ExpandableReveal
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
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
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * AI 信心度标签：基于三因子强度给出「信心 N%」。
 * 纯展示层估算（后端不提供该字段）：55 分基础分 + 45 分按三因子在各自满分中的占比加权。
 */
@Composable
fun AiConfidenceBadge(analysis: AiAnalysis, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(AppColors.AiBadgeBg, AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = "信心 ${aiConfidenceOf(analysis)}%", color = AppColors.RiskText, fontSize = AppTypography.Tiny)
    }
}

/**
 * 因子 → 信心度。
 *
 * 此前是「三因子齐全就 92%」的三档常量：看着像一个精确测量值，其实所有个股都一样，是假的。
 * 改成按因子强度加权后，不同个股得到不同数值，且高低可解释（动量占 50%、价值 30%、风险 20%）。
 * 因子全缺（后端未返回 factors）时回退 60。
 *
 * 公开出来是因为：[AiConfidenceBadge]（抽屉）与报告页的「AI 综合分析」标题都要用它，
 * 而两处的呈现方式不同（徽章 / 右侧小字），共用数值、各自决定样式。
 */
fun aiConfidenceOf(analysis: AiAnalysis): Int {
    val f = analysis.factors
    if (f.momentum == 0 && f.value == 0 && f.risk == 0) return 60
    val ratio = (f.momentum / 60f) * 0.5f + (f.value / 25f) * 0.3f + (f.risk / 8f) * 0.2f
    return (55f + 45f * ratio.coerceIn(0f, 1f)).roundToInt()
}

/**
 * AI 推理链折叠区：默认收起，点击展开「涨势/风险/买入」三组推理要点，
 * 让用户看到 AI 结论背后的思考步骤（可解释性）。
 */
@Composable
fun AiReasoning(item: StockItem, analysis: AiAnalysis, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val steps = remember(item, analysis) { buildReasoning(item, analysis) }
    // 展开必须由单一补间值驱动：AnimatedVisibility/expandVertically 的过渡在 Kuikly 上不逐帧执行
    // （表现为「啪」地跳出来），ExpandableReveal + animateFloatAsState 才是本项目验证过的可靠路径
    val p by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        // 折叠开关：浅底胶囊 + 靛蓝描边，让「这里可以点」在视觉上成立（此前只是一行灰字，
        // 混在正文里读起来像说明文字而不是控件）
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.Sm),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .background(AppColors.PageBg, AppShapes.Pill)
                    .border(1.dp, AppColors.AiLight.copy(alpha = 0.35f), AppShapes.Pill)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "收起 AI 推理过程" else "展开 AI 推理过程",
                    color = AppColors.RiskText,
                    fontSize = AppTypography.BodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(6.dp))
                // 箭头随展开进度翻转：收起指下、展开指上，中间是连续转过去的（不再靠 ▴/▾ 两个字符硬切）
                ChevronDown(modifier = Modifier.size(10.dp).rotate(180f * p), color = AppColors.RiskText)
            }
        }
        // 推理要点
        ExpandableReveal(progress = p, modifier = Modifier.fillMaxWidth()) {
            // 底色必须与页面底色不同:报告页改成白底后，原来的 PageBg(白) 内嵌块在白卡外会彻底看不见边界
            Column(modifier = Modifier.fillMaxWidth().background(AppColors.HeaderBg, AppShapes.Card).padding(AppSpacing.Md)) {
                steps.forEachIndexed { idx, step ->
                    if (idx > 0) Spacer(modifier = Modifier.height(AppSpacing.Md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 靛蓝短竖条：三组推理的分组锚点（此前三段只有加粗标题，扫读时找不到边界）
                        Box(
                            modifier = Modifier.width(3.dp).height(11.dp)
                                .background(AppColors.AiLight, RoundedCornerShape(2.dp)),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = step.title, color = AppColors.MainText, fontSize = AppTypography.BodySmall, fontWeight = FontWeight.SemiBold)
                    }
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
