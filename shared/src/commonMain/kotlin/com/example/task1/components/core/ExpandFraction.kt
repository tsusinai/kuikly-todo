package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.layout.Layout
import com.tencent.kuikly.compose.ui.unit.Constraints

/**
 * 变高内容的平滑展开/收起容器（Kuikly 专用通用原语）。
 *
 * ## 为什么需要它
 *
 * Kuikly Compose 上 `animateContentSize` 不存在、`AnimatedVisibility`/`AnimatedContent`
 * 的过渡动画实测完全不动（详见项目记忆「Kuikly 动画铁律」）。本组件用 Kuikly 底层
 * `Layout(content, modifier, measurePolicy)` 实现可靠的变高展开：
 *
 *  1. 测量子内容时把 maxHeight 放宽到 [Constraints.Infinity]，拿到内容**自然高度** naturalH；
 *  2. 向父级报告的高度 = naturalH × [progress]，子内容顶部对齐原位放置，
 *     超出部分由 `clipToBounds` 裁掉 → 视觉上「从 0 平滑展开到自然高度」。
 *
 * [progress] 变化 → 重组 → 逐帧重新 measure/layout（与已验证可靠的值参数修饰符同机制，
 * 参考 `alpha(x)` / `rotate(x)`）。content 行数/高度可变，测量每帧跟随，**无固定高度预算、
 * 无行数限制**——文案变长也能完整平滑展开。
 *
 * ## 用法
 *
 * ```
 * var expanded by remember { mutableStateOf(false) }
 * val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(500))
 * ExpandFraction(progress = progress) { AnyContent() }   // 任意变高内容
 * ```
 *
 * @param progress 展开进度 0f（完全收起）..1f（完全展开）
 * @param modifier 应用到容器的修饰符（建议叠加 `alpha(progress)` 做淡入淡出）
 * @param content 任意内容；按自然高度测量，超出当前进度的部分被裁剪
 */
@Composable
fun ExpandFraction(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val h = (placeable.height * progress).toInt().coerceAtLeast(0)
        layout(constraints.maxWidth, h) {
            placeable.placeRelative(0, 0)
        }
    }
}

/**
 * 带完成态直通的展开容器（推荐入口）。
 *
 * progress < 1 时走 [ExpandFraction] 裁剪展开；**progress >= 1 时直接原生布局**——
 * 因为展开动画测量的自然高度可能早于字体度量稳定（Kuikly 首帧文本度量偏小），
 * 若完成态仍钳在旧测量值上，文本真正排版后会被永久裁掉半行。
 * 完成态跳出裁剪后，内容后续变高也由普通布局自适应。
 */
@Composable
fun ExpandableReveal(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (progress >= 1f) {
        Box(modifier = modifier) { content() }
    } else {
        ExpandFraction(
            progress = progress,
            modifier = modifier.alpha(progress.coerceIn(0f, 1f)),
            content = content,
        )
    }
}
