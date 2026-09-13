package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.components.AiReasoning
import com.example.task1.components.AiThinking
import com.example.task1.components.aiConfidenceOf
import com.example.task1.components.BuySection
import com.example.task1.components.RiskSection
import com.example.task1.components.TrendSection
import com.example.task1.components.core.AiIconBadge
import com.example.task1.components.core.AiIconSize
import com.example.task1.components.core.Badge
import com.example.task1.components.core.ChevronBack
import com.example.task1.components.core.ErrorStateBox
import com.example.task1.components.core.ExpandableReveal
import com.example.task1.components.core.SectionDivider
import com.example.task1.components.core.SectionHeader
import com.example.task1.components.core.StockChart
import com.example.task1.data.AiAnalysis
import com.example.task1.data.AiFactors
import com.example.task1.data.AiNarrative
import com.example.task1.data.ChartPeriod
import com.example.task1.data.StockApis
import com.example.task1.data.StockChartData
import com.example.task1.data.StockItem
import com.example.task1.data.mockNews
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
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.text.SpanStyle
import com.tencent.kuikly.compose.ui.text.buildAnnotatedString
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.withStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import kotlinx.coroutines.CancellationException

/**
 * AI 完整分析报告页。
 *
 * 视觉与详情页/自选页同一套语言：沉浸 [AppColors.HeaderBg] 顶栏 + 白底 + 1dp 分隔线分块，
 * **不用浮动白卡**（同一只股票在详情页与报告页不该呈现成两种视觉体系）。
 * 左右边距 20dp、块间「12dp + 满幅分隔线 + 12dp」——与详情页一致。
 *
 * 结构（8 段）：行情块 → AI 总结 → 涨势分析（真实分时图）→ 风险评估 → 买入建议 →
 * 其他维度（因子明细）→ AI 推理过程（可折叠）→ 相关资讯。
 * 单一 progress 贯穿全时间线按段错峰舒展；段落舒展到位后，段内小动画（画图/仪表/进度条）才开始。
 */
@Page("aiReport")
class AiReportPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent { AiReportScreen() }
    }
}

/** 段落数：改这里即可增删段落，[SegStarts] 会自动重排。 */
private const val SEG_COUNT = 8

/** 段间时间窗宽度（每段舒展占 progress 的比例）。 */
private const val SEG_WINDOW = 0.14f

/** 块间节奏：内容 → 12dp → 满幅分隔线 → 12dp → 下一块。 */
private val SectionGap = 12.dp

/** 各段时间窗起点：等距铺开且最后一段恰在 progress=1 收口。 */
private val SegStarts = List(SEG_COUNT) { i -> i * (1f - SEG_WINDOW) / (SEG_COUNT - 1) }

