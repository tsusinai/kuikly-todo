package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.BuySection
import com.example.task1.components.MiniStockCard
import com.example.task1.components.RiskSection
import com.example.task1.components.TrendSection
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.core.ExpandFraction
import com.example.task1.data.AiAnalysis
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
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
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 段落窗口插值：progress 越过 [start] 后在 0.3 宽度内从 0 到 1。 */
private fun seg(progress: Float, start: Float): Float =
    ((progress - start) / 0.3f).coerceIn(0f, 1f)

/** 思考阶段时长：给足「AI 正在分析」的感知（思考词轮播 + 图标旋转）。 */
private const val THINKING_MS = 2000L
/** 结论舒展总时长（单 progress 贯穿，三段按时间窗错峰）。 */
private const val REVEAL_MS = 1000

/**
 * AI 分析抽屉内容（长按股票拉起）。
 *
 * 节奏：弹出 → 思考阶段（[THINKING_MS]，思考词轮播 + 图标旋转）→
 * 单一 progress（[REVEAL_MS]）驱动三段结论按时间窗错峰舒展（涨势 [0,0.3]、风险 [0.3,0.6]、买入 [0.6,1]）。
 * 🔴 不用多路 animateFloatAsState 级联步进——实测会在 Kuikly 上卡死在半路；单 progress 是验证过的可靠模式。
 */
@Composable
fun AiBottomSheet(analysis: AiAnalysis, stock: StockItem?, onDismiss: () -> Unit, onViewReport: () -> Unit) {
    var thinking by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(THINKING_MS)
        thinking = false
    }
    // 整体内容淡入（弹层滑上来时内容不生硬）
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(320),
    )
    // 单 progress 驱动三段错峰舒展：涨势 [0,0.3] / 风险 [0.3,0.6] / 买入 [0.6,1]
    val progress by animateFloatAsState(
        targetValue = if (thinking) 0f else 1f,
        animationSpec = tween(REVEAL_MS),
    )
    val trendP = seg(progress, 0f)
    val riskP = seg(progress, 0.3f)
    val buyP = seg(progress, 0.6f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).alpha(enterAlpha)) {
        // handle
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp).background(AppColors.HandleGray, AppShapes.Handle).align(Alignment.CenterHorizontally))
        // header
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiIconBadge(size = AiIconSize.Medium)
                Text(text = "智能分析", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = AppSpacing.Sm))
                Spacer(modifier = Modifier.width(AppSpacing.Sm))
                Box(modifier = Modifier.background(AppColors.AiBadgeBg, AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(text = "Power by Ai模型", color = AppColors.RiskText, fontSize = AppTypography.Tiny)
                }
                Spacer(modifier = Modifier.width(6.dp))
                AiConfidenceBadge(analysis)
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
        if (thinking) {
            // 「AI 思考中」阶段：思考词轮播 + 图标旋转
            AiThinking()
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            ExpandFraction(progress = trendP, modifier = Modifier.fillMaxWidth().alpha(trendP)) {
                TrendSection(analysis, stock, shown = true)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ExpandFraction(progress = riskP, modifier = Modifier.fillMaxWidth().alpha(riskP)) {
                RiskSection(analysis, stock, shown = true)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ExpandFraction(progress = buyP, modifier = Modifier.fillMaxWidth().alpha(buyP)) {
                BuySection(analysis, stock, shown = true)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // AI 推理链（可折叠）：让结论可解释
        if (!thinking && stock != null) {
            AiReasoning(stock, analysis)
            Spacer(modifier = Modifier.height(12.dp))
        }
        // CTA
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(AppColors.CtaBg, RoundedCornerShape(24.dp)).clickable(onClick = onViewReport).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "查看完整报告", color = Color.White, fontSize = AppTypography.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            AppIcon("arrow-right", modifier = Modifier.size(16.dp))
        }
    }
}
