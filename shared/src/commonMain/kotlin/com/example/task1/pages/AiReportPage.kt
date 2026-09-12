package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.AiThinking
import com.example.task1.components.BuySection
import com.example.task1.components.MiniStockCard
import com.example.task1.components.RiskSection
import com.example.task1.components.TrendSection
import com.example.task1.base.Utils
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.AppIcon
import com.example.task1.components.core.Badge
import com.example.task1.components.core.ExpandableReveal
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiFactors
import com.example.task1.data.FactorThresh
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.shadow
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule

/**
 * AI 完整分析报告页（按低保真重构）。
 *
 * 结构：顶栏（标题+Power badge | 分享/返回）→ 行情头（现价/涨跌/高低开/市值流通市盈）→
 * AI 总结 → 涨势分析 → 风险评估 → 买入建议 → 三列维度小卡（估值画像/量价健康/综合评级）→
 * 其他维度（因子明细）→ 相关资讯（示例）。
 * 8 段单一 progress 时间窗错峰舒展；卡片舒展完成后触发内部动画（画线/仪表/进度条）。
 */
@Page("aiReport")
class AiReportPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent { AiReportScreen() }
    }
}

/** 8 段时间窗起点（窗宽 0.14，最后一段恰在 progress=1 收口）。 */
private val SegStarts = listOf(0f, 0.12f, 0.24f, 0.36f, 0.48f, 0.60f, 0.72f, 0.86f)