@Composable
fun AiReportScreen(onBack: (() -> Unit)? = null) {
    val activity = LocalActivity.current
    // 返回出口:未显式传入时直接关当前页,避免「报告页返回键点不动」
    val navigateBack: () -> Unit = onBack
        ?: { activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage() }
    // 数据源链:自研后端(带 LLM 缝) → 直连腾讯(本地规则引擎推导) → 本地样例
    fun network(): NetworkModule = activity.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val stockApi = remember { StockApis.stocks { network() } }
    // 走势与详情页同源(后端 /chart → 样例走势兜底),两页同一只票看到的是同一条分时
    val chartApi = remember { StockApis.chart { network() } }
    var stock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    var chart by remember { mutableStateOf<StockChartData?>(null) }
    var chartFailed by remember { mutableStateOf(false) }
    // 失败态:取不到行情就无从生成分析,必须给出重试出口而不是停在「思考中」
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // 从路由参数读取目标股票 code（由 WatchlistPage.openReport 透传）；缺省回退首只，避免取错股
    val code = LocalConfiguration.current.pageData.params.optString("code")
    LaunchedEffect(code, reloadKey) {
        failed = false
        stock = null
        analysis = null
        chart = null
        chartFailed = false
        val target = stockApi.fetchStock(code) ?: stockApi.fetchWatchlist().stocks.firstOrNull()
        if (target == null) {
            failed = true
            return@LaunchedEffect
        }
        stock = target
        // 先取走势再取分析:整页的揭示由 analysis 驱动,保证「涨势分析」露出来时图已在手,
        // 不会出现「先亮出一块空框、隔一会儿才长出线」的抖动
        chart = try {
            chartApi.fetchChart(target.code, ChartPeriod.INTRADAY, quote = target)
                .takeIf { it.candles.isNotEmpty() }
                ?: run { chartFailed = true; null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            chartFailed = true
            null
        }
        analysis = stockApi.fetchAiAnalysis(target.code)
    }
    // 🔴 单一 progress 贯穿全时间线（多路 animateFloatAsState 级联会卡死，铁律）
    val progress by animateFloatAsState(
        targetValue = if (analysis != null) 1f else 0f,
        animationSpec = tween(1500),
    )
    val p = SegStarts.map { start -> seg(progress, start) }

    val configuration = LocalConfiguration.current
    val statusBarHeight = configuration.statusBarHeight
    val navigationBarHeight = configuration.navigationBarHeight
    LazyColumn(modifier = Modifier.fillMaxSize().background(AppColors.PageBg), beyondBoundsItemCount = 2) {
        // 顶栏不参与揭示动画:它是页面身份,不该「长出来」
        item { ReportHeader(topInset = statusBarHeight, onBack = navigateBack) }
        if (analysis != null && stock != null) {
            val s = stock!!
            // ① 行情块:报告页聚焦 AI 结论,行情只留「名称 + 现价 + 涨跌」——
            //    高/低/开、市值/流通/市盈在详情页已有,重复一遍只会挤掉结论的篇幅
            item { Reveal(p[0]) { QuoteBlock(s) } }
            // ② AI 总结(三段叙事连排,AI 口吻置顶综述)
            item { Reveal(p[1]) { AiSummaryBlock(s, analysis!!) } }
            // ③ 涨势分析:真实分时图(取数失败给可重试出口,不拿假曲线糊弄)
            item {
                Reveal(p[2]) {
                    TrendSection(
                        analysis = analysis!!,
                        stock = s,
                        shown = p[2] >= 1f,
                        contentPadding = 0.dp,
                        // 顶部 AI 总结卡已经讲过同一条趋势结论，图下再复述一遍只会显得页面在自我重复
                        showNarrative = false,
                        chartContent = {
                            val data = chart
                            if (data != null) {
                                StockChart(
                                    data = data,
                                    modifier = Modifier.fillMaxWidth(),
                                    showDimensionBar = false,
                                    mainHeight = 140.dp,
                                    volumeHeight = 44.dp,
                                )
                            } else if (chartFailed) {
                                ChartFailBox(onRetry = { reloadKey++ })
                            } else {
                                ChartSkeleton()
                            }
                        },
                    )
                }
            }
            // ④ 风险评估
            item { Reveal(p[3]) { RiskSection(analysis!!, s, shown = p[3] >= 1f, contentPadding = 0.dp) } }
            // ⑤ 买入建议
            item { Reveal(p[4]) { BuySection(analysis!!, s, shown = p[4] >= 1f, contentPadding = 0.dp) } }
            // ⑥ 其他维度(因子明细)
            item {
                Reveal(p[5]) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(icon = "ic-valuation", title = "其他维度")
                        Spacer(modifier = Modifier.height(10.dp))
                        FactorSection(analysis!!.factors)
                    }
                }
            }
            // ⑦ AI 推理过程(可折叠):抽屉只给结论,依据留在报告页
            item { Reveal(p[6]) { AiReasoning(s, analysis!!) } }
            // ⑧ 相关资讯(示例占位)
            item {
                Reveal(p[7], tail = false) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 不硬塞图标:现成图标集里没有「资讯」语义的，塞一个不相干的图标比留白更糟
                        SectionHeader(title = "相关资讯")
                        Spacer(modifier = Modifier.height(10.dp))
                        // 按个股生成:此前两条写死，所有股票(含港股)都是同一段 A 股语境文案
                        val news = remember(s) { mockNews(s) }
                        news.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SectionDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            NewsRow(item.title, item.meta)
                        }
                    }
                }
            }
        } else if (failed) {
            // 取数失败:给出可重试的出口(此前只有「思考中」,用户会一直等下去)
            item {
                ReportSection {
                    ErrorStateBox(
                        onRetry = { reloadKey++ },
                        title = "报告生成失败",
                        hint = "未能取到该股行情,无法生成分析,请检查网络后重试",
                        minHeight = 300.dp,
                    )
                }
            }
        } else {
            // 数据加载中:展示「AI 思考中」动画,替代空页面
            item { ReportSection { AiThinking() } }
        }
        // 末项让出导航栏高度,最后一段不会被手势条压住
        item { Spacer(modifier = Modifier.height((navigationBarHeight + 24f).dp)) }
    }
}

