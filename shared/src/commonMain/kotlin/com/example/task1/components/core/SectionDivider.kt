package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 区块分隔线：横向 1dp 的浅色细线，用于 section 之间或卡片内部的分隔。
 * 与 [com.example.task1.components.core.SectionHeader] 搭配使用构成完整「区块」视觉。
 */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
}
