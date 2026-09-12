package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.data.GroupDimension
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
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 分组维度选择弹层：列出全部 [GroupDimension]，当前项高亮并打勾，选中即回调关闭。
 * 由「分析智窗」长按呼出，用于切换自选列表的分组维度。
 *
 * @param current 当前选中维度
 * @param onSelect 选中某项的回调（传出维度）
 */
@Composable
fun DimensionPickerSheet(current: GroupDimension, onSelect: (GroupDimension) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp).background(AppColors.HandleGray, AppShapes.Handle).align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "选择分组维度", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        GroupDimension.values().forEach { dim ->
            val selected = dim == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(if (selected) AppColors.HeaderBg else AppColors.PageBg, AppShapes.Card)
                    .clickable { onSelect(dim) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = dim.label, color = AppColors.MainText, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (selected) {
                    Text(text = "✓", color = AppColors.RiseRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
