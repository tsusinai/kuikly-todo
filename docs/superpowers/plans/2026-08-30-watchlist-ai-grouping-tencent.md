# 自选页 AI 分组排行 + 腾讯实时行情 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把自选页（`WatchlistPage`）从扁平列表改为「AI 分析驱动分组排行(4 维可切换,长按智窗换维度)」，并把行情数据源接到腾讯实时接口。

**Architecture:** 数据层加 `AiProfile`(四维画像)+ `groupStocks()` 纯函数做分组;`TencentStockApi` 用框架级 `NetworkModule.requestGet` 拉 `qt.gtimg.cn`,非 JSON 回包按 `{"data":…}` 取原文解析;AI 画像由本地规则引擎 `deriveAiProfile()` 从实时行情推导。页面用 `LazyColumn` 渲染「分组标题 + 组内卡片」;底部「分析智窗」长按弹维度选择 `ModalBottomSheet`。失败回退 `SampleStockApi`。

**Tech Stack:** Kotlin Multiplatform + Kuikly Compose DSL。网络用框架级 `NetworkModule`(宿主自动提供),不引入新依赖。**没有测试源集**(只有 `commonMain`),故验证门槛是**编译通过**。

---

## Global Constraints

逐条复制自 `CLAUDE.md` + `rules/kuiklyComposeDSL.mdc` + 本项目既有坑 —— 每个任务都隐式包含这些:

- **包规则:** 仅 `androidx.compose.runtime.*` 用官方包;其余一律 `com.tencent.kuikly.compose.*`(`Modifier`/`Column`/`Text`/`Color`/`Alignment`…)。
- **页面**继承 `com.tencent.kuikly.compose.ComposeContainer`,注解 `@Page("name")`,override `willInit(){ super.willInit(); setContent { … } }`。`setContent` 单独 import `com.tencent.kuikly.compose.setContent`。
- `LazyColumn`:加 `beyondBoundsItemCount = 3`;不用 `reverseLayout`/`flingBehavior`。
- **不用** `fadeIn`/`fadeOut`/`scaleIn`/`scaleOut`(layerBlock bug);动画用 `AnimatedVisibility` + `expandVertically/shrinkVertically`。无 `animateColorAsState`。
- **ModalBottomSheet** 按 4 参用:`ModalBottomSheet(visible, onDismissRequest, containerColor, scrimColor)`。
- **`pointerInput` 必须传 key**;闭包内读可变 state 用 `remember { mutableStateOf }`。
- **长按交互**用 `detectTapGestures(onLongPress = …)`(fork 镜像 compose,已确认该用法的上/上两个重载存在;若编译失败则退回 `pointerInput` + `awaitLongPressOrCancellation` 自实现)。**勿引入拖拽**(拖进智窗此前已移除)。
- **网络:** `import com.tencent.kuikly.core.module.NetworkModule`;`NetworkResponse(dataClass: headerFields: JSONObject, statusCode: Int?)`;`NMAllResponse = (data: JSONObject, success: Boolean, errorMsg: String, response: NetworkResponse) -> Unit`。非 JSON 回包被包为 `{"data":"原始内容"}` → 用 `data.optString("data")`。`NetworkModule` 用 `acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)` 获取(框架级,宿主自动提供,无需 override `createExternalModules`)。
- **编译门槛:** `./gradlew :shared:compileDebugKotlinAndroid`(JDK17 由 `org.gradle.java.home` 设)。**无测试源集 → 唯一的验证是编译成功。**
- **无 git commit(除非用户明确要求)**。

### 关键真实 API(本次已在解析产物 `2.7.0-2.1.21` 源码核对)
- `NetworkModule`:`com.tencent.kuikly.core.module.NetworkModule`,有 `requestGet(url, param=JSONObject, responseCallback: NMAllResponse)` 与 `httpRequest(url, isPost, param, headers=null, cookie=null, timeout=30, responseCallback: NMAllResponse)`。`MODULE_NAME = ModuleConst.NETWORK`。common 源码位于 `core`/`core-android` sources jar 的 `commonMain/.../module/NetworkModule.kt`。
- `NetworkResponse(headerFields: JSONObject, statusCode: Int? = null)`。
- 现有组件 `AppIcon(name, modifier)`、`AppColors` token(`MainText`/`SubGray`/`AiLight`/`AiBadgeBg`/`RiseRed`/`Green`/`Border`/`CtaBg`…)在 `theme/AppColors.kt`。

