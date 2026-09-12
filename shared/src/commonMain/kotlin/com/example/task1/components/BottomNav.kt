package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/** 导航项：文字 → 图标资源名（双色 PNG，选中 -active（墨黑+主题靛蓝）/ 未选中 -gray 变体）。 */
private val NavItems = listOf(
    "行情" to "ic-nav-hangqing",
    "分析" to "ic-nav-fenxi",
    "我的" to "ic-nav-wode",
)

/**
 * 底部导航栏：行情 / 分析 / 我的 三个入口，均匀分布。
 * 图标使用 zip 图标集双色设计（墨黑+品牌红 / 灰+浅灰），选中态红版图标 + 红色加粗文字，
 * 未选中灰版图标 + 灰色文字。当前点击无动作（占位）。
 *
 * @param selected 当前选中的项名（默认「行情」）
 */
@Composable
fun BottomNav(selected: String = "行情") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItems.forEach { (label, iconBase) ->
            val sel = label == selected
            Column(
                modifier = Modifier.clickable { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 图标位（22dp 槽位对齐设计）：双色 PNG，选中 -active / 未选中 -gray
                AppIcon(
                    name = if (sel) "$iconBase-active" else "$iconBase-gray",
                    modifier = Modifier.size(width = 24.dp, height = 22.dp),
                )
                Text(
                    text = label,
                    color = if (sel) AppColors.RiseRed else AppColors.SubGray,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
