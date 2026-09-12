package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 涨跌徽章：浅色底 + 加粗百分比/数值文本，用于标注涨跌幅等关键指标。
 * 默认底色为浅绿（[AppColors.RiseBadgeBg]）、文字红（[AppColors.RiseRed]）；
 * 调用方按涨跌方向自定 [color]（涨红/跌绿）。
 *
 * @param text 徽章文本（通常为 `+1.23%` 这类带符号值）
 * @param color 文字颜色，默认 [AppColors.RiseRed]
 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.RiseRed,
    bgColor: Color = AppColors.RiseBadgeBg,
) {
    Box(
        modifier = modifier
            .background(bgColor, AppShapes.Badge)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, fontSize = AppTypography.Caption, fontWeight = FontWeight.Bold)
    }
}
