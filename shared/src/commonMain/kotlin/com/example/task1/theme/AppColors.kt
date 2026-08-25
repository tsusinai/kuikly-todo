package com.example.task1.theme

import com.tencent.kuikly.compose.ui.graphics.Color

object AppColors {
    val RiseRed = Color(0xFFDF0004)        // 涨/价格红
    val Green = Color(0xFF4A6B5A)          // 强调绿
    val SubGray = Color(0xFF666666)        // 次级灰 (代码/说明)
    val MainText = Color(0xFF2D2D2D)       // 主文字
    val Border = Color(0xFFB5C5C5)         // 边框
    val PageBg = Color(0xFFFFFFFF)         // 页面/弹层背景(设计稿为纯白)
    val HeaderBg = Color(0xFFF6DCD7)       // 顶部区背景
    val AiLight = Color(0xFFE9B8AC)        // AI 渐变起
    val AiBg = Color(0xFFF6DCD7)           // AI 渐变终
    val AiBadgeBg = Color(red = 233f / 255f, green = 184f / 255f, blue = 172f / 255f, alpha = 0.15f)
    val RiseBadgeBg = Color(red = 74f / 255f, green = 107f / 255f, blue = 90f / 255f, alpha = 0.10f)
    val RiskOrange = Color(0xFFF59E0B)     // 中低风险
    val RecommendPurple = Color(0xFFA78BFA) // 推荐指数
    val RiskText = Color(0xFF884D3A)       // 当前评级/高风险(旧)
    val GaugeHigh = Color(0xFFF0AAB4)      // 风险评估·高风险段浅粉
    val CtaBg = Color(0xFFF0DCD2)          // CTA 浅玫瑰背景

    val AiGradient = listOf(AiLight, AiBg)
    val RecommendGradient = listOf(Color(0xFFA78BFA), Color(0xFFC6B8F6)) // 买入建议进度填充渐变
}
