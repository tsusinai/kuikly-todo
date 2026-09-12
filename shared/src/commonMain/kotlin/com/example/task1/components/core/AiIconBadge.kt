package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.components.AppIcon
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp

/** AI 徽章三档尺寸：Small(18/12) / Medium(24/16) / Large(28/20)，单位为 dp。 */
enum class AiIconSize { Small, Medium, Large }

/**
 * AI「sparkles」圆角徽章 —— 统一替代各处手写的「圆角底 + sparkles 图标」。
 * [size] 控制徽章与图标尺寸；[background] 覆盖底色（默认 AiLight）；[onClick] 非空时整块可点。
 */
@Composable
fun AiIconBadge(
    modifier: Modifier = Modifier,
    size: AiIconSize = AiIconSize.Medium,
    background: Color = AppColors.AiLight,
    onClick: (() -> Unit)? = null,
) {
    val (boxSize, iconSize) = when (size) {
        AiIconSize.Small -> 18.dp to 12.dp
        AiIconSize.Medium -> 24.dp to 16.dp
        AiIconSize.Large -> 28.dp to 20.dp
    }
    Box(
        modifier = modifier
            .size(boxSize)
            .background(background, AppShapes.Pill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon("sparkles", modifier = Modifier.size(iconSize))
    }
}
