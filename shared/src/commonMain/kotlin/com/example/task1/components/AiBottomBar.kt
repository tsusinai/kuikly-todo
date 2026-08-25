package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.animation.shrinkVertically
import com.tencent.kuikly.compose.foundation.background
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
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 「分析智窗」底部栏（两段式）。
 *
 * 上段：sparkles + 「分析智窗」 + 右侧引导文案「长按并拖入股票进入ai分析」。
 * 下段（[showSparkline] 为 true 时）：分时走势红色 sparkline + label，用 AnimatedVisibility 平滑出现/消失。
 * 整栏可点击（页面层接成打开分析面板）；进入栏瞬间的触觉反馈由页面层拖拽命中逻辑负责。
 */
@Composable
fun AiBottomBar(
    onClick: () -> Unit,
    showSparkline: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(AppColors.HeaderBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(AppColors.AiLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    AppIcon("sparkles", modifier = Modifier.size(16.dp))
                }
                Text(text = "分析智窗", color = AppColors.MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            Text(text = "长按并拖入股票进入ai分析", color = AppColors.SubGray, fontSize = 13.sp)
        }
        AnimatedVisibility(
            visible = showSparkline,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RiseSparkline(modifier = Modifier.weight(1f).height(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "分时走势", color = AppColors.SubGray, fontSize = 12.sp)
            }
        }
    }
}
