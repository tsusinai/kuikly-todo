package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.base.Utils
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.animation.shrinkVertically
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

/**
 * 股票卡片。全卡统一行为（无 aiEnabled 区分）：单击在页面层被接成「选中」。
 *
 * [selected] 为 true 时：卡片边框高亮，并在 TopRow 下方用 AnimatedVisibility(expandVertically)
 * 平滑展开详情块（aiBrief 推介 + 高/低/开 + 查看详情 ›）。否则仅呈现紧凑态（与原一致）。
 */
@Composable
fun StockCard(
    item: StockItem,
    selected: Boolean,
    onOpenAi: () -> Unit,
    onEnterDetail: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp) // 卡片距屏幕边缘 10dp，与设计稿、沪深大盘摘要区对齐
            .padding(vertical = 5.dp)
            .background(Color.White, cardShape)
            .border(1.dp, if (selected) AppColors.AiLight else AppColors.Border, cardShape)
            .padding(12.dp)
    ) {
        TopRow(item)
        AnimatedVisibility(
            visible = selected,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            DetailBlock(item, onOpenAi, onEnterDetail)
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

/** 选中时展开的详情块：AI 推介 + 高/低/开 + 查看详情 ›。 */
@Composable
private fun DetailBlock(item: StockItem, onOpenAi: () -> Unit, onEnterDetail: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (item.aiBrief.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            AiBriefRow(item, onOpenAi)
        }
        Spacer(modifier = Modifier.height(10.dp))
        HighLowOpenRow(item)
        Spacer(modifier = Modifier.height(10.dp))
        DetailEntry(onEnterDetail)
    }
}

@Composable
private fun AiBriefRow(item: StockItem, onOpenAi: () -> Unit) {
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

@Composable
private fun HighLowOpenRow(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "高 ${Utils.formatPrice2(item.high)}", color = AppColors.RiseRed, fontSize = 12.sp)
        Text(text = "低 ${Utils.formatPrice2(item.low)}", color = AppColors.Green, fontSize = 12.sp)
        Text(text = "开 ${Utils.formatPrice2(item.open)}", color = AppColors.RiseRed, fontSize = 12.sp)
    }
}

@Composable
private fun DetailEntry(onEnterDetail: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnterDetail)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "查看详情", color = AppColors.MainText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(4.dp))
        AppIcon("arrow-right", modifier = Modifier.size(14.dp))
    }
}
