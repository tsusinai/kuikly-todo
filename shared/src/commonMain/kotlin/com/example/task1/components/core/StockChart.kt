package com.example.task1.components.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.base.Utils
import com.example.task1.data.ChartMetric
import com.example.task1.data.ChartPeriod
import com.example.task1.data.ChartStyle
import com.example.task1.data.StockChartData
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.tencent.kuikly.compose.foundation.gestures.detectHorizontalDragGestures
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.StrokeJoin
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.graphics.drawscope.clipRect
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 标准走势图表组件（股票详情页 / 后续任何需要 K 线走势的位置都可复用）。
 *
 * 三个维度可组合切换，新增维度只需往枚举里加项：
 * - 周期 [ChartPeriod]：由调用方持有（切周期通常要重新取数），配合 [ChartPeriodTabs] 使用
 * - 样式 [ChartStyle]：蜡烛 / 线图 / 面积；分时周期固定线图，样式入口置灰
 * - 指标 [ChartMetric]：均线叠加 / 成交量子图，可独立开关
 *
 * 部分受控：[style] / [metrics] 传 null 时组件内部持有并管理（调用方一行代码即可用），
 * 传入时按外部值渲染并通过 [onStyleChange] / [onMetricsChange] 回调外部。
 * 选点（十字线）状态始终由组件内部管理。
 *
 * 绘制约束：Kuikly 的 Canvas 没有 drawText，所有刻度与信息条都用 Text 叠加；
 * 也没有虚线效果，网格为浅色实线。
 */
