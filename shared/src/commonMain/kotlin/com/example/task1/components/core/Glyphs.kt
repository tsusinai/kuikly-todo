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
