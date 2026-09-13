package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.example.task1.base.Utils
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.core.Badge
import com.example.task1.components.core.CardSurface
import com.example.task1.components.core.Chip
import com.example.task1.components.core.ExpandableReveal
import com.example.task1.components.core.StatCell
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
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
 * [selected] 为 true 时卡片边框高亮，并平滑展开「详情块」（高/低/开 + 点击查看详情）；
 * 否则仅呈现紧凑态（TopRow [+ 重点股建议行]）。
 *
 * 收放动画由**单一进度值** [expand] 驱动（[ExpandableReveal]），**不要**换回
 * `AnimatedVisibility`/`expandVertically`：那套过渡在 Kuikly 上不逐帧执行，一整段收放会塌成
 * 一帧跳变——普通股收起时表现为「AI 提示词瞬间消失、下方行情瞬移补位」。
 */
@Composable
fun StockCard(
    item: StockItem,
    selected: Boolean,
    onOpenAi: () -> Unit,
    onEnterDetail: () -> Unit,
) {
    // 收起/展开的唯一驱动源：建议行与详情块共用它，两边才会同步收放，不会各弹各的。
    val expand by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
    )
    // 分时走势的画线动画已下沉到 RiseSparklineAnimated 内部（重组优化 2026-09-11）：
    // 动画中间值不再流经 StockCard/ExpandedBlock/BottomRow，避免展开态子树每帧重组
    CardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp) // 卡片距屏幕边缘 10dp，与设计稿、沪深大盘摘要区对齐
            .padding(vertical = 5.dp),
        selected = selected,
    ) {
        TopRow(item)
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.tags.forEach { tag -> Chip(text = tag) }
            }
        }
        // 建议行：重点股（aiEnabled=true）compact 直接露出，普通股仅展开后露出；仅在展开态可点 → 弹 AI 面板
        if (item.aiEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            AiBriefRow(item, onOpenAi, clickable = selected)
        } else {
            // 普通股收起态没有建议行，所以它必须跟着 expand 一起收放：
            // 早先这里写的是裸 `if (item.aiEnabled || selected)`，收起首帧整行就被摘掉、高度当场归零，
            // 于是提示词「啪」地消失、下方详情块瞬移补位。间隔也放进动画里，避免收起后残留 10dp 空白。
            ExpandableReveal(progress = expand) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    AiBriefRow(item, onOpenAi, clickable = selected)
                }
            }
        }
        // 仅「高/低/开 + 分时走势/点击查看详情」随选中做 expandVertically/shrinkVertically
        ExpandableReveal(progress = expand) {
            ExpandedBlock(item, selected, onEnterDetail)
        }
    }
}

/** 顶行：左侧名称+代码，右侧现价+涨跌额+涨跌幅徽章（红涨绿跌）。 */
@Composable
private fun TopRow(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.name, color = AppColors.MainText, fontSize = AppTypography.Title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Utils.formatPrice2(item.price),
                color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
                fontSize = AppTypography.Title,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 涨跌额（带符号），如 +43.62；涨红跌绿
            Text(
                text = Utils.formatSignedPrice2(item.change),
                color = if (item.change >= 0) AppColors.RiseRed else AppColors.Green,
                fontSize = AppTypography.Caption,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Badge(text = Utils.formatPercent(item.changePct), color = if (item.changePct >= 0) AppColors.RiseRed else AppColors.Green)
        }
    }
}

/** 选中时展开的详情块：高/低/开 三指标与「点击查看详情」同一行（分时走势图已按需求移除）。 */
@Composable
private fun ExpandedBlock(item: StockItem, selected: Boolean, onEnterDetail: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(label = "高", value = Utils.formatPrice2(item.high), valueColor = AppColors.RiseRed, modifier = Modifier.weight(1f).then(staggerCellModifier(selected, 0)))
        StatCell(label = "低", value = Utils.formatPrice2(item.low), valueColor = AppColors.Green, modifier = Modifier.weight(1f).then(staggerCellModifier(selected, 1)))
        StatCell(label = "开", value = Utils.formatPrice2(item.open), valueColor = AppColors.RiseRed, modifier = Modifier.weight(1f).then(staggerCellModifier(selected, 2)))
        // 与指标同一行的「查看详情」入口（靛蓝主色，呼应 AI/CTA）
        Text(
            text = "点击查看详情 ›",
            color = AppColors.CtaBg,
            fontSize = AppTypography.BodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onEnterDetail)
                .padding(start = 8.dp),
        )
    }
}

/** 建议行：AI 徽章 + 一句话建议 + 依据信号；[clickable] 控制是否可点（仅在展开态可点，避免误触）。 */
@Composable
private fun AiBriefRow(item: StockItem, onOpenAi: () -> Unit, clickable: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onOpenAi),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiIconBadge(size = AiIconSize.Small)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.aiBrief, color = AppColors.SubGray, fontSize = AppTypography.BodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.width(6.dp))
        // 依据信号：让建议可解释（如「依据：MACD金叉」）
        Text(
            text = "依据：${item.aiProfile.signal}",
            color = AppColors.AiLight,
            fontSize = AppTypography.Tiny,
            maxLines = 1,
        )
    }
}

/**
 * 单格「自下而上升入」的错峰补间：index 0/1/2 依次延迟 60ms。
 *
 * 位移走 `offset(y = Dp)` 值参数，而不是 `graphicsLayer { translationY = ... }`——
 * 后者在 Kuikly 上不逐帧执行，lambda 只在组合那一下求值，位移会停在首帧值上不再变化。
 */
@Composable
private fun staggerCellModifier(selected: Boolean, index: Int): Modifier {
    val slide by animateFloatAsState(
        targetValue = if (selected) 0f else 10f,
        animationSpec = tween(180, delayMillis = index * 60),
    )
    return Modifier.offset(y = slide.dp)
}
