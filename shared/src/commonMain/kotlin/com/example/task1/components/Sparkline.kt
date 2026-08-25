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
 *
 * [progress] 表示曲线完成的百分比（0f..1f）。默认 [progress] = 1f 时绘制完整曲线（不改变原有渲染）；
 * 当 0f <= [progress] < 1f 时，将两条 cubicTo 贝塞尔段各采样为密集折线，并按总长度比例截断路径。
 */
@Composable
fun RiseSparkline(modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            val padX = w * 0.05f
            val top = h * 0.14f
            val bottom = h * 0.88f
            // y(t)：t 从 0(顶部) 到 1(底部) 的线性插值
            fun y(t: Float) = top + (bottom - top) * t

            val path: Path = if (progress >= 1f) {
                Path().apply {
                    moveTo(padX, y(1f))
                    // 第一段：从左下缓升
                    cubicTo(w * 0.26f, y(1f), w * 0.34f, y(0.74f), w * 0.46f, y(0.62f))
                    // 第二段：快速拉升到右上方
                    cubicTo(w * 0.60f, y(0.50f), w * 0.74f, y(0.12f), w - padX, y(0.30f))
                }
            } else {
                val p = progress.coerceIn(0f, 1f)

                data class Pt(val x: Float, val y: Float)
                // 三次贝塞尔采样：B(t) = u^3*p0 + 3u^2 t*c1 + 3u t^2*c2 + t^3*p3
                fun cubic(p0: Pt, c1: Pt, c2: Pt, p3: Pt, t: Float): Pt {
                    val u = 1f - t
                    val x = u * u * u * p0.x + 3f * u * u * t * c1.x + 3f * u * t * t * c2.x + t * t * t * p3.x
                    val y = u * u * u * p0.y + 3f * u * u * t * c1.y + 3f * u * t * t * c2.y + t * t * t * p3.y
                    return Pt(x, y)
                }

                data class Seg(val p0: Pt, val c1: Pt, val c2: Pt, val p3: Pt)
                val segA = Seg(Pt(padX, y(1f)), Pt(w * 0.26f, y(1f)), Pt(w * 0.34f, y(0.74f)), Pt(w * 0.46f, y(0.62f)))
                val segB = Seg(Pt(w * 0.46f, y(0.62f)), Pt(w * 0.60f, y(0.50f)), Pt(w * 0.74f, y(0.12f)), Pt(w - padX, y(0.30f)))

                // 每段采样约 64 个点，按序拼接；段间共享的端点只保留一次，避免重叠点。
                val samplesPerSeg = 64
                val points = mutableListOf<Pt>()
                for (i in 0..samplesPerSeg) {
                    val t = i.toFloat() / samplesPerSeg
                    points.add(cubic(segA.p0, segA.c1, segA.c2, segA.p3, t))
                }
                for (i in 1..samplesPerSeg) {
                    val t = i.toFloat() / samplesPerSeg
                    points.add(cubic(segB.p0, segB.c1, segB.c2, segB.p3, t))
                }

                val totalSamples = points.size
                val keep = (p * totalSamples).toInt().coerceIn(0, totalSamples)
                val truncated = Path()
                if (keep >= 1) {
                    truncated.moveTo(points[0].x, points[0].y)
                    for (i in 1 until keep) {
                        truncated.lineTo(points[i].x, points[i].y)
                    }
                }
                truncated
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
