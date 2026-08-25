package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun MarketOverviewBar() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 15.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "+23.45亿", color = AppColors.RiseRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "已收盘", color = AppColors.MainText, fontSize = 13.sp)
        }
        Text(text = "2026-12-12 星期二", color = AppColors.MainText, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "涨23", color = AppColors.RiseRed, fontSize = 13.sp)
            Text(text = "跌12", color = AppColors.Green, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 涨23(红) / 跌12(绿)：左红右绿，与设计稿进度条一致
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(AppColors.Green, RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(0.68f).height(8.dp)
                .background(AppColors.RiseRed, RoundedCornerShape(4.dp)))
        }
    }
}
