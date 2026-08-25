package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.task1.base.BridgeModule
import com.example.task1.components.AiBottomBar
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.example.task1.components.AiBottomSheet
import com.example.task1.components.BottomNav
import com.example.task1.components.MarketOverviewBar
import com.example.task1.components.StockCard
import com.example.task1.components.TabBar
import com.example.task1.data.AiAnalysis
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.ModalBottomSheet
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Rect
import com.tencent.kuikly.compose.ui.draw.scale
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.boundsInRoot
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.FlowPreview
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 行情 / 自选列表页（主屏）。
 *
 * 通过路由名 "watchlist" 注册，布局自顶向下为：
 *  - 顶部 header：搜索框 + Tab 栏（自选/全球/港股/…）
 *  - 主体 LazyColumn：市场大盘摘要（MarketOverviewBar）+ 股票卡片列表（StockCard）
 *  - 底部固定区域：「分析智窗」栏（AiBottomBar）+ 底部导航（BottomNav）
 *  - 叠加的 ModalBottomSheet：AI 分析弹层（AiBottomSheet）
 *
 * 数据来自内置的 SampleStockApi；点击卡片跳股票详情，点击「分析智窗」/卡片上的 AI 入口弹出分析。
 */
@Page("watchlist")
class WatchlistPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        // 进入页面时先把 Compose 内容设置到容器里
        setContent { WatchlistScreen() }
    }

    // 注册本页的业务桥接模块：供 Compose 内通过 acquireModule 触发原生触觉反馈（vibrateShort）等
    override fun createExternalModules(): Map<String, Module>? {
        val modules = hashMapOf<String, Module>()
        modules[BridgeModule.MODULE_NAME] = BridgeModule()
        return modules
    }
}

