package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.StrokeJoin
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke

/**
 * 共用「涨势」红色 sparkline：用 Kuikly 原生 Canvas 画一条平滑上升的红色曲线。
 *
 * 尺寸完全由调用方传入的 [modifier] 决定（卡片弹层用 height(48.dp)，底部智窗用 weight(1f).height(28.dp)），
 * 内部 padX/top/bottom 按画布 w/h 比例计算。经 Artifact 实测：Canvas + Path + cubicTo + drawPath(红 Stroke) 渲染稳定。
 */
@Composable
fun RiseSparkline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            val padX = w * 0.05f
            val top = h * 0.14f
            val bottom = h * 0.88f
            // y(t)：t 从 0(顶部) 到 1(底部) 的线性插值
            fun y(t: Float) = top + (bottom - top) * t

            val path = Path().apply {
                moveTo(padX, y(1f))
                // 第一段：从左下缓升
                cubicTo(w * 0.26f, y(1f), w * 0.34f, y(0.74f), w * 0.46f, y(0.62f))
                // 第二段：快速拉升到右上方
                cubicTo(w * 0.60f, y(0.50f), w * 0.74f, y(0.12f), w - padX, y(0.30f))
            }
            drawPath(
                brush = Brush.linearGradient(listOf(AppColors.RiseRed, AppColors.RiseRed)),   // 红色 #DF0004
                path = path,
                style = Stroke(
                    width = (h * 0.045f).coerceIn(2f, 4f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