---

## Task 1: 数据模型与分组纯函数

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/data/StockGrouping.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/task1/data/StockApi.kt`(StockItem 加 `aiProfile`;SampleStockApi 的 7 支各补 `aiProfile`)

**Interfaces:**
- Produces: `data class AiProfile(action: String, signal: String, score: Int, scenario: String)`;`enum class GroupDimension(val label: String) { ACTION("操作建议"), SIGNAL("信号题材"), SCORE("评分分层"), SCENARIO("操作场景") }`;`data class StockGroup(val title: String, val stocks: List<StockItem>)`;`fun groupStocks(stocks: List<StockItem>, dimension: GroupDimension): List<StockGroup>`。
- Consumes: `StockItem`(需含 `aiProfile: AiProfile`)。Task 2/3 依赖这些类型。

- [ ] **Step 1: 在 `StockApi.kt` 给 `StockItem` 加字段并给 mock 填画像**

`StockApi.kt` 中 `data class StockItem` 追加字段(在 `aiBrief` 之后):
```kotlin
    val aiProfile: AiProfile,      // AI 四维画像(分组依据)
```
文件头追加 import(与 `StockItem` 同包,无需 import)。给 `SampleStockApi.list` 的 7 支各补 `aiProfile`(按 `derived` 值,保证每维 ≥2 组):
```kotlin
        // 1 贵州茅台
        aiProfile = AiProfile("重点关注", "MACD金叉", 95, "建议加自选"),
        // 2 腾讯控股
        aiProfile = AiProfile("低吸关注", "低位企稳", 78, "建议建仓"),
        // 3 宁德时代
        aiProfile = AiProfile("持股观望", "超跌反弹", 66, "继续持有"),
        // 4 比亚迪
        aiProfile = AiProfile("重点关注", "量能放大", 90, "建议建仓"),
        // 5 中国平安
        aiProfile = AiProfile("持股观望", "低位企稳", 64, "继续持有"),
        // 6 五粮液
        aiProfile = AiProfile("低吸关注", "低位企稳", 75, "建议加自选"),
        // 7 中芯国际
        aiProfile = AiProfile("重点关注", "量能放大", 88, "建议加自选"),
```

- [ ] **Step 2: 新建 `StockGrouping.kt`**

```kotlin
package com.example.task1.data

import com.example.task1.data.StockItem

data class AiProfile(
    val action: String,   // 操作建议: 重点关注/低吸关注/持股观望/建议回避
    val signal: String,   // 信号: MACD金叉/量能放大/低位企稳/超跌反弹
    val score: Int,       // 评分 0-100
    val scenario: String, // 场景: 建议加自选/建议建仓/建议减仓/继续持有
)

enum class GroupDimension(val label: String) {
    ACTION("操作建议"),
    SIGNAL("信号题材"),
    SCORE("评分分层"),
    SCENARIO("操作场景"),
}

data class StockGroup(val title: String, val stocks: List<StockItem>)

private fun scoreTier(score: Int): Pair<String, Int> = when {
    score >= 85 -> "高分推荐" to 0
    score in 70..84 -> "中分观察" to 1
    else -> "低分慎入" to 2
}