@Composable
fun WatchlistScreen() {
    // 页面级状态：列表数据、当前 Tab、AI 弹层开关、弹层所需数据
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("自选") }
    var showSheet by remember { mutableStateOf(false) }
    var activeStock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    // 当前被选中（展开）的卡片 id；单击卡片选中，拖拽也会同步选中
    var selectedId by remember { mutableStateOf<String?>(null) }
    // 拖拽相关：记录「分析智窗」的窗口 Rect、被拖拽的卡片、拖拽位移（尽力而为）
    var barRect by remember { mutableStateOf<Rect?>(null) }
    var draggingStock by remember { mutableStateOf<StockItem?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    // 拖拽中的卡片是否已「接触」分析智窗：驱动放大动画 + 触觉反馈
    var targetBar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    // 卡片中心（初始 root 位置 + 累计位移）是否落入「分析智窗」窗口
    fun hitBar(b: Rect?, bar: Rect?, d: Offset): Boolean {
        if (b == null || bar == null) return false
        val cx = b.left + d.x + b.width / 2
        val cy = b.top + d.y + b.height / 2
        return cx >= bar.left && cx <= bar.right && cy >= bar.top && cy <= bar.bottom
    }

    // 「分析智窗」接触时的放大动画
    val barScale by animateFloatAsState(
        targetValue = if (targetBar) 1.06f else 1f,
        animationSpec = tween(150),
    )

    // 进入页面时加载自选列表（内置样例数据，后续可切换真实行情）
    LaunchedEffect(Unit) { stocks = SampleStockApi.fetchWatchlist() }

    // 打开 AI 分析弹层：先取分析数据，再弹出
    fun openPanel(stock: StockItem) {
        activeStock = stock
        scope.launch {
            analysis = SampleStockApi.fetchAiAnalysis(stock.code)
            showSheet = true
        }
    }

    // 点击卡片：携带股票 code 跳转到股票详情页
    fun openDetail(stock: StockItem) {
        val pj = JSONObject()
        pj.put("code", stock.code)
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stockDetail", pj)
    }

    // 弹层内「查看完整报告」：跳转到 AI 报告页
    fun openReport() {
        val pj = JSONObject()
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("aiReport", pj)
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.PageBg)) {
        // 顶部 header：搜索框 + Tab 栏（该区域固定不随列表滚动）
        Column(modifier = Modifier.background(AppColors.HeaderBg).padding(horizontal = 10.dp).padding(top = 16.dp).padding(bottom = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 搜索框：空白色圆角胶囊 + 右侧「搜索」动作（设计稿 18:241 = 白色圆角矩形 + 右侧「搜索」文字，无占位符/无图标）
                Box(
                    modifier = Modifier.weight(1f).height(34.dp)
                        .background(Color.White, RoundedCornerShape(17.dp))
                        .border(1.dp, AppColors.Border, RoundedCornerShape(17.dp)),
                )
                Text(text = "搜索", color = AppColors.MainText, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).clickable { })
            }
            TabBar(selected = selectedTab, onSelect = { selectedTab = it })
        }

        // 主体列表：市场摘要 + 股票卡片；超出可视区预加载 3 项避免滚动空白
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondBoundsItemCount = 3,
        ) {
            item { MarketOverviewBar() }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            items(stocks, key = { it.id }) { item ->
                // 每张卡片：在布局回调里解析 root 坐标（boundsInRoot 已实现；boundsInWindow 在此版本恒抛 segqwe，必须避开）。
                // offset 实现拖拽时的视觉位移；pointerInput 以 item 为 key 避免 LazyList 复用错乱。
                var base by remember { mutableStateOf<Rect?>(null) }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { it ->
                            // 仅在该卡片未被拖拽时更新：冻结在 offset=0 的原始位置，避免与 offset+dragDelta 重复计位
                            if (draggingStock?.id != item.id) {
                                base = try { it.boundsInRoot() } catch (e: Throwable) { base }
                            }
                        }
                        .offset {
                            if (draggingStock?.id == item.id) IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt())
                            else IntOffset(0, 0)
                        }
                        .pointerInput(item) {
                            detectTapGestures(onTap = { selectedId = item.id })
                        }
                        .pointerInput(item) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingStock = item
                                    selectedId = item.id   // 拖拽同步选中
                                    dragDelta = Offset.Zero
                                    targetBar = false
                                },
                                onDrag = { _, amount ->
                                    dragDelta += amount
                                    // 卡片中心接触「分析智窗」：进入瞬间触发一次震动，并持续驱动放大动画
                                    val hit = hitBar(base, barRect, dragDelta)
                                    if (hit && !targetBar) {
                                        activity.acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).vibrateShort()
                                    }
                                    targetBar = hit
                                },
                                onDragEnd = {
                                    // 命中判断：卡片中心（初始 root 位置 + 累计位移）是否落在「分析智窗」的窗口范围内
                                    if (hitBar(base, barRect, dragDelta)) openPanel(item)
                                    draggingStock = null
                                    targetBar = false
                                },
                                onDragCancel = {
                                    draggingStock = null
                                    targetBar = false
                                },
                            )
                        }
                ) {
                    StockCard(
                        item = item,
                        selected = selectedId == item.id,
                        onOpenAi = { openPanel(item) },
                        onEnterDetail = { openDetail(item) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(90.dp)) }
        }

        // 底部固定区域：「分析智窗」栏（记录其窗口坐标供拖拽命中）+ 底部导航
        Column(modifier = Modifier.background(AppColors.PageBg)) {
            AiBottomBar(
                onClick = {
                    (stocks.find { it.id == selectedId } ?: stocks.firstOrNull())?.let { openPanel(it) }
                },
                showSparkline = selectedId != null || draggingStock != null || targetBar,
                // 用 boundsInRoot 解析窗口坐标（与卡片命中判定同一 root 坐标系）；静态栏无 offset，无需 guard。
                // scale 放在 onGloballyPositioned 之后（内层）：布局坐标不随缩放改变，命中区域保持稳定，仅做视觉放大。
                modifier = Modifier.onGloballyPositioned { it ->
                    barRect = try { it.boundsInRoot() } catch (e: Throwable) { barRect }
                }.scale(barScale),
            )
            BottomNav(selected = "行情")
        }
    }

    // AI 分析弹层：由 visible 布尔值控制开关；点遮罩/「×」触发 onDismissRequest
    if (showSheet && activeStock != null && analysis != null) {
        ModalBottomSheet(
            visible = showSheet,
            onDismissRequest = { showSheet = false },
            containerColor = AppColors.PageBg,
            scrimColor = Color(0x66000000),
        ) {
            AiBottomSheet(analysis = analysis!!, stock = activeStock, onDismiss = { showSheet = false }, onViewReport = { openReport() })
        }
    }
}
