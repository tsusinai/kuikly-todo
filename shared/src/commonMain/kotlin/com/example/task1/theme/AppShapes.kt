package com.example.task1.theme

import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.unit.dp

object AppShapes {
    val Card = RoundedCornerShape(12.dp)
    val Sheet = RoundedCornerShape(16.dp)
    /** 底部弹层顶边圆角：弹层贴屏幕底边，只圆上两角。 */
    val SheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Badge = RoundedCornerShape(4.dp)
    val Pill = RoundedCornerShape(999.dp)
    val Search = RoundedCornerShape(17.dp)
    val Handle = RoundedCornerShape(2.dp)
}
