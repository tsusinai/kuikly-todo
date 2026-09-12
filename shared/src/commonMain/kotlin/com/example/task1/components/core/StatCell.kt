package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 统计单元格（横排）：`标签 + 数值` 同行展示，用于卡片内「高/低/开」这类指标。
 * 标签用浅色 Light 字重，数值用 Bold 字重并支持自定义颜色（涨红/跌绿）。
 *
 * @param label 指标名（如「高」「低」）
 * @param value 指标值（已格式化的文本）
 * @param valueColor 数值颜色，默认主文字色
 */
@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColors.MainText,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = AppColors.MainText, fontSize = AppTypography.Body, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, color = valueColor, fontSize = AppTypography.Body, fontWeight = FontWeight.Bold)
    }
}
