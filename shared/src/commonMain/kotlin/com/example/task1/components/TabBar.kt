package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private val Tabs = listOf("自选", "全球", "港股", "期贷", "A股", "美股", "黄金")

@Composable
fun TabBar(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), beyondBoundsItemCount = 3) {
        items(Tabs, key = { it }) { tab ->
            val sel = tab == selected
            Text(
                text = tab,
                color = if (sel) AppColors.MainText else AppColors.SubGray,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (sel) 19.sp else 18.sp,   // 选中 sp+1
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}
