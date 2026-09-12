package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 标签胶囊：白底 + AI 浅色细边框的小圆角标签，用于股票卡片上的标签行（如「量比异动」「放量上攻」）。
 * 文字使用 [AppTypography.Tiny] 小字号，视觉克制、不抢主体信息。
 *
 * @param text 标签文本
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.White, AppShapes.Badge)
            .border(1.dp, AppColors.AiLight, AppShapes.Badge)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = AppColors.MainText, fontSize = AppTypography.Tiny)
    }
}
