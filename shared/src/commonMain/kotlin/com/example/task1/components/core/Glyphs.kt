package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 返回箭头(左向 chevron),Canvas 手绘。
 *
 * 刻意不复用 `assets/common/arrow-right.png`:那是一枚**白色**字形,是给深色/蓝色底用的
 * (如 AI 弹层里「查看完整报告」的蓝底按钮)。放到浅色圆底上就是白压白——详情页与报告页的
 * 返回键此前正是这个状态,看起来像「图标缺失」。手绘后颜色由 [color] 决定,与底色解耦。
 */
@Composable
fun ChevronBack(
    modifier: Modifier = Modifier,
    color: Color = AppColors.MainText,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        val elbow = Offset(size.width * 0.36f, size.height * 0.5f)
        drawLine(color, Offset(size.width * 0.68f, half), elbow, stroke, StrokeCap.Round)
        drawLine(color, elbow, Offset(size.width * 0.68f, size.height - half), stroke, StrokeCap.Round)
    }
}

/**
 * 下向 chevron（折叠区开关箭头），Canvas 手绘。
 *
 * 与 [ChevronBack] 同一考虑：不依赖字体字符（`▾`/`▴` 在不同字体下大小与基线都不受控），
 * 颜色由 [color] 决定，且能被外层 `rotate()` 连续旋转——收起指下、展开指上，中间态是转过去的。
 */
@Composable
fun ChevronDown(
    modifier: Modifier = Modifier,
    color: Color = AppColors.SubGray,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        val elbow = Offset(size.width * 0.5f, size.height * 0.66f)
        drawLine(color, Offset(half, size.height * 0.34f), elbow, stroke, StrokeCap.Round)
        drawLine(color, elbow, Offset(size.width - half, size.height * 0.34f), stroke, StrokeCap.Round)
    }
}

/**
 * 勾选图形（选中态），Canvas 手绘。
 *
 * 与 [ChevronBack] 同一考虑：不依赖字体字符 —— `✓` 在不同字体下粗细、大小、基线都不受控，
 * 而且颜色不能跟着主题走。这里由 [color] 决定，尺寸由外层 modifier 决定。
 */
@Composable
fun CheckMark(
    modifier: Modifier = Modifier,
    color: Color = AppColors.CtaBg,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        val elbow = Offset(size.width * 0.40f, size.height - half)
        drawLine(color, Offset(half, size.height * 0.52f), elbow, stroke, StrokeCap.Round)
        drawLine(color, elbow, Offset(size.width - half, half), stroke, StrokeCap.Round)
    }
}
