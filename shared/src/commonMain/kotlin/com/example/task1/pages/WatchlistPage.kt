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
import com.example.task1.components.AiBottomSheet
import com.example.task1.components.BottomNav
import com.example.task1.components.DimensionPickerSheet
import com.example.task1.components.GroupHeader
import com.example.task1.components.MarketOverviewBar
import com.example.task1.components.StockCard
import com.example.task1.components.TabBar
import com.example.task1.data.AiAnalysis
import com.example.task1.data.DataSource
import com.example.task1.data.GroupDimension
import com.example.task1.data.RuleEngineAiProvider
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockGroup
import com.example.task1.data.StockItem
import com.example.task1.data.TencentStockApi
import com.example.task1.data.WatchlistBundle
import com.example.task1.data.deriveAiAnalysis
import com.example.task1.data.groupStocks
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.ModalBottomSheet
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.pullToRefreshItem
import com.tencent.kuikly.compose.material3.rememberPullToRefreshState
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.scale
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 行情 / 自选列表页（主屏）。
 *
 * 通过路由名 "watchlist" 注册，布局自顶向下为：
 *  - 顶部 header：搜索框 + Tab 栏（自选/全球/港股/…），背景 HeaderBg 延伸到状态栏顶端
 *  - 主体 LazyColumn：市场大盘摘要（MarketOverviewBar）+ 股票卡片列表（StockCard）
 *  - 底部固定区域：「分析智窗」栏（AiBottomBar，常驻两态：思考中→全盘建议）+ 底部导航（BottomNav）
 *  - 叠加的 ModalBottomSheet：AI 分析弹层（AiBottomSheet）
 *
 * 数据来自内置的 SampleStockApi；点击卡片选中/展开（仅重点股 compact 露建议），
 * 展开后点建议行弹出单股 AI 分析；点击「查看详情」跳股票详情页。
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
    // 分组维度:长按「分析智窗」呼出维度弹层切换
    var dimension by remember { mutableStateOf(GroupDimension.ACTION) }
    var showDimPicker by remember { mutableStateOf(false) }
    // 主列表数据源三态:OFFLINE(固定数据替身)/LIVE(实时)/CACHE(失败但有缓存);fetchedAt=上次成功更新时间(epoch millis)
    var dataSource by remember { mutableStateOf(DataSource.OFFLINE) }
    var fetchedAt by remember { mutableStateOf(0L) }
    var missingStock by remember { mutableStateOf(0) }   // 实时行情「部分失败」的缺失只数(0=全成功),供角标
    var reloadKey by remember { mutableStateOf(0) }
    // 下拉刷新:refreshing 驱动 PullToRefreshState;listState 供 pullToRefreshItem 监测滚顶
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)
    var selectedTab by remember { mutableStateOf("自选") }
    var showSheet by remember { mutableStateOf(false) }
    var activeStock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    // 当前被选中（展开）的卡片 id；单击卡片选中，点已选中的收回
    var selectedId by remember { mutableStateOf<String?>(null) }
    // 「分析智窗」常驻两态：thinking「思考中……」→ advice「全盘建议」
    var thinking by remember { mutableStateOf(true) }
    var advice by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    // 获取框架级网络模块:本机走腾讯实时行情,失败回退内置样例
    fun network(): NetworkModule = activity.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    // 单一腾讯 API 实例:实时拉取 + 弹层 AiAnalysis(派生)复用;注入 RuleEngineAiProvider(演示模型,预留真 LLM)
    val tencentApi = remember { TencentStockApi(aiProvider = RuleEngineAiProvider, network = { network() }) }

    // 分组摘要文案:按当前维度对分组生成「重点N只」式覆盖描述
    fun buildAdvice(groups: List<StockGroup>): String =
        if (groups.isEmpty()) "暂无自选股"
        else groups.joinToString("、") { g -> "${g.title.replace("股票建议", "")}${g.stocks.size}只" }

    // 加载自选:优先腾讯实时;代码表固定 7 只。
    // 实时拉取成功(非空)→ LIVE;失败且已有展示数据(缓存)→ CACHE 保留上次;首载无缓存失败→ OFFLINE 固定数据替身。
    val fetch: suspend () -> WatchlistBundle = {
        try {
            val live = tencentApi.fetchWatchlist()
            if (live.stocks.isNotEmpty()) {
                missingStock = tencentApi.lastMissing   // 部分成功时保留成功项 + 角标提示缺失数
                live.copy(source = DataSource.LIVE)
            } else {
                // 全失败 → 有缓存则 CACHE,否则固定数据 OFFLINE
                missingStock = 0
                if (stocks.isNotEmpty()) WatchlistBundle(stocks, fetchedAt, DataSource.CACHE)
                else SampleStockApi.fetchWatchlist()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            missingStock = 0
            if (stocks.isNotEmpty()) WatchlistBundle(stocks, fetchedAt, DataSource.CACHE)
            else SampleStockApi.fetchWatchlist()
        }
    }

    // 应用一组拉取结果到页面状态;showThinking 控制首载「思考中」动画(下拉刷新不触发)
    suspend fun applyFetch(showThinking: Boolean) {
        if (showThinking) thinking = true
        val bundle = fetch()
        stocks = bundle.stocks
        fetchedAt = bundle.fetchedAt
        dataSource = bundle.source
        if (showThinking) {
            delay(800)
            thinking = false
        }
    }

    // epoch millis → "HH:mm:ss"(东八区)。无日期库,纯算术偏移;仅作演示级时间标签
    fun formatTime(ms: Long): String {
        val bj = ms + 8 * 3600 * 1000L
        fun two(v: Long) = ((v % 60).toString().padStart(2, '0'))
        val h = ((bj / 3_600_000L) % 24).toString().padStart(2, '0')
        return "$h:${two(bj / 60_000L)}:${two(bj / 1000L)}"
    }
    // 分档触觉：keyboard/medium/light 由 BridgeModule.vibrateShort(type) 支持（现只用默认 heavy）
    fun haptic(type: String) {
        activity.acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).vibrateShort(type)
    }

    // 单击卡片：展开卡片的选区（点已选中的收回）；按压缩放动画由 cardPressed 驱动
    fun openPanel(stock: StockItem) {
        haptic("light")   // 弹层打开的轻震
        activeStock = stock
        scope.launch {
            // 由已加载的个股实时行情+画像派生弹层 AiAnalysis(与卡片口径一致,不再二次网络请求)
            analysis = deriveAiAnalysis(stock)
            showSheet = true
        }
    }

    // 点击卡片「查看详情」：携带股票 code 跳转到股票详情页
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

    // 进入页面/点重试:思考中→(实时/缓存/兜底)加载→显示
    LaunchedEffect(reloadKey) {
        applyFetch(showThinking = true)
    }

    // 智窗建议文案跟随当前维度(切维度/换数据时重新生成摘要)
    LaunchedEffect(dimension, stocks) {
        advice = buildAdvice(groupStocks(stocks, dimension))
    }

    // 顶部状态栏高度（dp）：让 HeaderBg 背景覆盖到状态栏顶端、内容避开状态栏
    val statusBarHeight = LocalConfiguration.current.statusBarHeight

    // 分组值一次性计算:按当前维度对自选股分组(LazyColumn 组头 + 组内卡片复用同一记忆值)
    val groups = remember(stocks, dimension) { groupStocks(stocks, dimension) }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.PageBg)) {
        // 顶部 header：搜索框 + Tab 栏（该区域固定不随列表滚动；背景延伸进状态栏）
        Column(
            modifier = Modifier
                .background(AppColors.HeaderBg)
                .padding(top = (statusBarHeight + 8f).dp)
                .padding(horizontal = 10.dp)
                .padding(bottom = 8.dp),
        ) {
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

        // 主体列表：pull-to-refresh + 状态横幅 + 市场摘要 + 分组卡片；超出可视区预加载 3 项避免滚动空白
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            beyondBoundsItemCount = 3,
        ) {
            // 下拉刷新:必须作首项;任务结束把 refreshing 置 false 让 state 自动归位
            pullToRefreshItem(
                state = pullState,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        applyFetch(showThinking = false)
                        refreshing = false
                    }
                },
                scrollState = listState,
            )
            if (dataSource == DataSource.OFFLINE && stocks.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "实时行情暂不可用，展示示例数据（点此重试）", color = AppColors.RiskText, fontSize = 12.sp)
                    }
                }
            } else if (dataSource == DataSource.CACHE) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "行情非实时（更新于 ${formatTime(fetchedAt)}），点此重试", color = AppColors.RiskText, fontSize = 12.sp)
                    }
                }
            } else if (missingStock > 0) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "部分行情获取失败（$missingStock），点此重试", color = AppColors.RiskText, fontSize = 12.sp)
                    }
                }
            }
            item { MarketOverviewBar() }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            groups.forEach { group ->
                item(key = "h-${dimension.name}-${group.title}") { GroupHeader(group) }
                items(group.stocks, key = { it.id }) { item ->
                    // 每张卡片：单击选中/取消（仅一张展开）；长按拖拽入口已移除
                    var cardPressed by remember { mutableStateOf(false) }
                    val cardScale by animateFloatAsState(if (cardPressed) 0.985f else 1f, tween(120))
                    Box(
                        modifier = Modifier
                            .scale(cardScale)
                            .pointerInput(item) {
                                detectTapGestures(
                                    onPress = { cardPressed = true; tryAwaitRelease(); cardPressed = false },
                                    onTap = {
                                        selectedId = if (selectedId == item.id) null else item.id
                                        haptic("light")   // 点选轻震
                                    },
                                )
                            },
                    ) {
                        StockCard(
                            item = item,
                            selected = selectedId == item.id,
                            onOpenAi = { openPanel(item) },
                            onEnterDetail = { openDetail(item) },
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(50.dp)) }
        }

        // 底部固定区域：「分析智窗」栏（常驻：思考中→全盘建议）+ 底部导航
        Column(modifier = Modifier.background(AppColors.PageBg)) {
            AiBottomBar(
                advice = advice,
                thinking = thinking,
                dimensionLabel = dimension.label,
                onLongPress = { showDimPicker = true },
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

    // 长按「分析智窗」呼出的维度选择弹层:切换分组维度,选中即关闭
    if (showDimPicker) {
        ModalBottomSheet(
            visible = showDimPicker,
            onDismissRequest = { showDimPicker = false },
            containerColor = AppColors.PageBg,
            scrimColor = Color(0x66000000),
        ) {
            DimensionPickerSheet(
                current = dimension,
                onSelect = { dim ->
                    dimension = dim
                    showDimPicker = false
                },
            )
        }
    }
}