/**
 * 段落揭示:单 progress 时间窗 → 舒展 + 淡入,尾部带「块间节奏」。
 *
 * 内容与分隔线必须裹在**同一个 Column** 里:ExpandableReveal 底层只 measure/place 第一个子节点,
 * 平级兄弟会被整块丢掉。
 */
@Composable
private fun Reveal(progress: Float, tail: Boolean = true, content: @Composable () -> Unit) {
    ExpandableReveal(progress = progress, modifier = Modifier.fillMaxWidth().alpha(progress)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ReportSection { content() }
            if (tail) {
                Spacer(modifier = Modifier.height(SectionGap))
                SectionDivider()
                Spacer(modifier = Modifier.height(SectionGap))
            }
        }
    }
}

/**
 * 内容块外壳:只有 20dp 屏幕边距,**没有底色/圆角/阴影/描边**。
 * 分块靠块间的满幅 [SectionDivider],与详情页同一套语言。
 */
@Composable
private fun ReportSection(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { content() }
}

/** 页头:标题居中 + 左侧返回、右侧分享(与详情页 Header 同规格:32dp 白圆、无描边)。 */
@Composable
private fun ReportHeader(topInset: Float, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.HeaderBg)
            .padding(start = 12.dp, end = 12.dp, top = (topInset + 10f).dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCircleButton(onClick = onBack) { ChevronBack(modifier = Modifier.size(16.dp)) }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "完整分析报告", color = AppColors.MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Box(modifier = Modifier.background(AppColors.AiBadgeBg, AppShapes.Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(text = "Power by Ai 模型", color = AppColors.RiskText, fontSize = AppTypography.Tiny)
            }
        }
        NavCircleButton(onClick = { /* 分享：预留 */ }) { ShareGlyph() }
    }
}

/** 顶栏圆形图标钮:白底圆、内容自定(与详情页返回钮同规格)。 */
@Composable
private fun NavCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).background(Color.White, AppShapes.Pill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 分享图标:上箭头出托盘(Canvas 手绘,zip 无分享图标)。 */
@Composable
private fun ShareGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
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

/** 行情块:名称 + 代码一行,现价大字居中,涨跌额 + 涨跌幅徽章(与详情页 PriceBlock 同规格)。 */
@Composable
private fun QuoteBlock(item: StockItem) {
    val up = item.changePct >= 0
    val trendColor = if (up) AppColors.RiseRed else AppColors.Green
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶栏与行情之间留 12dp:详情页 Header → PriceBlock 也是 12dp,两页同一只票的顶部节奏一致
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.name, color = AppColors.MainText, fontSize = AppTypography.Title, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = AppTypography.Caption)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = Utils.formatPrice2(item.price), color = trendColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Utils.formatSignedPrice2(item.change),
                color = trendColor,
                fontSize = AppTypography.Body,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Badge(text = Utils.formatPercent(item.changePct), color = trendColor)
        }
    }
}

/**
 * AI 总结块:与「涨势分析」「风险评估」同构的普通区块——区块标题 + 三段正文，
 * 左右边距由 [ReportSection] 的 20dp 统一提供。
 *
 * 此前是一张浅靛蓝卡:卡片自带 12dp 内边距,正文因此比全页其他文字多内缩 12dp(32dp 处起排),
 * 整页只有它一个「浮起来的紫色块」。页面既然已经是「白底 + 分隔线分块」,AI 结论没有理由例外——
 * 结论靠标题和红色结论词就立得住,不需要一层底色。
 */