@Composable
fun StockChart(
    data: StockChartData,
    modifier: Modifier = Modifier,
    style: ChartStyle? = null,
    onStyleChange: ((ChartStyle) -> Unit)? = null,
    metrics: Set<ChartMetric>? = null,
    onMetricsChange: ((Set<ChartMetric>) -> Unit)? = null,
    /** 左右滑动图表切周期:参数是 ±1 的步进,周期顺序与边界由调用方决定(它才持有 [ChartPeriod])。 */
    onPeriodStep: ((Int) -> Unit)? = null,
    mainHeight: Dp = 180.dp,
    volumeHeight: Dp = 54.dp,
    showDimensionBar: Boolean = true,
) {
    val intraday = data.period == ChartPeriod.INTRADAY

    var innerStyle by remember { mutableStateOf(ChartStyle.CANDLE) }
    val activeStyle = if (intraday) ChartStyle.LINE else style ?: innerStyle
    val changeStyle: (ChartStyle) -> Unit = { next ->
        if (style == null) innerStyle = next
        onStyleChange?.invoke(next)
    }

    var innerMetrics by remember { mutableStateOf(setOf(ChartMetric.MA, ChartMetric.VOLUME)) }
    val activeMetrics = metrics ?: innerMetrics
    val toggleMetric: (ChartMetric) -> Unit = { target ->
        val next = if (target in activeMetrics) activeMetrics - target else activeMetrics + target
        if (metrics == null) innerMetrics = next
        onMetricsChange?.invoke(next)
    }

    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }

    // 入场揭示:只在换周期(数据变)时重放。样式切换不重放——蜡烛/线图/面积看的是同一段走势,
    // 点一下 chip 就把整幅走势擦一遍属于「动画加错地方」。
    // 动画值一律不在组合期读:读 progress.value 等于每帧重组整个图表。
    // 画布在绘制期读它们,于是一帧只重绘,不重组也不 relayout。
    var appeared by remember(data) { mutableStateOf(false) }
    LaunchedEffect(data) { appeared = true }
    val progress = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(ENTER_REVEAL_MILLIS),
    )

    // 成交量 / 均线开关的「舒展」:副图与均线一起长出来、缩回去,动画只在绘制与图层透明度上。
    // 布局在两端各动一次(展开先占位再长出来,收起等缩完再撤占位),中间帧布局不动——
    // 每帧改高度会让整页 relayout,那正是切 chip 时的卡顿来源。
    val volumeOn = ChartMetric.VOLUME in activeMetrics
    val maOn = ChartMetric.MA in activeMetrics && data.ma.isNotEmpty()
    val volumeReveal = remember { Animatable(if (volumeOn) 1f else 0f) }
    val maReveal = remember { Animatable(if (maOn) 1f else 0f) }
    var volumeSlot by remember { mutableStateOf(volumeOn) }
    var maSlot by remember { mutableStateOf(maOn) }
    LaunchedEffect(volumeOn) {
        if (volumeOn) {
            volumeSlot = true
            volumeReveal.animateTo(1f, tween(METRIC_REVEAL_MILLIS))
        } else {
            volumeReveal.animateTo(0f, tween(METRIC_REVEAL_MILLIS))
            volumeSlot = false
        }
    }
    LaunchedEffect(maOn) {
        if (maOn) {
            maSlot = true
            maReveal.animateTo(1f, tween(METRIC_REVEAL_MILLIS))
        } else {
            maReveal.animateTo(0f, tween(METRIC_REVEAL_MILLIS))
            maSlot = false
        }
    }

    // 切周期入画:新周期从滑动方向那一侧滑进来;首次进入方向为 0,只做揭示不做位移
    val periodIndex = ChartPeriod.entries.indexOf(data.period)
    val slideCache = remember { intArrayOf(periodIndex, 0) }
    if (slideCache[0] != periodIndex) {
        slideCache[1] = if (periodIndex > slideCache[0]) 1 else -1
        slideCache[0] = periodIndex
    }
    val slide = remember(data) { Animatable(slideCache[1] * SLIDE_IN_DP) }
    LaunchedEffect(slide) { slide.animateTo(0f, tween(260)) }

    val stepPeriod = onPeriodStep
    val swipeModifier = if (stepPeriod == null) {
        Modifier
    } else {
        Modifier.pointerInput(data.period) {
            var acc = 0f
            var fired = false
            val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f; fired = false },
                onDragEnd = { acc = 0f },
                onDragCancel = { acc = 0f },
            ) { _, dragAmount ->
                // 十字线已经竖起时不接管:长按拖动看盘不该被误判成切周期
                if (fired || selectedIndex != null) return@detectHorizontalDragGestures
                acc += dragAmount
                if (abs(acc) >= threshold) {
                    fired = true
                    stepPeriod(if (acc < 0f) 1 else -1)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // px 版 offset:位移在测量/图层阶段生效,切周期滑入时不重组也不重绘
            .offset { IntOffset(slide.value.dp.roundToPx(), 0) }
            .then(swipeModifier),
    ) {
        // 样式 / 指标放整幅图上方:样式一行在上,指标一行在下
        if (showDimensionBar) {
            ChartStyleChips(
                style = activeStyle,
                onStyleChange = changeStyle,
                metrics = activeMetrics,
                onToggleMetric = toggleMetric,
                styleEnabled = !intraday,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        SelectionBar(data = data, index = selectedIndex)
        Spacer(modifier = Modifier.height(6.dp))
        if (data.isEmpty) {
            Box(
                modifier = Modifier.fillMaxWidth().height(mainHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "暂无数据", color = AppColors.SubGray, fontSize = AppTypography.BodySmall)
            }
        } else {
            // 均线值始终参与价格区间(哪怕均线被关掉):开关只影响可见度,
            // 否则一按开关整幅主图会瞬间重新缩放,那个「跳一下」比动画本身更扎眼
            val (low, high) = priceRangeOf(data, intraday, data.ma.isNotEmpty())
            Row(modifier = Modifier.fillMaxWidth()) {
                MainChartCanvas(
                    data = data,
                    style = activeStyle,
                    showMa = maSlot,
                    intraday = intraday,
                    low = low,
                    high = high,
                    progress = { progress.value },
                    maReveal = { maReveal.value },
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    modifier = Modifier.weight(1f).height(mainHeight),
                )
                AxisColumn(
                    values = axisValues(low, high),
                    height = mainHeight,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TimeAxisRow(data = data)
            if (volumeSlot) {
                // 副图栏位本身也长出来 / 缩回去:高度不这么动,下面那行 chip 会「跳」一下
                RevealHeight(
                    reveal = { volumeReveal.value },
                    height = volumeHeight,
                    gap = METRIC_GAP,
                ) { h ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        VolumeCanvas(
                            data = data,
                            progress = { progress.value },
                            selectedIndex = selectedIndex,
                            modifier = Modifier.weight(1f).height(h),
                        )
                        RevealLayer(reveal = { volumeReveal.value }) {
                            AxisColumn(
                                values = listOf(formatVolume(data.candles.maxOf { it.volume }), "", ""),
                                height = h,
                            )
                        }
                    }
                }
            }
            if (maSlot) {
                RevealHeight(
                    reveal = { maReveal.value },
                    height = LEGEND_HEIGHT,
                    gap = METRIC_GAP,
                ) {
                    RevealLayer(reveal = { maReveal.value }) { MaLegend(data = data) }
                }
            }
        }
    }
}

/** 选点信息条：未选中显示最新一根，选中显示十字线所在那一根。点击空白清除选点。 */
@Composable
private fun SelectionBar(data: StockChartData, index: Int?) {
    val target = index ?: (data.candles.size - 1)
    val candle = data.candles.getOrNull(target)
    if (candle == null) {
        Spacer(modifier = Modifier.height(SELECTION_BAR_HEIGHT))
        return
    }
    // 分时看的是当日涨跌（对昨收），K 线看的是与前一根的对比
    val base = when {
        data.period == ChartPeriod.INTRADAY -> data.prevClose
        target > 0 -> data.candles[target - 1].close
        else -> data.prevClose
    }
    val pct = if (base != 0L) (candle.close - base) * 100.0 / base else 0.0
    val trendColor = if (pct >= 0) AppColors.RiseRed else AppColors.Green
    val picked = index != null

    Row(
        modifier = Modifier.fillMaxWidth().height(SELECTION_BAR_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (picked) candle.label else "最新",
            color = if (picked) AppColors.MainText else AppColors.SubGray,
            fontSize = AppTypography.Tiny,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = "开${Utils.formatPrice2(candle.open)} 高${Utils.formatPrice2(candle.high)} " +
                "低${Utils.formatPrice2(candle.low)} 收${Utils.formatPrice2(candle.close)}",
            color = AppColors.SubGray,
            fontSize = AppTypography.Tiny,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        Text(
            text = Utils.formatPercent(pct),
            color = trendColor,
            fontSize = AppTypography.Tiny,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 均线图例：三条均线的最新值，色点与主图颜色一致。 */
@Composable
private fun MaLegend(data: StockChartData) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MA_COLORS.forEach { (window, color) ->
            val value = data.ma[window]?.lastOrNull { it != null } ?: return@forEach
            Box(modifier = Modifier.size(width = 6.dp, height = 6.dp).background(color, AppShapes.Pill))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "MA$window ${Utils.formatPrice2(value)}",
                color = AppColors.SubGray,
                fontSize = 9.sp,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
    }
}

/** 渐显 / 渐隐:透明度在图层阶段读取,动画既不触发重组也不重新测量。 */
@Composable
private fun RevealLayer(reveal: () -> Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = reveal().coerceIn(0f, 1f)
        },
    ) {
        content()
    }
}

/**
 * 展开 / 收起:高度从 0 长到 [height](连上方 [gap] 的间距一起长),收起时缩回 0。
 *
 * 高度只在这里读,动画期间每帧只重排这一块自己,不会让整幅图表重组;
 * 开关一按,下面那行 chip 跟着上移 / 下移,而不是「跳」一下。
 */
@Composable
private fun RevealHeight(
    reveal: () -> Float,
    height: Dp,
    gap: Dp,
    content: @Composable (Dp) -> Unit,
) {
    val r = reveal().coerceIn(0f, 1f)
    val h = height * r
    Box(
        modifier = Modifier.fillMaxWidth().height(h + gap * r),
        contentAlignment = Alignment.BottomStart,
    ) {
        content(h)
    }
}

/** 主图：网格 + 数据层（按 [progress] 从左到右揭示）+ 十字线。 */
@Composable
private fun MainChartCanvas(
    data: StockChartData,
    style: ChartStyle,
    showMa: Boolean,
    intraday: Boolean,
    low: Float,
    high: Float,
    progress: () -> Float,
    maReveal: () -> Float,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier,
) {
    Canvas(
        modifier = modifier
            .pointerInput(data) {
                detectTapGestures { offset ->
                    onSelect(indexAt(offset.x, size.width, data.candles.size))
                }
            }
            .pointerInput(data) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onSelect(indexAt(offset.x, size.width, data.candles.size)) },
                    onDrag = { change, _ -> onSelect(indexAt(change.position.x, size.width, data.candles.size)) },
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) },
                )
            },
    ) {
        drawGrid()
        clipRect(right = size.width * progress().coerceIn(0f, 1f)) {
            drawMainSeries(data, style, showMa, intraday, low, high, maReveal = maReveal())
        }
        drawCrosshair(data, low, high, selectedIndex, style == ChartStyle.CANDLE)
    }
}

