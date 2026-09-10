package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.core.Spring
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.spring
import com.tencent.kuikly.compose.animation.core.tween
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
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 股票卡片。单击在页面层被接成「选中/取消」。
 *
 * TopRow（名称/代码+价/涨跌）常驻；建议行显示条件为 [StockItem.aiEnabled] 或 [selected]：
 * 重点股（aiEnabled=true）compact 直接露出建议，普通股仅在展开后露出。建议行仅在展开态可点，
 * 因此所有卡片（含重点股）的 AI 弹窗入口都要先展开、再点建议行。
 * [selected] 为 true 时卡片边框高亮，并用 AnimatedVisibility(expandVertically) 平滑展开详情块
 * （高/低/开 + 分时走势/点击查看详情）；否则仅呈现紧凑态（TopRow + 建议行）。
 */
@Composable
fun StockCard(
    item: StockItem,
    selected: Boolean,
    onOpenAi: () -> Unit,
    onEnterDetail: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    // 分时走势曲线「绘制进度」随选中做 0→1 补间，用于卡片展开时的画线效果
    val sparkProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp) // 卡片距屏幕边缘 10dp，与设计稿、沪深大盘摘要区对齐
            .padding(vertical = 5.dp)
            .shadow(if (selected) 2.dp else 0.dp, cardShape, clip = false) // 选中时轻微投影提亮
            .background(Color.White, cardShape)
            .border(1.dp, if (selected) AppColors.AiLight else AppColors.Border, cardShape)
            .padding(12.dp)
    ) {
        TopRow(item)
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TagRow(item.tags)
        }
        // 建议行：重点股（aiEnabled=true）compact 直接露出，普通股仅展开后露出；仅在展开态可点 → 弹 AI 面板
        if (item.aiEnabled || selected) {
            Spacer(modifier = Modifier.height(10.dp))
            AiBriefRow(item, onOpenAi, clickable = selected)
        }
        // 仅「高/低/开 + 分时走势/点击查看详情」随选中做 expandVertically/shrinkVertically
        AnimatedVisibility(
            visible = selected,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                expandFrom = Alignment.Top,
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            ExpandedBlock(item, selected, onEnterDetail, sparkProgress)
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
            Text(text = item.name, color = AppColors.MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = Utils.formatPrice2(item.price), color = AppColors.RiseRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            // 涨跌额（带符号），如 +43.62；涨红跌绿
            Text(
                text = Utils.formatSignedPrice2(item.change),
                color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
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
            fontWeight = FontWeight.Bold,
        )
    }
}

/** D2 标签行：圆角描边小胶囊横向排列。Tags 来自 deriveTags(与后端镜像)。 */
@Composable
private fun TagRow(tags: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, AppColors.AiLight, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = tag, color = AppColors.MainText, fontSize = 11.sp)
            }
        }
    }
}

/** 选中时展开的详情块：高/低/开 + 分时走势/点击查看详情（底部同一行）。aiBrief 已移至卡片常驻区。 */
@Composable
private fun ExpandedBlock(item: StockItem, selected: Boolean, onEnterDetail: () -> Unit, sparkProgress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        HighLowOpenRow(item, selected)
        Spacer(modifier = Modifier.height(12.dp))
        BottomRow(onEnterDetail, sparkProgress)
    }
}

@Composable
private fun AiBriefRow(item: StockItem, onOpenAi: () -> Unit, clickable: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onOpenAi),
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
        Text(text = item.aiBrief, color = AppColors.SubGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HighLowOpenRow(item: StockItem, selected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HloCell(
            label = "高", value = Utils.formatPrice2(item.high), valueColor = AppColors.RiseRed,
            modifier = staggerCellModifier(selected, 0),
        )
        HloCell(
            label = "低", value = Utils.formatPrice2(item.low), valueColor = AppColors.Green,
            modifier = staggerCellModifier(selected, 1),
        )
        HloCell(
            label = "开", value = Utils.formatPrice2(item.open), valueColor = AppColors.RiseRed,
            modifier = staggerCellModifier(selected, 2),
        )
    }
}

/** 单格「自下而上升入」的错峰补间：index 0/1/2 依次延迟 60ms。 */
@Composable
private fun staggerCellModifier(selected: Boolean, index: Int): Modifier {
    val slide by animateFloatAsState(
        targetValue = if (selected) 0f else 10f,
        animationSpec = tween(180, delayMillis = index * 60),
    )
    return Modifier.graphicsLayer { translationY = slide }
}

/** 高/低/开 单元格：标签 light + 值 bold（对照 stock-info-bar 32:2）。 */
@Composable
private fun HloCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = AppColors.MainText, fontSize = 14.sp, fontWeight = FontWeight.Light)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 底部行：左侧「分时走势」sparkline，右侧「点击查看详情」（对照 stock-info-bar 32:2 同一水平线）。 */
@Composable
private fun BottomRow(onEnterDetail: () -> Unit, sparkProgress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RiseSparkline(modifier = Modifier.weight(1f).height(28.dp), progress = sparkProgress)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "分时走势", color = AppColors.SubGray, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "点击查看详情",
            color = AppColors.MainText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.clickable(onClick = onEnterDetail),
        )
    }
}
