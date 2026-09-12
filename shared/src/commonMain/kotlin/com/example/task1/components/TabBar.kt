package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.animation.core.animateDpAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private val Tabs = listOf("自选", "全球", "港股", "期贷", "A股", "美股", "黄金")

/**
 * 顶部市场分类 Tab 栏：横向可滚动的文字标签，选中态加粗并放大 1sp，
 * 下方带「动态装饰线」——短圆角横线随选中项切换，宽度 0→20dp 动画出现/收回。
 * 数据源为固定的 [Tabs] 列表，按 [selected] 高亮当前项。
 *
 * @param selected 当前选中的 Tab 名
 * @param onSelect 点击某项的回调（传出 Tab 名）
 */
@Composable
fun TabBar(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), beyondBoundsItemCount = 3) {
        items(Tabs, key = { it }) { tab ->
            val sel = tab == selected
            Column(
                modifier = Modifier.clickable { onSelect(tab) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tab,
                    color = if (sel) AppColors.MainText else AppColors.SubGray,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (sel) 19.sp else 18.sp,   // 选中 sp+1
                )
                Spacer(modifier = Modifier.height(3.dp))
                // 动态装饰线：选中项下方短圆角横线，宽度动画切换（0=隐藏）
                val lineW by animateDpAsState(
                    targetValue = if (sel) 20.dp else 0.dp,
                    animationSpec = tween(180),
                )
                Box(
                    modifier = Modifier
                        .width(lineW)
                        .height(3.dp)
                        .background(AppColors.MainText, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
