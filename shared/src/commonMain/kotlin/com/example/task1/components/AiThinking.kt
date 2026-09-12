package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.draw.scale
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * AI 思考词列表：在「分析中」阶段轮播，强化「AI 正在工作」的感知。
 * 顶层常量供 [AiThinking] 与 [com.example.task1.components.AiBottomBar] 共用，保证两处文案一致。
 */
val ThinkingPhrases = listOf(
    "分析行情数据",
    "匹配个股画像",
    "评估风险因素",
    "生成操作建议",
)

/**
 * 可复用的「AI 思考中」组件：sparkles 徽章持续旋转 + 轻微脉动 + 思考词轮播。
 *
 * 动画全部走「值参数修饰符 + animateFloatAsState」可靠路线（Kuikly 动画铁律）：
 *  - 旋转：每 350ms 目标角 +90°，tween(350) 恰好衔接 → 视觉上连续匀速旋转；
 *  - 脉动：scale 值参数往复（graphicsLayer{lambda} 形式不逐帧执行，勿用）。
 */
@Composable
fun AiThinking(
    modifier: Modifier = Modifier,
    withBadge: Boolean = true,
) {
    var pulse by remember { mutableStateOf(false) }
    var phraseIndex by remember { mutableStateOf(0) }
    var spinStep by remember { mutableStateOf(0) }

    // 徽章脉动：false↔true 往复驱动 scale
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            delay(600)
        }
    }
    // 思考词轮播：每 700ms 切换
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            phraseIndex = (phraseIndex + 1) % ThinkingPhrases.size
        }
    }
    // 持续旋转：每 350ms 步进 90°，tween(350) 无缝衔接 = 连续旋转
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            spinStep++
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pulse) 1.08f else 0.92f,
        animationSpec = tween(600),
    )
    val rotation by animateFloatAsState(
        targetValue = spinStep * 90f,
        animationSpec = tween(350),
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (withBadge) {
            Box(modifier = Modifier.rotate(rotation).scale(scale)) {
                AiIconBadge(size = AiIconSize.Large)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = ThinkingPhrases[phraseIndex],
            color = AppColors.SubGray,
            fontSize = AppTypography.BodySmall,
        )
    }
}
