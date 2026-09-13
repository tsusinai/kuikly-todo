package com.example.task1.theme

import com.tencent.kuikly.compose.ui.graphics.Color

/**
 * 配色方案 v2 ——「石墨蓝金融」Graphite Blue
 *
 * 设计说明（2026-09-11 重设计）：
 * - 中性底色由暖粉系转为冷调蓝灰系，营造专业金融质感
 * - 涨/跌语义色重校准：涨 #E5484D / 跌 #30A46C（A股习惯：红涨绿跌）
 * - AI 强调色从珊瑚粉转为靛蓝系（AiLight/AiBg/AiBadgeBg/RiskText 统一靛蓝身份）
 * - CTA 从浅玫瑰改为实色靛蓝（白字对比度 6.3:1，WCAG AA）
 * - 所有正文色对白底对比度 ≥ 4.5:1（WCAG AA）
 * - 页面语言统一：所有页面都是 HeaderBg 顶栏 + PageBg 白底 + 1dp 分隔线分块，
 *   不用浮动白卡（避免同一份数据在两个页面呈现成两种视觉体系）
 */
object AppColors {
    val RiseRed = Color(0xFFE5484D)        // 涨/价格红（Radix Red 9，对白 4.0:1，大字号达标）
    val Green = Color(0xFF30A46C)          // 强调绿/跌（Radix Green 9）
    val SubGray = Color(0xFF6B7280)        // 次级灰 (代码/说明，对白 4.8:1)
    val MainText = Color(0xFF1A1D24)       // 主文字（墨黑，对白 15.9:1）
    val Border = Color(0xFFE4E7EE)         // 边框（冷调浅灰）
    val PageBg = Color(0xFFFFFFFF)         // 页面/弹层背景(纯白)
    val HeaderBg = Color(0xFFEDF1F7)       // 顶部区背景（冷调蓝灰）
    val AiLight = Color(0xFF7C8CF8)        // AI 渐变起（靛蓝亮）
    val AiBg = Color(0xFFE9EDFF)           // AI 渐变终（靛蓝浅底）
    val AiBadgeBg = Color(red = 124f / 255f, green = 140f / 255f, blue = 248f / 255f, alpha = 0.12f)
    val RiseBadgeBg = Color(red = 48f / 255f, green = 164f / 255f, blue = 108f / 255f, alpha = 0.10f)
    val RiskOrange = Color(0xFFD97706)     // 中低风险/警示（Amber 600，对白 3.3:1 大字号达标）
    val RecommendPurple = Color(0xFF7048E8) // 推荐指数（Violet 600，对白 5.5:1）
    val RiskText = Color(0xFF4353D9)       // AI 徽章文字/当前评级（靛蓝 600，对白 6.0:1）
    val GaugeHigh = Color(0xFFF2B8B5)      // 风险评估·高风险段浅红
    val CtaBg = Color(0xFF4353D9)          // CTA 实色靛蓝（白字对比 6.3:1）
    val HandleGray = Color(0xFFD5D9E0)  // 底部抽屉手柄灰

    // 走势图表：均线族 / 网格 / 刻度文字
    val Ma5 = Color(0xFFE8A33D)        // 均线 MA5（橙）
    val Ma10 = Color(0xFF3B82F6)       // 均线 MA10（蓝）
    val Ma20 = Color(0xFF8B5CF6)       // 均线 MA20（紫）
    val ChartGrid = Color(0xFFEDF1F7)  // 图表网格线（比 Border 更浅）
    val ChartAxis = Color(0xFF9AA3B2)  // 图表刻度文字

    val AiGradient = listOf(AiLight, AiBg)
    val RecommendGradient = listOf(Color(0xFF7048E8), Color(0xFF9B8AF0)) // 买入建议进度填充渐变
}