/** 按 [dimension] 分组:ACTION/SIGNAL/SCENARIO 按画像字段分桶,SCORE 按分数分档;组内按 score 降序(并列按 changePct 降序)。 */
fun groupStocks(stocks: List<StockItem>, dimension: GroupDimension): List<StockGroup> {
    // 每个分组的规范顺序(组间排序);未命中的组追加在后
    val order: Map<String, Int> = when (dimension) {
        GroupDimension.ACTION -> mapOf("重点关注" to 0, "低吸关注" to 1, "持股观望" to 2, "建议回避" to 3)
        GroupDimension.SIGNAL -> mapOf("量能放大" to 0, "MACD金叉" to 1, "低位企稳" to 2, "超跌反弹" to 3)
        GroupDimension.SCORE -> mapOf("高分推荐" to 0, "中分观察" to 1, "低分慎入" to 2)
        GroupDimension.SCENARIO -> mapOf("建议加自选" to 0, "建议建仓" to 1, "建议减仓" to 2, "继续持有" to 3)
    }
    fun categoryKey(item: StockItem): Pair<String, Int> = when (dimension) {
        GroupDimension.ACTION -> item.aiProfile.action to (order[item.aiProfile.action] ?: Int.MAX_VALUE)
        GroupDimension.SIGNAL -> item.aiProfile.signal to (order[item.aiProfile.signal] ?: Int.MAX_VALUE)
        GroupDimension.SCORE -> scoreTier(item.aiProfile.score)
        GroupDimension.SCENARIO -> item.aiProfile.scenario to (order[item.aiProfile.scenario] ?: Int.MAX_VALUE)
    }

    val buckets = LinkedHashMap<String, MutableList<StockItem>>()
    for (item in stocks) {
        val (key, _) = categoryKey(item)
        buckets.getOrPut(key) { mutableListOf() }.add(item)
    }
    return buckets.entries
        .sortedWith(compareBy { order[it.key] ?: Int.MAX_VALUE })
        .map { (title, list) ->
            StockGroup(
                title = "${title}股票建议",
                stocks = list.sortedWith(compareByDescending({ it.aiProfile.score }, { it.changePct })),
            )
        }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL(确认 `aiProfile` 字段、`AiProfile`/`groupStocks` 名称一致)。

---

## Task 2: AI 规则引擎

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/data/AiProfileDeriver.kt`

**Interfaces:**
- Consumes: `StockItem`、`AiProfile`(Task 1)。
- Produces: `fun deriveAiProfile(item: StockItem): AiProfile`。Task 3(TencentStockApi)依赖它。

- [ ] **Step 1: 新建 `AiProfileDeriver.kt`**

```kotlin
package com.example.task1.data

/** 由实时行情推导 AI 画像(纯函数,阈值可调)。腾讯接口不含 AI,故本地规则替代。 */
fun deriveAiProfile(item: StockItem): AiProfile {
    val changePct = item.changePct
    val pe = item.pe

    // 操作建议
    val action = when {
        changePct > 3.0 -> "重点关注"
        changePct in 1.0..3.0 -> "低吸关注"
        changePct > -1.0 -> "持股观望"
        else -> "建议回避"
    }
    // 信号(用涨幅 + 市盈率启发)
    val signal = when {
        changePct > 2.0 && pe <= 0 -> "量能放大"
        changePct > 2.0 -> "MACD金叉"
        pe in 1.0..20.0 -> "低位企稳"
        else -> "超跌反弹"
    }
    // 评分 0-100
    val momentumScore = (changePct.coerceIn(-5.0, 8.0) / 8.0 * 60.0).toInt()
    val valueScore = if (pe in 1.0..20.0) 25 else if (pe > 40.0) 10 else 15
    val riskScore = when {
        changePct < -2.0 -> 0
        changePct < 0.0 -> 5
        else -> 8
    }
    val score = (50 + momentumScore + valueScore + riskScore).coerceIn(0, 100)

    // 场景
    val scenario = when {
        action == "重点关注" && score >= 85 -> "建议加自选"
        action == "低吸关注" -> "建议建仓"
        action == "持股观望" -> "继续持有"
        else -> "建议减仓"
    }
    return AiProfile(action, signal, score, scenario)
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL。

---

## Task 3: 腾讯实时行情 `TencentStockApi`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/data/TencentStockApi.kt`

**Interfaces:**
- Consumes: `StockApi`(接口)、`StockItem`、`deriveAiProfile`(Task 2)、`NetworkModule`、`JSONObject`(`com.tencent.kuikly.core.nvi.serialization.json`)。
- Produces: `class TencentStockApi(private val network: () -> NetworkModule) : StockApi` 实现 `fetchWatchlist()`(拉取实时 → 解析 → 填画像)。Task 7 用它。

- [ ] **Step 1: 新建 `TencentStockApi.kt`**

```kotlin
package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 腾讯实时行情。用框架级 NetworkModule.requestGet 拉 qt.gtimg.cn。
 * 自选代码固定,本地维护 市场前缀 + code→name 映射(规避跨端 GBK 解码)。
 * 行情字段按腾讯 `~` 分隔协议映射;价位元→分、市值亿→元。AI 画像由 deriveAiProfile 推导。
 */
class TencentStockApi(private val network: () -> NetworkModule) : StockApi {

    // 自选代码表(本地固定):code -> (marketPrefix, name)
    private val codebook: List<Pair<String, Pair<String, String>>> = listOf(
        "600519" to ("sh" to "贵州茅台"),
        "00700" to ("hk" to "腾讯控股"),
        "300750" to ("sz" to "宁德时代"),
        "002594" to ("sz" to "比亚迪"),
        "601318" to ("sh" to "中国平安"),
        "000858" to ("sz" to "五粮液"),
        "688981" to ("sh" to "中芯国际"),
    )

    override suspend fun fetchWatchlist(): List<StockItem> {
        val query = codebook.joinToString(",") { (code, m) -> "${m.first}${code}" }
        val url = "http://qt.gtimg.cn/q=$query"
        val raw = requestRaw(url)
        val items = mutableListOf<StockItem>()
        for ((code, market) in codebook) {
            val name = market.second
            val body = extractBody(raw, market.first, code) ?: continue
            val f = body.split("~")
            if (f.size <= 24) continue
            val price = fen(f[3])
            val change = fen(f[21])
            val changePct = f.getOrElse(22) { "0" }.toDoubleOrNull() ?: 0.0
            val high = fen(f.getOrElse(23) { "0" })
            val low = fen(f.getOrElse(24) { "0" })
            val open = fen(f.getOrElse(5) { "0" })
            val marketCap = yi(f.getOrElse(35) { "0" })
            val floatCap = yi(f.getOrElse(34) { "0" })
            val pe = f.getOrElse(29) { "0" }.toDoubleOrNull() ?: 0.0
            val item = StockItem(
                id = code, name = name, code = code,
                price = price, change = change, changePct = changePct,
                aiEnabled = false, aiBrief = "",
                high = high, low = low, open = open,
                marketCap = marketCap, floatCap = floatCap,
                pe = pe, etfRatio = 0.0,
                aiProfile = AiProfile("持股观望", "低位企稳", 60, "继续持有"), // 占位,下面重填
            )
            val profile = deriveAiProfile(item.copy(price = price, change = change, changePct = changePct))
            val enabled = profile.score >= 80
            items.add(item.copy(aiEnabled = enabled, aiBrief = briefText(profile), aiProfile = profile))
        }
        return items
    }

    // 非 JSON 回包被包为 {"data":"原始内容"},取 optString("data") 得原始文本
    private suspend fun requestRaw(url: String): String? {
        return suspendCoroutine { cont ->
            val nm = network()
            nm.requestGet(url, JSONObject()) { data, success, errorMsg, _ ->
                if (success) cont.resume(data.optString("data")) else cont.resume(null)
            }
        }
    }

    /** 从原始文本中取 `v_sh600519="..."` 的引号内内容。 */
    private fun extractBody(raw: String?, market: String, code: String): String? {
        if (raw == null) return null
        val key = "v_${market}${code}="
        val i = raw.indexOf(key)
        if (i < 0) return null
        val start = raw.indexOf('"', i)
        if (start < 0) return null
        val end = raw.indexOf('"', start + 1)
        if (end < 0) return null
        return raw.substring(start + 1, end)
    }

    private fun fen(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L)
    private fun yi(s: String): Long = (s.toDoubleOrNull()?.let { Math.round(it * 1_0000_0000L) } ?: 0L)

    private fun briefText(p: AiProfile): String = "${p.signal},${p.action}(${p.score}分)"

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis = SampleStockApi.fetchAiAnalysis(code)
    override suspend fun fetchStock(code: String): StockItem? = fetchWatchlist().find { it.code == code }
    override suspend fun fetchGlobalAdvice(): String = ""
}
```

> ⚠️ 实机验证项:`extractBody` 的 `v_` 前缀对 HK(`00700`)是否为 `v_hk00700` 或 `r_hk00700`;AH 字段索引(尤其 `pe`/市值)须对照一次真实响应。若 HK 命中失败,则 `00700` 会因 `extractBody==null` 被 `continue` 跳过,列表少一只——可接受,后续再补。

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL(TencentStockApi 类型/import 正确)。

---

## Task 4: 智窗长按 + 维度标签

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/components/AiBottomBar.kt`

**Interfaces:**
- Consumes: `find` 现有 `AiBottomBar(advice, thinking, modifier)`。
- Produces: `AiBottomBar(advice, thinking, dimensionLabel: String, onLongPress: () -> Unit, modifier: Modifier = Modifier)`。Task 7 传入。

- [ ] **Step 1: 改签名 + 文案 + 长按**

把 `fun AiBottomBar(advice: String, thinking: Boolean, modifier: Modifier = Modifier)` 改为:
```kotlin
@Composable
fun AiBottomBar(
    advice: String,
    thinking: Boolean,
    dimensionLabel: String,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
```
在建议态分支(else)文案行改为带维度前缀:
```kotlin
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "全盘AI建议·$dimensionLabel", color = AppColors.MainText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = advice, color = AppColors.SubGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
```
给外层 `Column` 的 `modifier` 链上加 `pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }`,并补 import:
```kotlin
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
```
> 若 `detectTapGestures(onLongPress = …)` 编译失败(不识别该重载),改为在 `foundation.gestures` 包下手动实现长按:
> ```kotlin
> import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
> import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
> import com.tencent.kuikly.compose.ui.input.pointer.util.awaitLongPressOrCancellation  // 若此包缺失,用 androidx 等价 API
> .pointerInput(Unit) {
>     awaitEachGesture {
>         val down = awaitFirstDown(requireUnconsumed = false)
>         awaitLongPressOrCancellation(down.id)?.let { onLongPress.invoke() }
>     }
> }
> ```
> 优先用 `detectTapGestures(onLongPress = …)`,编译不过再退回指针方案。

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL。

---

## Task 5: 分组标题组件

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/GroupHeader.kt`

**Interfaces:**
- Consumes: `StockGroup`(Task 1)、`AppColors`、`AppIcon`。
- Produces: `@Composable fun GroupHeader(group: StockGroup)`,组标题 `stock 建议` 加数量角标。Task 7 使用。

- [ ] **Step 1: 新建 `GroupHeader.kt`**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.data.StockGroup
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun GroupHeader(group: StockGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).background(AppColors.AiLight, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                AppIcon("sparkles", modifier = Modifier.size(12.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = group.title, color = AppColors.MainText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.background(AppColors.AiBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text = "${group.stocks.size}", color = AppColors.RiskText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL。

---

## Task 6: 维度选择弹层内容

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/DimensionPickerSheet.kt`

**Interfaces:**
- Consumes: `GroupDimension`(Task 1)、`AppColors`、`AppIcon`、`ModalBottomSheet`(在页面层)。
- Produces: `@Composable fun DimensionPickerSheet(current: GroupDimension, onSelect: (GroupDimension) -> Unit)`。Task 7 包在 `ModalBottomSheet` 里。

- [ ] **Step 1: 新建 `DimensionPickerSheet.kt`**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.data.GroupDimension
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun DimensionPickerSheet(current: GroupDimension, onSelect: (GroupDimension) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp).background(androidx.compose.ui.graphics.Color(0xFFCBCBCB), RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "选择分组维度", color = AppColors.MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        GroupDimension.values().forEach { dim ->
            val selected = dim == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(if (selected) AppColors.HeaderBg else AppColors.PageBg, RoundedCornerShape(12.dp))
                    .clickable { onSelect(dim) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = dim.label, color = AppColors.MainText, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (selected) {
                    Text(text = "✓", color = AppColors.RiseRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL。

---

## Task 7: WatchlistPage 分组渲染 + 维度切换 + 网络加载

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/pages/WatchlistPage.kt`

**Interfaces:**
- Consumes: `groupStocks`/`GroupDimension`(Task1)、`TencentStockApi`(Task3)、`AiBottomBar(dimensionLabel,onLongPress)`(Task4)、`GroupHeader`(Task5)、`DimensionPickerSheet`(Task6)、`NetworkModule`。
- Produces: 分组 LazyColumn + 长按切维度 + 实时/兜底加载。

- [ ] **Step 1: 改状态与数据源**

在 `WatchlistScreen()` 顶部,现有 `stocks` 后追加:
```kotlin
    var dimension by remember { mutableStateOf(GroupDimension.ACTION) }
    var showDimPicker by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
```
把现有两个 `LaunchedEffect(Unit)` 换成数据加载 + 智窗:
```kotlin
    val activity = LocalActivity.current
    fun network(): NetworkModule = activity.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)

    val fetch: suspend () -> List<StockItem> = {
        try {
            val live = TencentStockApi { network() }.fetchWatchlist()
            offline = false
            live
        } catch (e: Throwable) {
            offline = true
            SampleStockApi.fetchWatchlist()
        }
    }

    LaunchedEffect(Unit) {
        stocks = fetch()
        thinking = true
        delay(800)
        // 智窗建议文案按分组生成摘要
        advice = buildAdvice(groupStocks(stocks, dimension))
        thinking = false
    }
```
新增本地函数(放在 `aifa` 相关函数附近):
```kotlin
    fun buildAdvice(groups: List<StockGroup>): String =
        if (groups.isEmpty()) "暂无自选股"
        else groups.joinToString("、") { g -> "${g.title.replace("股票建议", "")}${g.stocks.size}只" }
```
并把 `openDetail` 里 `activity.acquireModule<RouterModule>(…)` 保留(activity 变量已有)。

> 注意原代码已有 `val activity = LocalActivity.current`(第 ~100 行),复用即可,别重复声明。原 `SampleStockApi.fetchGlobalAdvice()` 引用删除。

- [ ] **Step 2: 改 LazyColumn 为分组渲染**

把现有 `items(stocks, key = { it.id }) { … }`(整块)替换为:
```kotlin
            val groups = groupStocks(stocks, dimension)
            groups.forEach { group ->
                item(key = "h-${dimension.name}-${group.title}") { GroupHeader(group) }
                items(group.stocks, key = { it.id }) { item ->
                    // 原卡片逻辑原样保留(scale/pointerInput/StockCard)
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
                                        haptic("light")
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
```
> `groups` 用 `remember(stocks, dimension) { groupStocks(stocks, dimension) }` 包住;上面的循环内 `groupStocks(stocks, dimension)` 也可改为引用该 remember 值。确认 `GroupHeader` 已在 import。

- [ ] **Step 3: 智窗传参 + 维度弹层**

`AiBottomBar` 调用处改为:
```kotlin
            AiBottomBar(
                advice = advice,
                thinking = thinking,
                dimensionLabel = dimension.label,
                onLongPress = { showDimPicker = true },
            )
```
在文件底部(现有 AI 弹层之后)追加维度选择弹层:
```kotlin
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
```
补 import:`com.example.task1.data.GroupDimension`、`com.example.task1.data.StockGroup`、`com.example.task1.data.TencentStockApi`、`com.tencent.kuikly.core.module.NetworkModule`。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL。

---

## Task 8: 编译与手测清单

**Files:** 无(验证任务)。

- [ ] **Step 1: 全量编译**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL(所有改动类型闭合)。

- [ ] **Step 2: 手测(Android,需联网)**

1. 启动自选页 → 默认「操作建议」3 组(重点关注3/低吸2/观望2)。
2. **长按底部「分析智窗」** → 弹出 4 维度 → 切「信号题材」4 组、「评分分层」3 组、「操作场景」3 组;组内按评分降序。
3. 智窗文案「全盘AI建议·<维度>」+ 摘要,随维度变化。
4. 卡片价格/涨跌为腾讯实时值;展开/建议行弹 AI 面板正常。
5. 断网进页 → 回退 mock + 弱提示,列表仍分组显示。

---

## 依赖顺序与并行

Tasks 1 → 2 → 3 有类型依赖(串行);Task 4/5/6 相对独立(可与 2/3 并行);Task 7 依赖 1/3/4/5/6;Task 8 收尾。**subagent-driven 时按 依赖图 分组派发**,每任务结束先各自编译,再合并。
