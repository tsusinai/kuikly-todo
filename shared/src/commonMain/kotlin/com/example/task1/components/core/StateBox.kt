package com.example.task1.components.core

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 页面级状态占位:加载 / 失败可重试 / 空数据。三页共用同一套视觉与交互口径。
 *
 * 抽出来的理由:此前详情页与报告页各写一份「加载中…」,而失败态干脆没有——
 * 数据取不到时用户只能看永久转圈,无法自救。统一到这里之后,
 * 加载态给占位、失败态**必须**给 [ErrorStateBox.onRetry]、空态给出可读结论。
 *
 * 三者等高(默认 [DEFAULT_STATE_HEIGHT]),相互切换不产生布局跳动。
 */
private val DEFAULT_STATE_HEIGHT = 160.dp

/** 加载态:居中一句轻提示,失败/成功切换时高度不跳动。 */
@Composable
fun LoadingStateBox(
    modifier: Modifier = Modifier,
    text: String = "加载中…",
    minHeight: Dp = DEFAULT_STATE_HEIGHT,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(minHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = AppColors.SubGray, fontSize = AppTypography.Body)
    }
}

/**
 * 失败态:必须提供 [onRetry],否则用户没有出口。
 *
 * @param title 一句话结论(如「行情加载失败」)
 * @param hint 可操作的原因提示(如「请检查网络后重试」)
 */
@Composable
fun ErrorStateBox(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "数据加载失败",
    hint: String = "行情服务暂时不可用,请检查网络后重试",
    retryLabel: String = "重试",
    minHeight: Dp = DEFAULT_STATE_HEIGHT,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(minHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = AppColors.MainText,
                fontSize = AppTypography.Title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                color = AppColors.SubGray,
                fontSize = AppTypography.Caption,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .background(AppColors.CtaBg, RoundedCornerShape(10.dp))
                    .clickable { onRetry() }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = retryLabel,
                    color = Color.White,
                    fontSize = AppTypography.BodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 空态:请求成功但没有内容可展示;与失败态区分,不误导用户去重试网络。 */
@Composable
fun EmptyStateBox(
    modifier: Modifier = Modifier,
    text: String = "暂无数据",
    minHeight: Dp = DEFAULT_STATE_HEIGHT,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(minHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .background(AppColors.HeaderBg, AppShapes.Badge)
                    .border(1.dp, AppColors.Border, AppShapes.Badge)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = text, color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            }
        }
    }
}
