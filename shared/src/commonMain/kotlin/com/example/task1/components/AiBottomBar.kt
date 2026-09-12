package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.core.ExpandFraction
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppSpacing
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 收起态右侧功能提示（轮播展示）。 */
val AiBarHints = listOf(
    "点击展开全盘分析 ▾",
    "长按股票展开AI分析",
    "长按智窗切换分析维度",
)

/**
 * 「分析智窗」标准组件：底部常驻 AI 建议条，点击展开/收起全盘分析。
 *
 * ## 交互
 *  - 思考态（[thinking]=true）：icon + 标题 + 思考词轮播（[ThinkingPhrases]）
 *  - 收起态：icon + 标题 + 右侧功能提示滚动栏（3 条引导每 3 秒轮换）
 *  - 展开态：icon 旋转一圈 → 头部行向上收起 → 全盘分析按**内容自然高度**平滑舒展
 *    （[ExpandFraction] 变高展开原语）；关闭为逆过程（icon 反向转回顾位）
 *  - 单击整卡切换展开/收起；长按整卡触发 [onLongPress]（用于呼出维度选择）
 *
 * ## 状态模式
 *  - **非受控**（默认）：不传 [expanded]，组件自管理展开态，页面重启后收起。
 *  - **受控**：传入 [expanded]，状态由调用方持有，每次切换回调 [onExpandedChange]。
 *
 * ## 用法
 * ```
 * // 非受控（推荐，一行接入）
 * AiBottomBar(advice, thinking, dimensionLabel, onLongPress = { showDimPicker = true })
 *
 * // 受控（需要联动其他 UI 时）
 * var expanded by remember { mutableStateOf(false) }
 * AiBottomBar(advice, thinking, dimensionLabel, expanded = expanded,
 *             onExpandedChange = { expanded = it })
 * ```
 *
 * 悬浮于列表上方展示（调用方通过 Box `align(BottomCenter)` 摆放）。
 *
 * @param advice 全盘建议文案（思考结束后展示；变高，自动平滑展开）
 * @param thinking 是否思考中（数据加载/AI 生成期间传 true）
 * @param dimensionLabel 当前分析维度名（展示于「全盘AI建议·xx」标题）
 * @param expanded 受控展开态；null = 非受控（组件自管理）
 * @param onExpandedChange 展开态变化回调（展开=true / 收起=false）
 * @param onLongPress 长按整卡回调
 */
@Composable
fun AiBottomBar(
    advice: String,
    thinking: Boolean,
    dimensionLabel: String,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    // 非受控内部状态（受控模式忽略）
    var internalExpanded by remember { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val toggleExpand = {
        val next = !isExpanded
        if (expanded == null) internalExpanded = next
        onExpandedChange(next)
    }

    // 思考词轮播索引（仅 thinking 态使用）
    var phraseIndex by remember { mutableStateOf(0) }
    LaunchedEffect(thinking) {
        if (thinking) {
            while (true) {
                delay(700)
                phraseIndex = (phraseIndex + 1) % ThinkingPhrases.size
            }
        }
    }

    // 收起态右侧功能提示轮播：每 3 秒淡出→换条→淡入。
    // 不用 AnimatedContent（其过渡依赖 fadeIn，Kuikly 上 layerBlock 缺失会导致文字消失）。
    var hintIndex by remember { mutableStateOf(0) }
    var hintFading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            hintFading = true          // alpha → 0（tween 200）
            delay(250)
            hintIndex = (hintIndex + 1) % AiBarHints.size
            hintFading = false         // alpha → 1
        }
    }
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintFading) 0f else 1f,
        animationSpec = tween(200),
    )

    // 展开动画链（🔴 纯值驱动，单 progress 控制全部；不用 AnimatedVisibility——
    // 其 enter/exit 在 Kuikly 上实测完全不动，无论 fade 还是尺寸动画）：
    // progress 0→1（tween 500）同时驱动：icon rotate(p*360) 转一圈、
    // 头部行 ExpandFraction(1-p) 上收、全盘分析 ExpandFraction(p) 按自然高度舒展。
    // 关闭逆过程：progress 1→0 全部反向。
    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(500),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Sm)
            .shadow(6.dp, AppShapes.Sheet, clip = false)
            .background(Color.White, AppShapes.Sheet)
            .pointerInput(onLongPress, toggleExpand) {
                detectTapGestures(
                    onTap = { toggleExpand() },
                    onLongPress = { onLongPress() },
                )
            }
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
    ) {
        if (thinking) {
            // 思考态：logo + 标题 + 思考词轮播
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiIconBadge(size = AiIconSize.Medium)
                Text(text = "分析智窗", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = AppSpacing.Sm))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "${ThinkingPhrases[phraseIndex]}...", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            }
        } else {
            // 头部行：随 progress 上收（自然高→0 的裁剪收缩 =「向上收起」视觉）+ 淡出
            ExpandFraction(
                progress = 1f - progress,
                modifier = Modifier.fillMaxWidth().alpha(1f - progress),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.rotate(progress * 360f)) {
                        AiIconBadge(size = AiIconSize.Medium)
                    }
                    Text(text = "分析智窗", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = AppSpacing.Sm))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = AiBarHints[hintIndex],
                        color = AppColors.SubGray,
                        fontSize = AppTypography.BodySmall,
                        maxLines = 1,
                        modifier = Modifier.width(150.dp).alpha(hintAlpha),
                    )
                }
            }
            // 全盘分析：随 progress 舒展——按内容【自然高度】平滑展开（变高内容 OK，不限行数）
            ExpandFraction(
                progress = progress,
                modifier = Modifier.fillMaxWidth().alpha(progress),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "全盘AI建议·$dimensionLabel", color = AppColors.MainText, fontSize = AppTypography.Caption)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = advice, color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
                }
            }
        }
    }
}
