package com.example.task1.pages

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page

/**
 * 股票详情页（预留页）。
 *
 * 通过路由名 "stockDetail" 注册：自选列表页点击卡片后会携带 `code` 参数跳转到本页。
 * 当前仅作为占位（展示标题 + 说明文案），后续再补齐完整的行情/分时/详情内容。
 */
@Page("stockDetail")
class StockDetailPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        // 进入页面时先把 Compose 内容设置到容器里
        setContent { StockDetailScreen() }
    }
}

@Composable
fun StockDetailScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.PageBg).padding(24.dp),
    ) {
        Text(text = "股票详情（预留页）", color = AppColors.MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "点击卡片即可进入；本页为占位，后续实现完整详情。", color = AppColors.SubGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