@Composable
fun AiReportScreen(onBack: () -> Unit = {}) {
    val activity = LocalActivity.current
    var stock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    // 从路由参数读取目标股票 code（由 WatchlistPage.openReport 透传）；缺省回退首只，避免取错股
    val code = LocalConfiguration.current.pageData.params.optString("code")
    LaunchedEffect(code) {
        val target = SampleStockApi.fetchStock(code) ?: SampleStockApi.fetchWatchlist().stocks.firstOrNull()
        stock = target
        analysis = target?.let { SampleStockApi.fetchAiAnalysis(it.code) }
    }
    // 🔴 单一 progress 贯穿全时间线（多路 animateFloatAsState 级联会卡死，铁律）
    val progress by animateFloatAsState(
        targetValue = if (analysis != null) 1f else 0f,
        animationSpec = tween(1500),
    )
    val p = SegStarts.map { start -> seg(progress, start) }

    val statusBarHeight = LocalConfiguration.current.statusBarHeight
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppColors.ReportBg)
            .padding(top = statusBarHeight.dp)
            .padding(horizontal = 16.dp),
        beyondBoundsItemCount = 2,
    ) {
        // 顶栏：标题 + Power badge + 分享/返回
        item {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "完整分析报告", color = AppColors.MainText, fontSize = AppTypography.H2, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.background(AppColors.RiskOrange.copy(alpha = 0.12f), AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = "Power by Ai模型", color = AppColors.RiskOrange, fontSize = AppTypography.Tiny)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    NavCircleButton(onClick = { /* 分享：预留 */ }) { ShareGlyph() }
                    Spacer(modifier = Modifier.width(8.dp))
                    NavCircleButton(onClick = onBack) {
                        AppIcon("arrow-right", modifier = Modifier.size(14.dp).rotate(180f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (analysis != null && stock != null) {
            val s = stock!!
            // ① 行情头
            item {
                ExpandableReveal(progress = p[0], modifier = Modifier.fillMaxWidth().alpha(p[0])) {
                    QuoteHeaderCard(stock)
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ② AI 总结（三段叙事连排，AI 口吻置顶综述）
            item {
                ExpandableReveal(progress = p[1], modifier = Modifier.fillMaxWidth().alpha(p[1])) {
                    // AI 总结专属视觉：浅靛蓝底 + 靛蓝细描边，与普通白卡区分
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
                            .background(AppColors.AiBadgeBg, RoundedCornerShape(12.dp))
                            .border(1.dp, AppColors.AiLight, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.padding(top = 2.dp)) {
                                AiIconBadge(size = AiIconSize.Small)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = com.example.task1.data.AiNarrative.trend(s, analysis!!) +
                                    com.example.task1.data.AiNarrative.risk(s, analysis!!) +
                                    com.example.task1.data.AiNarrative.buy(s, analysis!!),
                                color = AppColors.MainText,
                                fontSize = AppTypography.Body,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ③ 涨势分析
            item {
                ExpandableReveal(progress = p[2], modifier = Modifier.fillMaxWidth().alpha(p[2])) {
                    ReportCard { TrendSection(analysis!!, stock, shown = p[2] >= 1f, annotate = true) }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ④ 风险评估
            item {
                ExpandableReveal(progress = p[3], modifier = Modifier.fillMaxWidth().alpha(p[3])) {
                    ReportCard { RiskSection(analysis!!, stock, shown = p[3] >= 1f) }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ⑤ 买入建议
            item {
                ExpandableReveal(progress = p[4], modifier = Modifier.fillMaxWidth().alpha(p[4])) {
                    ReportCard { BuySection(analysis!!, stock, shown = p[4] >= 1f) }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ⑥ 三列维度小卡
            item {
                ExpandableReveal(progress = p[5], modifier = Modifier.fillMaxWidth().alpha(p[5])) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DimensionTile(
                            icon = "ic-valuation",
                            title = "估值画像",
                            value = Utils.formatDouble2(s.pe),
                            sub = valuationLabel(s.pe).first,
                            valueColor = valuationLabel(s.pe).second,
                            modifier = Modifier.weight(1f),
                        )
                        DimensionTile(
                            icon = "ic-volprice",
                            title = "量价健康",
                            value = Utils.formatDouble2(s.amplitude) + "%",
                            sub = "振幅 · " + if (s.amplitude >= FactorThresh.TAG_AMPLITUDE_PCT) "波动较大" else "波动可控",
                            valueColor = AppColors.MainText,
                            modifier = Modifier.weight(1f),
                        )
                        DimensionTile(
                            icon = "ic-trend",
                            title = "综合评级",
                            value = "${analysis!!.score}",
                            sub = s.aiProfile.action,
                            valueColor = AppColors.RecommendPurple,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ⑦ 其他维度（因子明细）
            item {
                ExpandableReveal(progress = p[6], modifier = Modifier.fillMaxWidth().alpha(p[6])) {
                    ReportCard(contentPadding = 12.dp) {
                        Text(text = "其他维度", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        FactorSection(analysis!!.factors)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            // ⑧ 相关资讯（示例占位）
            item {
                ExpandableReveal(progress = p[7], modifier = Modifier.fillMaxWidth().alpha(p[7])) {
                    ReportCard(contentPadding = 12.dp) {
                        Text(text = "相关资讯", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        NewsRow("行业动态：板块缩量整理，龙头估值回归合理区间", "示例资讯 · 今日 09:30")
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
                        Spacer(modifier = Modifier.height(8.dp))
                        NewsRow("公司公告：年度分红方案落地，股息率维持高位", "示例资讯 · 昨日 18:00")
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        } else {
            // 数据加载中：展示「AI 思考中」动画，替代空页面
            item {
                ReportCard { AiThinking() }
            }
        }
    }
}

/** 行估值结论：PE 阈值分档（与 AiNarrative.risk 同阈值）→ (label, color)。 */
private fun valuationLabel(pe: Double): Pair<String, Color> = when {
    pe <= 0 -> "亏损" to AppColors.RiskOrange
    pe <= com.example.task1.data.AiThresh.PE_GOOD -> "估值偏低" to AppColors.Green
    pe <= com.example.task1.data.AiThresh.PE_BAD -> "估值适中" to AppColors.RiskOrange
    else -> "估值偏高" to AppColors.RiseRed
}

/** 顶栏圆形图标钮：白底描边圆，内容自定。 */
@Composable
private fun NavCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(28.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 分享图标：上箭头出托盘（Canvas 手绘，zip 无分享图标）。 */
@Composable
private fun ShareGlyph() {
    Canvas(modifier = Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()
        drawLine(AppColors.SubGray, Offset(w * 0.25f, h * 0.55f), Offset(w * 0.25f, h * 0.9f), stroke)
        drawLine(AppColors.SubGray, Offset(w * 0.75f, h * 0.55f), Offset(w * 0.75f, h * 0.9f), stroke)
        drawLine(AppColors.SubGray, Offset(w * 0.25f, h * 0.9f), Offset(w * 0.75f, h * 0.9f), stroke)
        drawLine(AppColors.SubGray, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.68f), stroke)
        drawLine(AppColors.SubGray, Offset(w * 0.32f, h * 0.24f), Offset(w * 0.5f, h * 0.08f), stroke)
        drawLine(AppColors.SubGray, Offset(w * 0.68f, h * 0.24f), Offset(w * 0.5f, h * 0.08f), stroke)
    }
}

/** 行情头卡：名称/代码 | 现价+涨跌额+涨跌幅 | 高低开 | 市值/流通/市盈（低保真行情区）。 */
@Composable
private fun QuoteHeaderCard(stock: StockItem?) {
    if (stock == null) return
    val up = stock.changePct >= 0
    val trendColor = if (up) AppColors.RiseRed else AppColors.Green
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = stock.name, color = AppColors.MainText, fontSize = AppTypography.Title, fontWeight = FontWeight.Bold)
                Text(text = stock.code, color = AppColors.SubGray, fontSize = AppTypography.Caption)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = Utils.formatPrice2(stock.price), color = trendColor, fontSize = AppTypography.H2, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Badge(text = Utils.formatSignedPrice2(stock.change), color = trendColor)
            Spacer(modifier = Modifier.width(6.dp))
            Badge(text = Utils.formatPercent(stock.changePct), color = trendColor)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuoteCell("高", Utils.formatPrice2(stock.high), trendColor, modifier = Modifier.weight(1f))
            QuoteCell("低", Utils.formatPrice2(stock.low), if (up) AppColors.Green else AppColors.RiseRed, modifier = Modifier.weight(1f))
            QuoteCell("开", Utils.formatPrice2(stock.open), trendColor, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuoteCell("市值", Utils.formatMarketCapYuan(stock.marketCap), AppColors.MainText, modifier = Modifier.weight(1f))
            QuoteCell("流通", Utils.formatMarketCapYuan(stock.floatCap), AppColors.MainText, modifier = Modifier.weight(1f))
            QuoteCell("市盈", Utils.formatDouble2(stock.pe), AppColors.MainText, modifier = Modifier.weight(1f))
        }
    }
}

/** 行情头单元格：灰标签在上、值在下（低保真「高 1317.00」样式）。 */
@Composable
private fun QuoteCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = valueColor, fontSize = AppTypography.Body, fontWeight = FontWeight.SemiBold)
    }
}

/** 三列维度小卡：竖版（图标 + 标题 + 主值 + 副值）。 */
@Composable
private fun DimensionTile(
    icon: String,
    title: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(AppColors.AiBadgeBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = valueColor, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = sub, color = AppColors.SubGray, fontSize = AppTypography.Tiny, maxLines = 1)
    }
}

/** 相关资讯行：标题 + 来源时间。 */
@Composable
private fun NewsRow(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        // 靛蓝 accent 竖条：资讯条目的视觉锚点
        Box(
            modifier = Modifier.padding(top = 3.dp, end = 8.dp)
                .size(width = 3.dp, height = 30.dp)
                .background(AppColors.AiLight, RoundedCornerShape(2.dp)),
        )
        Column {
            Text(text = title, color = AppColors.MainText, fontSize = AppTypography.BodySmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = meta, color = AppColors.SubGray, fontSize = AppTypography.Tiny)
        }
    }
}

/** 段落窗口插值：progress 越过 [start] 后在 0.14 宽度内从 0 到 1。 */
private fun seg(progress: Float, start: Float): Float =
    ((progress - start) / 0.14f).coerceIn(0f, 1f)

/** 报告页白色分区卡：圆角 12dp 白底 + 1dp 发丝描边。
 * [contentPadding] 默认 0——内容组件自带 12dp+ 内边距。 */
@Composable
private fun ReportCard(contentPadding: com.tencent.kuikly.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    // 必须用 Column：Box 子元素是堆叠布局，多子元素卡（标题+内容）会文字重叠
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(contentPadding),
    ) {
        content()
    }
}

/** D4 因子明细区:动量/价值/风险分 + 行业 + 强于大盘基准差。 */
@Composable
private fun FactorSection(f: AiFactors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionRow("动量分", f.momentum.toString())
        SectionRow("价值分", f.value.toString())
        SectionRow("风险分", f.risk.toString())
        val rank = if (f.industryRank >= 0) "（组内第 ${f.industryRank + 1}）" else ""
        SectionRow("行业", f.industry + rank)
        SectionRow("强于大盘", f.benchmarkDelta?.let { Utils.formatPercent(it) } ?: "—")
    }
}

/** 因子单行：左侧固定宽标签 + 右侧值。 */
@Composable
private fun SectionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, color = AppColors.SubGray, fontSize = AppTypography.BodySmall, modifier = Modifier.width(72.dp))
        Text(text = value, color = AppColors.MainText, fontSize = AppTypography.BodySmall)
    }
}
