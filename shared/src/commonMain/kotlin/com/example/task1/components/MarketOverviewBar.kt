package com.example.task1.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.data.MarketOverview
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp

/** 涨跌条动画时长：够看清比例变化，又不至于让刷新后的数字等太久。 */
private const val BAR_ANIM_MS = 420
private const val BAR_HEIGHT_DP = 8

/**
 * 市场大盘摘要栏：合计市值变动、收盘状态、数据日期，以及涨/跌家数与对应红绿进度条。
 *
 * [overview] 由列表同一次拉取推导（见 [com.example.task1.data.deriveMarketOverview]）。
 * 没有数据时**不要调用本组件**——列表区由页面统一渲染加载态/空态，
 * 免得占位条与加载提示同屏打架。
 *
 * 涨跌条：底色是跌（绿），上层红条宽度 = 涨占比，比例变化走 [animateFloatAsState]。
 * 本组件只在数据到位后才被组合，所以首帧拿到的就是终值——入场展开要靠 [ready] 这一帧延迟：
 * 先按 0 组合，再由 effect 把目标值抬到真实比例，动画才会从 0 展开。
 */
@Composable
fun MarketOverviewBar(overview: MarketOverview, modifier: Modifier = Modifier) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val netChange = overview.netCapChangeYuan
    val amountColor = when {
        netChange > 0L -> AppColors.RiseRed
        netChange < 0L -> AppColors.Green
        else -> AppColors.MainText
    }
    val hasBreadth = overview.riseCount + overview.fallCount > 0
    val riseFraction by animateFloatAsState(
        targetValue = if (ready) overview.riseFraction else 0f,
        animationSpec = tween(durationMillis = BAR_ANIM_MS, easing = FastOutSlowInEasing),
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 15.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Utils.formatSignedYuan(netChange),
                color = amountColor,
                fontSize = AppTypography.H1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = overview.statusText,
                color = AppColors.MainText,
                fontSize = AppTypography.BodySmall,
            )
        }
        Text(
            text = overview.dateText,
            color = AppColors.MainText,
            fontSize = AppTypography.BodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "涨${overview.riseCount}", color = AppColors.RiseRed, fontSize = AppTypography.BodySmall)
            if (overview.flatCount > 0) {
                Text(text = "平${overview.flatCount}", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            }
            Text(text = "跌${overview.fallCount}", color = AppColors.Green, fontSize = AppTypography.BodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 涨(红) / 跌(绿)：左红右绿。无涨跌样本时底色退成中性灰，免得看着像「全跌」
        Box(
            modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT_DP.dp)
                .background(if (hasBreadth) AppColors.Green else AppColors.Border, AppShapes.Badge),
        ) {
            val riseWidth = riseFraction.coerceIn(0f, 1f)
            // 宽度为 0 的 Box 没有意义，比例还没涨起来时干脆不渲染
            if (riseWidth > 0.001f) {
                Box(
                    modifier = Modifier.fillMaxWidth(riseWidth).height(BAR_HEIGHT_DP.dp)
                        .background(AppColors.RiseRed, AppShapes.Badge),
                )
            }
        }
    }
}
