package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.task1.base.BridgeModule
import com.example.task1.base.RecompositionProfilerSetup
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
import com.example.task1.data.FactorThresh
import com.example.task1.data.StockItem
import com.example.task1.data.StockApis
import com.example.task1.data.deriveAiAnalysis
import com.example.task1.data.nowMillis
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.BackHandler
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
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.ui.draw.scale
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.platform.LocalConfiguration
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** AI 抽屉「分析中」下限：保住「AI 确实在算」的感知，也避免一闪而过。 */
private const val ANALYZE_MIN_MS = 900L

/** AI 抽屉「分析中」上限：后端卡住时不能把用户钉在思考态，超时就用本地结论揭晓。 */
private const val ANALYZE_MAX_MS = 2500L

/**
 * AI 抽屉退场静默窗口。
 *
 * 退场动画由 ModalBottomSheet 内部的 visibleState 驱动（时长 250ms，与之对齐）。关闭时先
 * 把 visible 置 false 让动画跑起来，节点要等动画播完（库回调 onDismissRequest）才摘，
 * 摘早了动画会被掐断，表现为「啪」地切走。
 *
 * 这个常量只用于兜底：入场动画还没播完就按返回时，里层的 MutableTransitionState 无法反向
 * （库的 LaunchedEffect 只在 currentState 已 settled 时才接受关闭），动画永远不会收尾，
 * 只能超时摘节点，否则弹层会卡在「visible=false 但一直显示」。
 */
