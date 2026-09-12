package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 卡片容器：统一的白底圆角卡片外观（阴影 + 边框 + 内边距）。
 * 卡片列表中「选中/未选中」的视觉差异由此组件承载：
 * 选中态加 2dp 阴影 + AI 浅色边框，未选中态为常规 1dp 灰色边框。
 *
 * @param selected 是否选中（高亮边框 + 阴影）
 * @param onClick 非空时整卡可点击（可选，便于卡片整体接点击事件）
 * @param content 卡片内容
 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = AppShapes.Card
    Column(
        modifier = modifier
            .shadow(if (selected) 2.dp else 0.dp, shape, clip = false)
            .background(Color.White, shape)
            .border(1.dp, if (selected) AppColors.AiLight else AppColors.Border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        content()
    }
}
