package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.components.AppIcon
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 圆形图标按钮：浅色圆底 + 居中图标，用于弹层关闭、操作入口等轻量图标交互。
 * 图标由 [com.example.task1.components.AppIcon] 按资源名渲染。
 *
 * @param icon 图标资源名（如 "x-circle"）
 * @param onClick 点击回调
 */
@Composable
fun IconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(AppColors.PageBg, AppShapes.Pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, modifier = Modifier.size(14.dp))
    }
}
