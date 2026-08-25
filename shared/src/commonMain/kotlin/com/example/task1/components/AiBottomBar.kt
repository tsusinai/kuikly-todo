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
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/**
 * 「分析智窗」底部常驻建议卡（单行/两态）。
 *
 * 白色胶囊，`sparkles + 分析智窗` 标题常驻；右侧内容随 [thinking] 两态切换：
 *  - 思考态：浅灰「思考中......」
 *  - 建议态：`全盘股票AI建议：`（MainText）+ [advice] 文案（SubGray，两行）
 *
 * 作为常驻信息卡，默认不绑定点击动作；拖入股票的入口已移除。
 */
@Composable
fun AiBottomBar(
    advice: String,
    thinking: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            .background(Color.White, RoundedCornerShape(16.dp))
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "全盘股票AI建议：", color = AppColors.MainText, fontSize = 13.sp)
                    Text(text = advice, color = AppColors.SubGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