/** 成交量子图：柱色跟随该根 K 线涨跌；高度由展开动画给，柱子自然从基线长出来 / 缩回去。 */
@Composable
private fun VolumeCanvas(
    data: StockChartData,
    progress: () -> Float,
    selectedIndex: Int?,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val n = data.candles.size
        if (n == 0) return@Canvas
        val w = size.width
        val h = size.height
        val maxVolume = data.candles.maxOf { it.volume }.coerceAtLeast(1L)
        val columnWidth = w / n
        val bodyWidth = (columnWidth * 0.62f).coerceAtLeast(1.5f)

        clipRect(right = w * progress().coerceIn(0f, 1f)) {
            data.candles.forEachIndexed { i, candle ->
                val ratio = candle.volume.toFloat() / maxVolume
                val barHeight = max(h * ratio, 1f)
                val x = (i + 0.5f) * columnWidth - bodyWidth / 2f
                drawRect(
                    color = if (candle.isUp) AppColors.RiseRed else AppColors.Green,
                    topLeft = Offset(x, h - barHeight),
                    size = Size(bodyWidth, barHeight),
                    alpha = 0.65f,
                )
            }
        }
        selectedIndex?.let { i ->
            if (i in data.candles.indices) {
                val x = (i + 0.5f) * columnWidth
                drawLine(
                    color = AppColors.SubGray,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

/** 价格刻度列：5 档，与主图网格线对齐（首尾也是网格上下边界）。 */
@Composable
private fun AxisColumn(values: List<String>, height: Dp) {
    Column(
        // 不加上下 padding：让首尾文字的中心落在网格上下边界上（SpaceBetween 会把行高一半留在外侧）
        modifier = Modifier.width(AXIS_WIDTH).height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        values.forEach { value ->
            Text(
                text = value,
                color = AppColors.ChartAxis,
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** 时间轴：从每根 K 线自带的 label 里等距抽 5 档。 */
@Composable
private fun TimeAxisRow(data: StockChartData) {
    val n = data.candles.size
    if (n == 0) return
    val indices = listOf(0, n / 4, n / 2, n * 3 / 4, n - 1)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                indices.forEach { i ->
                    Text(text = data.candles[i].label, color = AppColors.ChartAxis, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.width(AXIS_WIDTH))
    }
}

/** 网格：3 条水平 + 3 条垂直浅色实线，不画外框（外框由价格刻度首尾隐式给出）。 */
private fun DrawScope.drawGrid() {
    val w = size.width
    val h = size.height
    val top = AXIS_PAD_V.toPx()
    val bottom = h - AXIS_PAD_V.toPx()
    for (k in 1..3) {
        val y = top + (bottom - top) * k / 4f
        drawLine(color = AppColors.ChartGrid, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
    }
    for (k in 1..3) {
        val x = w * k / 4f
        drawLine(color = AppColors.ChartGrid, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = 1f)
    }
}

/** 数据层：分时线（含均价线与昨收基准）/ 蜡烛 / 收盘线 / 面积，外加均线叠加。 */
private fun DrawScope.drawMainSeries(
    data: StockChartData,
    style: ChartStyle,
    showMa: Boolean,
    intraday: Boolean,
    low: Float,
    high: Float,
    maReveal: Float,
) {
    val n = data.candles.size
    if (n == 0) return
    val w = size.width
    val h = size.height
    val top = AXIS_PAD_V.toPx()
    val bottom = h - AXIS_PAD_V.toPx()
    val range = (high - low).coerceAtLeast(1f)
    val columnWidth = w / n

    fun yOf(value: Long): Float = bottom - (value - low) / range * (bottom - top)
    // 蜡烛按列中心排布，折线类按等分点排布（首尾贴边）
    fun xOf(i: Int): Float = when {
        n == 1 -> w / 2f
        style == ChartStyle.CANDLE -> (i + 0.5f) * columnWidth
        else -> i * w / (n - 1)
    }

    val rising = data.candles.last().close >= data.candles.first().open
    val trendColor = if (rising) AppColors.RiseRed else AppColors.Green

    if (intraday || style != ChartStyle.CANDLE) {
        val linePath = Path()
        data.candles.forEachIndexed { i, candle ->
            val x = xOf(i)
            val y = yOf(candle.close)
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        // 分时与面积样式填充渐变，线图保持干净
        if (intraday || style == ChartStyle.AREA) {
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(xOf(n - 1), bottom)
                lineTo(xOf(0), bottom)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(trendColor.copy(alpha = 0.22f), trendColor.copy(alpha = 0.02f)),
                    startY = top,
                    endY = bottom,
                ),
            )
        }
        drawPath(
            path = linePath,
            color = trendColor,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    if (intraday) {
        // 昨收基准线：分时判断强弱的参照
        val yPrev = yOf(data.prevClose)
        drawLine(
            color = AppColors.SubGray.copy(alpha = 0.5f),
            start = Offset(0f, yPrev),
            end = Offset(w, yPrev),
            strokeWidth = 1f,
        )
        if (data.avgPrice.size == n) {
            val avgPath = Path()
            data.avgPrice.forEachIndexed { i, value ->
                val x = xOf(i)
                val y = yOf(value)
                if (i == 0) avgPath.moveTo(x, y) else avgPath.lineTo(x, y)
            }
            drawPath(
                path = avgPath,
                color = AppColors.Ma5,
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    } else if (style == ChartStyle.CANDLE) {
        data.candles.forEachIndexed { i, candle ->
            val color = if (candle.isUp) AppColors.RiseRed else AppColors.Green
            val x = xOf(i)
            drawLine(
                color = color,
                start = Offset(x, yOf(candle.high)),
                end = Offset(x, yOf(candle.low)),
                strokeWidth = 1f,
            )
            val yOpen = yOf(candle.open)
            val yClose = yOf(candle.close)
            val bodyWidth = (columnWidth * 0.62f).coerceAtLeast(1.5f)
            drawRect(
                color = color,
                topLeft = Offset(x - bodyWidth / 2f, min(yOpen, yClose)),
                size = Size(bodyWidth, max(abs(yClose - yOpen), 1f)),
            )
        }
    }

    if (showMa) {
        data.ma.forEach { (window, series) ->
            val color = when (window) {
                5 -> AppColors.Ma5
                10 -> AppColors.Ma10
                20 -> AppColors.Ma20
                else -> AppColors.SubGray
            }
            val path = Path()
            var started = false
            series.forEachIndexed { i, value ->
                if (value != null) {
                    val x = xOf(i)
                    val y = yOf(value)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
            }
            if (started) {
                drawPath(
                    path = path,
                    // 均线随开关渐显 / 渐隐,和副图柱子的舒展同一拍
                    color = color.copy(alpha = maReveal.coerceIn(0f, 1f)),
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** 十字线：竖线 + 横线 + 圆点。绘制在裁剪区之外，所以揭示动画不会截断它。 */
private fun DrawScope.drawCrosshair(
    data: StockChartData,
    low: Float,
    high: Float,
    selectedIndex: Int?,
    candleLayout: Boolean,
) {
    val index = selectedIndex ?: return
    val candle = data.candles.getOrNull(index) ?: return
    val n = data.candles.size
    val w = size.width
    val h = size.height
    val top = AXIS_PAD_V.toPx()
    val bottom = h - AXIS_PAD_V.toPx()
    val range = (high - low).coerceAtLeast(1f)
    val x = when {
        n == 1 -> w / 2f
        candleLayout -> (index + 0.5f) * (w / n)
        else -> index * w / (n - 1)
    }
    val y = bottom - (candle.close - low) / range * (bottom - top)

    drawLine(color = AppColors.SubGray, start = Offset(x, top), end = Offset(x, bottom), strokeWidth = 1f)
    drawLine(color = AppColors.SubGray, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
    drawCircle(color = AppColors.MainText, radius = 2.5.dp.toPx(), center = Offset(x, y))
}

/** 主图价格范围：均线值一并纳入（分时也有均线了），分时再把昨收纳入并以它对称。 */
private fun priceRangeOf(data: StockChartData, intraday: Boolean, withMa: Boolean): Pair<Float, Float> {
    if (data.candles.isEmpty()) return 0f to 1f
    var low = Float.MAX_VALUE
    var high = -Float.MAX_VALUE
    data.candles.forEach { candle ->
        low = min(low, candle.low.toFloat())
        high = max(high, candle.high.toFloat())
    }
    if (withMa) {
        data.ma.values.forEach { series ->
            series.forEach { value ->
                if (value != null) {
                    low = min(low, value.toFloat())
                    high = max(high, value.toFloat())
                }
            }
        }
    }
    if (intraday) {
        data.avgPrice.forEach { value ->
            low = min(low, value.toFloat())
            high = max(high, value.toFloat())
        }
        val prev = data.prevClose.toFloat()
        low = min(low, prev)
        high = max(high, prev)
        val span = max(high - prev, prev - low)
        low = prev - span
        high = prev + span
    }
    if (high <= low) high = low + 1f
    return low to high
}

/** 价格刻度 5 档：最高在顶、最低在底，与网格的四等分位置对齐。 */
private fun axisValues(low: Float, high: Float): List<String> {
    val step = (high - low) / 4f
    return List(5) { i -> Utils.formatPrice2((high - step * i).roundToLong()) }
}

private fun indexAt(x: Float, widthPx: Int, count: Int): Int? {
    if (count <= 0 || widthPx <= 0) return null
    val ratio = (x / widthPx.toFloat()).coerceIn(0f, 1f)
    return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

private fun formatVolume(hand: Long): String = when {
    hand >= 100_000_000L -> "${hand / 100_000_000L}亿手"
    hand >= 10_000L -> "${hand / 10_000L}万手"
    else -> "${hand}手"
}

/** 入场揭示时长(ms):换周期时走势从左到右擦出。 */
private const val ENTER_REVEAL_MILLIS = 420

/** 成交量 / 均线开关的舒展时长(ms):短于主图揭示,手感上是「嗒」一下弹开而不是慢慢铺。 */
private const val METRIC_REVEAL_MILLIS = 240

/** 切周期滑动阈值(dp):超过就切一档。取值偏大,避免与「长按拖十字线」抢手势。 */
private const val SWIPE_THRESHOLD_DP = 44f

/** 切周期入画的水平位移幅度(dp)。 */
private const val SLIDE_IN_DP = 14f

private val AXIS_WIDTH = 52.dp
private val AXIS_PAD_V = 6.dp
private val SELECTION_BAR_HEIGHT = 18.dp

/** 主图与副图 / 副图与均线图例之间的行距。 */
private val METRIC_GAP = 6.dp

/** 均线图例行的名义高度:只用于展开动画的节奏,实际高度仍是内容自身量出来的。 */
private val LEGEND_HEIGHT = 16.dp

private val MA_COLORS = listOf(
    5 to AppColors.Ma5,
    10 to AppColors.Ma10,
    20 to AppColors.Ma20,
)