@Composable
private fun AiSummaryBlock(item: StockItem, analysis: AiAnalysis) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 体例与 SectionHeader 一致(标题 + 右上小字),只多一枚 AI 徽章:这一段是全页唯一由模型生成的
        // 内容,标题栏认得出「这是 AI 说的」比省一个图标重要；区块本身保持白底无卡片
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AiIconBadge(size = AiIconSize.Small)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "AI 综合分析", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            // 信心度作右侧小字(与「风险评估」右上的等级、「买入建议」右上的推荐指数同一位置、同一体例)
            Text(
                text = "信心 ${aiConfidenceOf(analysis)}%",
                color = AppColors.SubGray,
                fontSize = AppTypography.BodySmall,
                modifier = Modifier.align(Alignment.Bottom),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 按段分行渲染:三段直接字符串相加会粘成一句读不断的长句
        val paragraphs = remember(item, analysis) { AiNarrative.summaryParagraphs(item, analysis) }
        paragraphs.forEachIndexed { idx, paragraph ->
            if (idx > 0) Spacer(modifier = Modifier.height(6.dp))
            Text(
                // 首段是趋势结论:结论词按「涨势分析」段同一规则高亮，全页只有一种强调方式
                text = highlightTrend(paragraph, if (idx == 0) analysis.trendLabel else null),
                color = AppColors.MainText,
                fontSize = AppTypography.Body,
            )
        }
    }
}

/**
 * 把正文里的结论词标红加粗（规则与 [TrendSection] 一致）。
 * [label] 为 null 或没命中时原样返回——返回类型统一为 AnnotatedString，调用处不必再分支。
 */
private fun highlightTrend(body: String, label: String?) = buildAnnotatedString {
    val key = label.orEmpty()
    val idx = body.indexOf(key)
    if (key.isEmpty() || idx < 0) {
        append(body)
    } else {
        append(body.substring(0, idx))
        withStyle(SpanStyle(color = AppColors.RiseRed, fontWeight = FontWeight.Bold)) { append(key) }
        append(body.substring(idx + key.length))
    }
}

/** 走势区加载骨架:占住与图表同高的位置,避免揭示过程中高度跳变。 */
@Composable
private fun ChartSkeleton() {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp).border(1.dp, AppColors.Border, AppShapes.Card),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "走势加载中", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
    }
}

/** 走势取数失败:明确说失败并给重试出口,不退回「看起来像真数据」的示意曲线。 */
@Composable
private fun ChartFailBox(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp)
            .border(1.dp, AppColors.Border, AppShapes.Card)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "走势加载失败 · 点此重试", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
    }
}

/** 相关资讯行:标题 + 来源口径。 */
@Composable
private fun NewsRow(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        // 靛蓝 accent 竖条:资讯条目的视觉锚点
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

/** D4 因子明细区:动量/价值/风险分 + 行业 + 相对大盘基准差。 */
@Composable
private fun FactorSection(f: AiFactors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标出量程:三因子不是同一把尺子(动量 0-60 / 价值 0-25 / 风险 0-8),
        // 只写裸分数会让人误读——「价值分 25」其实是满分,「动量分 17」却只是一小半
        SectionRow("动量分", "${f.momentum} / $FACTOR_MOMENTUM_MAX")
        SectionRow("价值分", "${f.value} / $FACTOR_VALUE_MAX")
        SectionRow("风险分", "${f.risk} / $FACTOR_RISK_MAX")
        val rank = if (f.industryRank >= 0) "（组内第 ${f.industryRank + 1}）" else ""
        SectionRow("行业", f.industry + rank)
        // 用带符号格式:这一行没有红绿颜色兜底,用 formatPercent 会把「跑输 1.40%」显示成「1.40%」,
        // 与下方资讯里同源的 benchmarkDelta 说法正好相反
        SectionRow("相对大盘", f.benchmarkDelta?.let { Utils.formatSignedPercent(it) } ?: "—")
    }
}

/** 三因子量程,与 [com.example.task1.data.deriveAiProfile] 的 scoreParts 一致(后端 AiRules 同源)。 */
private const val FACTOR_MOMENTUM_MAX = 60
private const val FACTOR_VALUE_MAX = 25
private const val FACTOR_RISK_MAX = 8

/** 因子单行:左侧固定宽标签 + 右侧值。 */
@Composable
private fun SectionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, color = AppColors.SubGray, fontSize = AppTypography.BodySmall, modifier = Modifier.width(72.dp))
        Text(text = value, color = AppColors.MainText, fontSize = AppTypography.BodySmall)
    }
}

/** 段落窗口插值：progress 越过 [start] 后在 [SEG_WINDOW] 宽度内从 0 到 1。 */
private fun seg(progress: Float, start: Float): Float =
    ((progress - start) / SEG_WINDOW).coerceIn(0f, 1f)
