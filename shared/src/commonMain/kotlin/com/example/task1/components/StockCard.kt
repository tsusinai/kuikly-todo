package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.base.Utils
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
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
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun StockCard(item: StockItem, onOpenAi: () -> Unit) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp) // 卡片距屏幕边缘 10dp，与设计稿、沪深大盘摘要区对齐
            .padding(vertical = 5.dp)
            .background(Color.White, cardShape)
            .border(1.dp, AppColors.Border, cardShape)
            .padding(12.dp)
    ) {
        TopRow(item)
        if (item.aiEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            AiRow(item, onOpenAi)
        }
    }
}

@Composable
private fun TopRow(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.name, color = AppColors.MainText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = Utils.formatPrice2(item.price), color = AppColors.RiseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            // 涨跌额（带符号），如 +43.62；涨红跌绿
            Text(
                text = Utils.formatSignedPrice2(item.change),
                color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChangeBadge(item.changePct)
        }
    }
}

@Composable
private fun ChangeBadge(changePct: Double) {
    Box(
        modifier = Modifier
            .background(AppColors.RiseBadgeBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = Utils.formatPercent(changePct),
            color = if (changePct >= 0) AppColors.RiseRed else AppColors.Green,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AiRow(item: StockItem, onOpenAi: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAi),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(AppColors.AiLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon("sparkles", modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.aiBrief, color = AppColors.Green, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
