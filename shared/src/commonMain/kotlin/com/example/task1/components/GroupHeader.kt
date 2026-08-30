package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.data.StockGroup
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun GroupHeader(group: StockGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).background(AppColors.AiLight, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                AppIcon("sparkles", modifier = Modifier.size(12.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = group.title, color = AppColors.MainText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.background(AppColors.AiBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text = "${group.stocks.size}", color = AppColors.RiskText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
