package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.components.AppIcon
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 区块标题：左侧「可选图标 + 标题」、右侧可选说明文字，两端对齐。
 * 用于 AI 分析各 section（涨势分析 / 风险评估 / 买入建议）的统一标题样式。
 *
 * @param title 标题文本
 * @param icon 可选图标资源名（如 "trending-up"）
 * @param rightText 可选右侧说明文字（如趋势标签、风险等级）
 * @param rightTextColor 右侧文字颜色（默认主文字色）
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    rightText: String? = null,
    rightTextColor: Color = AppColors.MainText,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                AppIcon(it, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = title, color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.SemiBold)
        }
        rightText?.let {
            // 沉底对齐：小字标签与 H3 标题基线一致，不再悬在半空
            Text(
                text = it,
                color = rightTextColor,
                fontSize = AppTypography.BodySmall,
                modifier = Modifier.align(Alignment.Bottom),
            )
        }
    }
}
