package com.example.task1.components.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 相邻两项的入场间隔、总错峰上限、单项时长。 */
/**
 * 相邻两项的入场间隔、错峰总上限、单项时长。
 *
 * 这三个值分工不同：单项时长负责「不突兀」，错峰间隔负责「有级联」。
 * 想更快时应该优先压 [STAGGER_MAX_MS]（尾部项不必等太久），[STAGGER_STEP_MS] 要留够——
 * 把间隔压到 20ms 级、时长压到 200ms 级，级联就看不出来了，整屏会读成「啪一下全蹦出来」。
 */
private const val STAGGER_STEP_MS = 40
private const val STAGGER_MAX_MS = 300
private const val APPEAR_MS = 300

/**
 * 入场原语：内容首次组合后从「透明 + 下移 [offsetY]」过渡到原位，第 [index] 项按
 * [STAGGER_STEP_MS] 递延（封顶 [STAGGER_MAX_MS]），一批内容因此错峰涌现而不是同时蹦出来。
 *
 * 两处刻意的取舍：
 *  - 动画值走**值参数**（`alpha` / `offset(y = Dp)`），不用在 `graphicsLayer{}` lambda 里读动画状态 ——
 *    后者是项目里实测过会「假死」（值停在首帧）的写法，值参数则每帧都跟着重组走；
 *  - 只做透明度和位移，**不做尺寸动画**：入场不该改变列表布局，否则相邻项会跟着一起抖。
 *
 * LazyColumn 里条目滚出可视区被回收、再滚回来时会重新入场一次。短列表观感正常（也算「滚动显现」），
 * 长列表若嫌吵，就不要逐项包，改成只给分组块包一层。
 */
@Composable
fun AppearIn(
    index: Int = 0,
    modifier: Modifier = Modifier,
    offsetY: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val delayMs = (index * STAGGER_STEP_MS).coerceAtMost(STAGGER_MAX_MS)
        if (delayMs > 0) delay(delayMs.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = APPEAR_MS, easing = FastOutSlowInEasing),
    )
    Box(modifier = modifier.alpha(progress).offset(y = offsetY * (1f - progress))) {
        content()
    }
}
