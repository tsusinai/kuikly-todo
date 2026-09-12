package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.data.ChartMetric
import com.example.task1.data.ChartPeriod
import com.example.task1.data.ChartStyle
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 周期维度分段：位于图表上方。选中值是页面状态（切周期要重新取数），所以由调用方持有。
 *
 * 未选中项保留同高度占位条，切换时不会引起文字跳动。
 */
@Composable
fun ChartPeriodTabs(
    current: ChartPeriod,
    onSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartPeriod.values().forEach { period ->
            val selected = period == current
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(period) }) {
                Text(
                    text = period.label,
                    color = if (selected) AppColors.MainText else AppColors.SubGray,
                    fontSize = AppTypography.Body,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 2.dp)
                        .background(if (selected) AppColors.MainText else Color.Transparent, AppShapes.Handle),
                )
            }
        }
    }
}

/**
 * 样式 + 指标维度 chips：位于图表上方。
 *
 * 同一行里两组并排：左边是互斥的样式选择（蜡烛/线图/面积），右边是可独立开关的指标（成交量/均线）——
 * 两组之间留出空档，指标开关会让副图栏位展开/收起，切换时看得清是谁在动。
 * 分时周期没有蜡烛概念，用 [styleEnabled] 置灰样式入口。
 */
@Composable
fun ChartStyleChips(
    style: ChartStyle,
    onStyleChange: (ChartStyle) -> Unit,
    metrics: Set<ChartMetric>,
    onToggleMetric: (ChartMetric) -> Unit,
    styleEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartStyle.values().forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.width(6.dp))
                ChartChip(
                    label = item.label,
                    selected = item == style,
                    enabled = styleEnabled,
                    onClick = { onStyleChange(item) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartMetric.values().forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.width(6.dp))
                ChartChip(
                    label = item.label,
                    selected = item in metrics,
                    onClick = { onToggleMetric(item) },
                )
            }
        }
    }
}

/** 维度 chip：选中用 HeaderBg 底 + 主色加粗字，disabled 只降透明度不隐藏，保持布局稳定。 */
@Composable
private fun ChartChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val highlighted = selected && enabled
    Box(
        modifier = Modifier
            .background(if (highlighted) AppColors.HeaderBg else Color.Transparent, AppShapes.Badge)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> AppColors.SubGray.copy(alpha = 0.4f)
                highlighted -> AppColors.MainText
                else -> AppColors.SubGray
            },
            fontSize = AppTypography.Tiny,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