private const val SHEET_EXIT_MS = 260L

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
        // 接入 Recomposition Profiler：采集重组性能数据，输出到 cache/KuiklyProfiler/（调试用）
        RecompositionProfilerSetup.setupAndStart()
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
    val activity = LocalActivity.current
    // 获取框架级网络模块:本机走腾讯实时行情,失败回退内置样例
    fun network(): NetworkModule = activity.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    // 数据源链:自研后端 → 直连腾讯 → 本地样例。降级全部在数据层完成,页面不感知走了哪一层
    val stockApi = remember { StockApis.stocks { network() } }

    // ViewModel：承载行情加载/分组/摘要/智窗建议等业务逻辑，UI 只负责渲染与交互
    val vm: WatchlistViewModel = viewModel { WatchlistViewModel(stockApi) }

    // 以下为纯 UI 状态（弹层开关、选中、下拉刷新等），保留在 Composable
    var showDimPicker by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // 下拉刷新:refreshing 驱动 PullToRefreshState;listState 供 pullToRefreshItem 监测滚顶
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState(isRefreshing = refreshing)
    var selectedTab by remember { mutableStateOf("自选") }
    // AI 抽屉拆成两个正交状态，才能既有退场动画又不重放：
    //  - sheetVisible：传给 ModalBottomSheet，驱动库的进出场动画
    //  - sheetPresent：节点是否留在组合树。必须留到退场动画播完，否则动画被掐断（直接切走）
    //  - closeRequested：已请求关闭，用来区分 onDismissRequest 的两种来源（遮罩点击 / 动画播完）
    var sheetVisible by remember { mutableStateOf(false) }
    var sheetPresent by remember { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    var activeStock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    // AI 抽屉的分析阶段：由后台校正取数的真实进度驱动（不是写死的固定延时）
    var analyzing by remember { mutableStateOf(false) }
    var analyzeJob by remember { mutableStateOf<Job?>(null) }
    // 当前被选中（展开）的卡片 id；单击卡片选中，点已选中的收回
    var selectedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
    /**
     * 打开 AI 抽屉：本地结论先出（秒开、离线可用、与列表同源），再在后台向后端校正。
     *
     * 校正走既有的 [com.example.task1.data.RoutedStockApi.fetchAiAnalysis]（后端 /analysis →
     * 直连腾讯+本地推导 → 样例兜底，降级由路由层负责，不会抛异常）。总时长夹在
     * [ANALYZE_MIN_MS] 与 [ANALYZE_MAX_MS] 之间：太快没有「在算」的感知，太慢不能钉住用户。
     * 后端当前是规则引擎的逐字复刻，数值多半与本地一致 → 不替换、不闪烁；接真 LLM 后这里自动生效。
     */
    fun openPanel(stock: StockItem) {
        haptic("light")   // 弹层打开的轻震
        activeStock = stock
        // 本地结论立即落地：抽屉不等网络就弹出（离线也能用）
        analysis = deriveAiAnalysis(stock)
        closeRequested = false
        sheetPresent = true
        sheetVisible = true
        analyzing = true
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            val startedAt = nowMillis()
            val remote = withTimeoutOrNull(ANALYZE_MAX_MS) { stockApi.fetchAiAnalysis(stock.code) }
            val elapsed = nowMillis() - startedAt
            if (elapsed < ANALYZE_MIN_MS) delay(ANALYZE_MIN_MS - elapsed)
            // 抽屉仍开着 + 仍是同一只票 + 结果确实不同，三者齐备才替换
            if (remote != null && sheetVisible && activeStock?.code == stock.code && remote != analysis) {
                analysis = remote
            }
            if (activeStock?.code == stock.code) analyzing = false
        }
    }

    /**
     * 关闭 AI 抽屉（返回键 / 关闭钮 / 点遮罩共用）。
     *
     * 先把 visible 置 false 让库的退场动画跑起来，节点等动画播完（库回调 onDismissRequest）再摘。
     * 同时**立刻**取消校正任务：任务在退场窗口内回写 analyzing/analysis 会触发重组，把原生
     * 窗口重新顶出来 —— 这正是「收回又弹出」和「加载中按返回又弹出」的成因。
     */
    fun closeSheet() {
        if (closeRequested) return
        closeRequested = true
        analyzeJob?.cancel()
        sheetVisible = false
    }

    /** 摘掉弹层节点：退场动画已播完（库回调），或兜底超时。 */
    fun finishClose() {
        closeRequested = false
        sheetVisible = false
        sheetPresent = false
    }

    // 兜底摘除：入场动画还没播完就按返回时，里层 MutableTransitionState 无法反向，动画永远
    // 不会收尾，只能超时摘节点。正常路径下 onDismissRequest 会先一步摘掉，这里不会触发。
    // key 带上 sheetPresent：重新打开（sheetVisible=true）会让这个 effect 重启并取消待执行摘除。
    LaunchedEffect(sheetVisible, sheetPresent) {
        if (!sheetVisible && sheetPresent) {
            delay(SHEET_EXIT_MS)
            if (sheetPresent && !sheetVisible) {
                finishClose()
            }
        }
    }

    // 点击卡片「查看详情」：携带股票 code 跳转到股票详情页
    fun openDetail(stock: StockItem) {
        val pj = JSONObject()
        pj.put("code", stock.code)
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stockDetail", pj)
    }

    // 弹层内「查看完整报告」：跳转到 AI 报告页；透传当前弹层股票 code，供报告页按股取数
    fun openReport() {
        val pj = JSONObject()
        activeStock?.let { pj.put("code", it.code) }
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("aiReport", pj)
    }

    // 进入页面/点重试:思考中→(实时/缓存/兜底)加载→显示
    LaunchedEffect(reloadKey) {
        vm.load(showThinking = true)
    }

    // 智窗建议已由 VM 统一维护（vm.advice），无需在 UI 层重算。

    // 顶部状态栏高度（dp）：让 HeaderBg 背景覆盖到状态栏顶端、内容避开状态栏
    val statusBarHeight = LocalConfiguration.current.statusBarHeight

    // 「分析智窗」展开态：点击智窗切换（收起=提示文案，展开=全盘建议全文）
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
                // 搜索框：白色胶囊 + 放大镜图形 + 占位文案（点击无动作，预留搜索功能）
                Row(
                    modifier = Modifier.weight(1f).height(38.dp)
                        .background(Color.White, RoundedCornerShape(19.dp))
                        .border(1.dp, AppColors.Border, RoundedCornerShape(19.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchGlyph()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "搜索股票、代码", color = AppColors.SubGray, fontSize = AppTypography.Body)
                }
                // 「搜索」动作：实色靛蓝胶囊按钮，与 AI 主色呼应
                Box(
                    modifier = Modifier.padding(start = 10.dp)
                        .background(AppColors.CtaBg, RoundedCornerShape(17.dp))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(text = "搜索", color = Color.White, fontSize = AppTypography.Body, fontWeight = FontWeight.SemiBold)
                }
            }
            TabBar(selected = selectedTab, onSelect = { selectedTab = it })
        }

        // 主体区（Box）：列表铺满，「分析智窗」悬浮其底部上方
        Box(modifier = Modifier.weight(1f)) {
        // 主体列表：pull-to-refresh + 状态横幅 + 市场摘要 + 分组卡片；超出可视区预加载 3 项避免滚动空白
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            beyondBoundsItemCount = 3,
        ) {
            // 下拉刷新:必须作首项;任务结束把 refreshing 置 false 让 state 自动归位
            pullToRefreshItem(
                state = pullState,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        vm.refresh()
                        refreshing = false
                    }
                },
                scrollState = listState,
            )
            if (vm.dataSource == DataSource.OFFLINE && vm.stocks.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "实时行情暂不可用，展示示例数据（点此重试）", color = AppColors.RiskOrange, fontSize = AppTypography.Caption)
                    }
                }
            } else if (vm.dataSource == DataSource.CACHE) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "行情非实时（更新于 ${formatTime(vm.fetchedAt)}），点此重试", color = AppColors.RiskOrange, fontSize = AppTypography.Caption)
                    }
                }
            } else if (vm.missingStock > 0) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(AppColors.HeaderBg, RoundedCornerShape(8.dp))
                            .clickable { reloadKey++ }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "部分行情获取失败（${vm.missingStock}），点此重试", color = AppColors.RiskOrange, fontSize = AppTypography.Caption)
                    }
                }
            }
            // 顶部摘要读 vm.overview（与列表同源，随刷新一起变），不再是写死的演示数字
            item { MarketOverviewBar(overview = vm.overview) }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            vm.groups.forEach { group ->
                item(key = "h-${vm.dimension.name}-${group.title}") { GroupHeader(group) }
                items(group.stocks, key = { it.id }) { item ->
                    // 每张卡片：单击选中/取消（仅一张展开）；长按拖拽入口已移除
                    var cardPressed by remember { mutableStateOf(false) }
                    val cardScale by animateFloatAsState(if (cardPressed) 0.985f else 1f, tween(120))
                    // lambda 用 remember(item) 缓存：避免父级重组时每次新建 lambda 导致 StockCard 子树无法 skip（重组风暴根因之一）
                    val onOpenAi = remember(item) { { openPanel(item) } }
                    val onEnterDetail = remember(item) { { openDetail(item) } }
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
                                    // 长按股票：直接弹出 AI 分析抽屉（免先展开再点建议行）
                                    onLongPress = {
                                        haptic("medium")
                                        openPanel(item)
                                    },
                                )
                            },
                    ) {
                        StockCard(
                            item = item,
                            selected = selectedId == item.id,
                            onOpenAi = onOpenAi,
                            onEnterDetail = onEnterDetail,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(110.dp)) }   // 底部悬浮「分析智窗」的避让空间
        }

        // 「分析智窗」标准组件（非受控模式：展开态由组件自管理），悬浮在列表上方
        AiBottomBar(
            advice = vm.advice,
            thinking = vm.thinking,
            dimensionLabel = vm.dimension.label,
            onLongPress = { showDimPicker = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
        }   // Box（主体区）结束

        // 底部导航（固定页底；必须在根 Column 内，否则会被当作独立根节点叠到页面顶部）
        BottomNav(selected = "行情")
    }   // Column（根布局）结束

    // AI 分析弹层：由 visible 布尔值控制开关；点遮罩/「×」触发 onDismissRequest
    if (sheetPresent && activeStock != null && analysis != null) {
        ModalBottomSheet(
            visible = sheetVisible,
            // 两种来源：点遮罩（还没请求关闭）→ 开始关闭；退场动画播完（已在关闭中）→ 摘节点
            onDismissRequest = {
                if (closeRequested) finishClose() else closeSheet()
            },
            // 透明容器：顶部圆角由 AiBottomSheet 自绘（ModalBottomSheet 没有 shape 参数）
            containerColor = Color.Transparent,
            scrimColor = Color(0x66000000),
        ) {
            // 返回键必须在弹层内容内注册：fork 的 BackPressHandler.dispatchOnBackEvent() 只执行
            // 回调栈顶（最后注册）的一个，注册在弹层内才能抢在库 DialogContent 的 BackHandler 之前
            // 命中（注册在页面外层则永远是死代码），再由 [closeSheet] 统一驱动「先动画、后摘节点」。
            // 此版本的 BackHandler 没有 enabled 参数，只能靠条件组合来开关。
            BackHandler {
                closeSheet()
            }
            AiBottomSheet(
                analysis = analysis!!,
                stock = activeStock,
                analyzing = analyzing,
                onDismiss = { closeSheet() },
                onViewReport = { openReport() },
            )
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
            // 同上：返回键注册在弹层内容内，才能先于库的 DialogContent 处理
            BackHandler { showDimPicker = false }
            DimensionPickerSheet(
                current = vm.dimension,
                onSelect = { dim ->
                    vm.onDimensionChange(dim)
                    showDimPicker = false
                },
            )
        }
    }
}

/** 搜索放大镜图形：Canvas 手绘（圆环 + 手柄），无需图标资产。 */
@Composable
private fun SearchGlyph() {
    Canvas(modifier = Modifier.size(15.dp)) {
        val strokeW = 1.6.dp.toPx()
        val r = size.minDimension / 2f - strokeW
        drawCircle(
            color = AppColors.SubGray,
            radius = r,
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = Stroke(width = strokeW),
        )
        drawLine(
            color = AppColors.SubGray,
            start = Offset(size.width * 0.42f + r * 0.62f, size.height * 0.42f + r * 0.62f),
            end = Offset(size.width - strokeW, size.height - strokeW),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
