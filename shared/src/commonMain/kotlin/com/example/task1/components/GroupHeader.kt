package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.data.StockGroup
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/** 分组标题 → 语义图标（zip 图标集，主题靛蓝双色）；未匹配分组返回 null（回退 AI 徽章）。 */
private fun groupIcon(title: String): String? = when {
    title.contains("重点关注") -> "ic-group-focus"
    title.contains("低吸") -> "ic-group-dip"
    title.contains("观望") -> "ic-group-hold"
    title.contains("回避") -> "ic-group-avoid"
    else -> null
}
/**
 * 分组标题：左侧「AI 徽章 + 分组名」、右侧该组股票数量角标。
 * 用于自选列表按维度分组后各组的组头。
 *
 * @param group 分组数据（含标题与股票列表）
 */
@Composable
fun GroupHeader(group: StockGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = groupIcon(group.title)
            if (icon != null) {
                AppIcon(icon, modifier = Modifier.size(18.dp))
            } else {
                AiIconBadge(size = AiIconSize.Small)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = group.title, color = AppColors.MainText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.background(AppColors.AiBadgeBg, AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text = "${group.stocks.size}", color = AppColors.RiskText, fontSize = AppTypography.Caption, fontWeight = FontWeight.SemiBold)
        }
    }
}
