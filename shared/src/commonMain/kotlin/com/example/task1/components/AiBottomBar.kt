package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
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
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 「分析智窗」底部常驻建议卡（两态：思考→建议）。
 *
 * 白色胶囊，`sparkles + 分析智窗` 标题常驻；右侧内容随 [thinking] 切换：
 *  - 思考态：浅灰「思考中......」
 *  - 建议态：`全盘AI建议·<dimensionLabel>`（MainText）+ [advice] 文案（SubGray，最多两行）
 *
 * 长按整卡呼出维度选择弹层（[onLongPress]）；拖入股票的入口已移除。
 */
@Composable
fun AiBottomBar(
    advice: String,
    thinking: Boolean,
    dimensionLabel: String,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            .background(Color.White, RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            Spacer(modifier = Modifier.width(10.dp))
            if (thinking) {
                Text(text = "思考中......", color = AppColors.SubGray, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "全盘AI建议·$dimensionLabel", color = AppColors.MainText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = advice, color = AppColors.SubGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    // 维度切换入口:点击弹维度层(与长按等价)。assets 无切换类 icon,用「▾」文本箭头;如需 PNG 可补 swap.png
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(AppColors.AiLight, RoundedCornerShape(9.dp))
                            .clickable { onLongPress() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "▾", color = AppColors.MainText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
