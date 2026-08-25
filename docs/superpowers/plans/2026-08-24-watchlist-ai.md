# 行情自选页 + AI 分析面板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use Kuikly Compose UI to build two Figma screens — a watchlist/行情 page and an AI-analysis bottom sheet — per `docs/superpowers/specs/2026-08-24-watchlist-ai-design.md`.

**Architecture:** One `@Page("watchlist")` list page that renders a header (search + LazyRow tabs), a market-overview bar, a `LazyColumn` of stock cards, and a fixed bottom area (「分析智窗」bar + bottom nav). Tapping a card opens a placeholder `@Page("stockDetail")`; tapping the card's AI row, dragging a card onto the 智窗 bar, or tapping the 智窗 bar opens an in-page `ModalBottomSheet` AI panel; the panel's「查看完整报告」opens `@Page("aiReport")`. Data comes from a reserved `StockApi` interface backed by `SampleStockApi`.

**Tech Stack:** Kotlin Multiplatform + Kuikly Compose DSL (single codebase → Android/iOS/鸿蒙/H5/小程序). No new dependencies.

---

## Global Constraints

Verbatim from `CLAUDE.md` + `rules/kuiklyComposeDSL.mdc` — every task implicitly includes these:

- **Package rule:** only `androidx.compose.runtime.*` uses the official package; **everything else** uses `com.tencent.kuikly.compose.*`.
- **Pages** extend `com.tencent.kuikly.compose.ComposeContainer`, annotated `@Page("name")`, override `willInit() { super.willInit(); setContent { ... } }`. `setContent` is imported separately as `com.tencent.kuikly.compose.setContent`.
- **LazyColumn/LazyRow:** delete `reverseLayout` and `flingBehavior`; add `beyondBoundsItemCount = 3`.
- **No `Icon`/`IconImage`** → use `Image`. **`TextField` must pass an explicit `onValueChange`** (default is empty → input won't update).
- **`pointerInput` must pass a key**, and every mutable state read inside the closure must be readable live (use `remember { mutableStateOf }` + `.value`); LazyList item closure key = `item`.
- **ModalBottomSheet** signature: `ModalBottomSheet(visible, onDismissRequest, modifier, containerColor, contentColor, tonalElevation, scrimColor, dismissOnDrag, dismissThreshold = 0.25f, animationDurationMillis = 250, content: @Composable ColumnScope.() -> Unit)`. No `sheetState`/`shape`/`dragHandle`.
- **Do NOT use** `fadeIn`/`fadeOut`/`scaleIn`/`scaleOut` (buggy — `layerBlock` missing). Use `ModalBottomSheet.animationDurationMillis` for the panel, and plain linear rendering for the list.
- **Assets:** business-module assets live at `shared/src/commonMain/assets/common` (common) and `shared/src/commonMain/assets/$pageName` (page). Reference via `DrawableResource(ImageUri.commonAssets("name").toUrl(""))` + `Image(painter = painterResource(drawable), ...)`; needs `@OptIn(InternalResourceApi::class)` (import `com.tencent.kuikly.compose.resources.InternalResourceApi`).
- **Build gate:** `./gradlew :shared:compileDebugKotlinAndroid` (JDK 17 via `org.gradle.java.home` in `gradle.properties`).
- **No git repo** in `D:\Codes\Task1` → skip `git commit` steps; verify by compile + manual render. There is **no test source set** (only `commonMain`), so the verification gate is compilation.

### Key packages (already verified against `D:/Codes/KuiklyUI`)
- `Color`, `Brush` → `com.tencent.kuikly.compose.ui.graphics`
- `RoundedCornerShape` → `com.tencent.kuikly.compose.foundation.shape`
- `Modifier`, `graphicsLayer` → `com.tencent.kuikly.compose.ui` (Modifier) / `com.tencent.kuikly.compose.ui.graphics`
- `Offset`, `Rect` → `com.tencent.kuikly.compose.ui.geometry`; `IntOffset` → `com.tencent.kuikly.compose.ui.unit`; `dp`, `sp` → `com.tencent.kuikly.compose.ui.unit`
- `Row`, `Column`, `Box`, `Spacer`, `fillMaxWidth`, `fillMaxSize`, `size`, `width`, `height`, `padding`, `background`, `border`, `clickable`, `offset(Density.() -> IntOffset)`, `weight` (ColumnScope), `Arrangement` → `com.tencent.kuikly.compose.foundation.layout`
- `onGloballyPositioned`, `LayoutCoordinates` (+ extension `boundsInWindow(): Rect`) → `com.tencent.kuikly.compose.ui.layout`
- `pointerInput` → `com.tencent.kuikly.compose.ui.input.pointer`
- `detectDragGesturesAfterLongPress`, `detectTapGestures` → `com.tencent.kuikly.compose.foundation.gestures`
- `FontWeight` → `com.tencent.kuikly.compose.ui.text.font`; `TextAlign` → `com.tencent.kuikly.compose.ui.text`; `TextOverflow` → `com.tencent.kuikly.compose.ui.text.style`
- `painterResource`, `DrawableResource`, `InternalResourceApi` → `com.tencent.kuikly.compose.resources`
- `ImageUri` → `com.tencent.kuikly.core.base.attr` (`commonAssets(path)`, `pageAssets(path)`, `toUrl(pageName)`)
- `RouterModule` → `com.tencent.kuikly.core.module` (`openPage(pageName, JSONObject?)`); `JSONObject` → `com.tencent.kuikly.core.nvi.serialization.json`
- `LocalActivity.current` (a `ComposeContainer`/`Pager`) → `com.tencent.kuikly.compose.ui.platform` — it exposes `acquireModule<RouterModule>(RouterModule.MODULE_NAME)`.

### Icons to export from Figma (fileKey `AOGH9OMqmNFPTeoiuMJzpE`)
| asset name (saved as) | nodeId | size |
|---|---|---|
| `sparkles` | `18:143` | 16×16 |
| `x-circle` | `1:189` | 14×14 |
| `trending-up` | `1:202` | 16×16 |
| `shield-alert` | `1:221` | 16×16 |
| `award` | `1:240` | 16×16 |
| `arrow-right` | `1:250` | 16×16 |

Export each as **SVG** to `shared/src/commonMain/assets/common/<name>.svg`; ALSO export a **PNG@2x** fallback at `shared/src/commonMain/assets/common/<name>@2x.png` in case Kuikly `Image` fails to render SVG (runtime fallback uses `<name>@2x` via the code in Task 4). The sparkline, gauge bar, and progress bars are drawn with Compose primitives (Box + background), NOT assets.

---

## Task 1: Extended price formatters in `base/Utils.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/task1/base/Utils.kt`

**Interfaces:**
- Produces: `Utils.formatPrice2(price: Long): String` → `"1856.00"`; `Utils.formatPriceWhole(price: Long): String` → `"1920"`. Consumed by StockCard, AiBottomSheet, MarketOverviewBar.

- [ ] **Step 1: Add the two formatter functions** (inside the existing `internal object Utils`, after `convertToPriceStr`):

```kotlin
    /** cents -> "1856.00" (always 2 decimals). */
    fun formatPrice2(price: Long): String {
        val yuan = price / 100
        val fen = (price % 100).toInt()
        val fenStr = if (fen < 10) "0$fen" else "$fen"
        return "$yuan.$fenStr"
    }

    /** cents -> "1920" (whole yuan, no decimals). */
    fun formatPriceWhole(price: Long): String = (price / 100).toString()
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL` (no new references yet, so this just confirms the file still compiles).

---

## Task 2: Design tokens `theme/AppColors.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/theme/AppColors.kt`

**Interfaces:**
- Produces: `object AppColors` with `RiseRed`, `Green`, `SubGray`, `MainText`, `Border`, `PageBg`, `HeaderBg`, `AiLight`, `AiBg`, `AiBadgeBg`, `RiseBadgeBg`, `RiskOrange`, `RecommendPurple`, `RiskText`, and `val AiGradient: List<Color>`. Consumed by every component.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.theme

import com.tencent.kuikly.compose.ui.graphics.Color

object AppColors {
    val RiseRed = Color(0xFFDF0004)        // 涨/价格红
    val Green = Color(0xFF4A6B5A)          // 强调绿
    val SubGray = Color(0xFF636363)        // 次级灰 (代码/说明)
    val MainText = Color(0xFF2D2D2D)       // 主文字
    val Border = Color(0xFFB5C5C5)         // 边框
    val PageBg = Color(0xFFF7F7F7)         // 页面/弹层背景
    val HeaderBg = Color(0xFFF6DCD7)       // 顶部区背景
    val AiLight = Color(0xFFE9B8AC)        // AI 渐变起
    val AiBg = Color(0xFFF6DCD7)           // AI 渐变终
    val AiBadgeBg = Color(red = 233f / 255f, green = 184f / 255f, blue = 172f / 255f, alpha = 0.15f)
    val RiseBadgeBg = Color(red = 74f / 255f, green = 107f / 255f, blue = 90f / 255f, alpha = 0.10f)
    val RiskOrange = Color(0xFFF59E0B)     // 中低风险
    val RecommendPurple = Color(0xFFA78BFA) // 推荐指数
    val RiskText = Color(0xFF884D3A)       // 当前评级/高风险

    val AiGradient = listOf(AiLight, AiBg)
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL` (unused object is fine; no compile error).

---

## Task 3: Reserved data API + sample `data/StockApi.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/data/StockApi.kt`

**Interfaces:**
- Produces: `data class StockItem(id, name, code, price: Long, changePct: Double, aiEnabled: Boolean, aiBrief: String)`; `data class AiAnalysis(trendLabel, trendText, riskLevel, riskText, score: Int, targetPrice: Long, stopLossPrice: Long)`; `interface StockApi { suspend fun fetchWatchlist(): List<StockItem>; suspend fun fetchAiAnalysis(code: String): AiAnalysis }`; `object SampleStockApi : StockApi`.
- Consumed by: WatchlistPage, AiBottomSheet, AiReportPage, StockCard.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.data

data class StockItem(
    val id: String,
    val name: String,
    val code: String,
    val price: Long,          // 分
    val changePct: Double,    // 涨跌幅 %
    val aiEnabled: Boolean,
    val aiBrief: String,      // AI 卡副标题，如 "近5日连续上涨，MACD金叉形成，建议查看详情"
)

data class AiAnalysis(
    val trendLabel: String,       // "短期看涨信号明显"
    val trendText: String,        // 正文
    val riskLevel: String,        // "中低风险"
    val riskText: String,         // "当前评级：中低风险"
    val score: Int,               // 推荐指数 85/100
    val targetPrice: Long,        // 分
    val stopLossPrice: Long,      // 分
)

/** 预留：接真实行情/分析接口时只替换实现。 */
interface StockApi {
    suspend fun fetchWatchlist(): List<StockItem>
    suspend fun fetchAiAnalysis(code: String): AiAnalysis
}

object SampleStockApi : StockApi {
    private val list = listOf(
        StockItem("1", "贵州茅台", "600519", 185600, 2.35, true, "近5日连续上涨，MACD金叉形成，建议查看详情"),
        StockItem("2", "腾讯控股", "00700", 39640, 1.20, false, ""),
        StockItem("3", "宁德时代", "300750", 23520, -0.80, false, ""),
        StockItem("4", "比亚迪", "002594", 28600, 3.10, true, "量能齐升，短线动能增强，建议观察"),
        StockItem("5", "中国平安", "601318", 4820, -0.50, false, ""),
        StockItem("6", "五粮液", "000858", 13860, 0.65, false, ""),
        StockItem("7", "中芯国际", "688981", 9870, 4.20, true, "放量上攻，MACD翻红，注意回踩"),
    )

    override suspend fun fetchWatchlist(): List<StockItem> = list

    override suspend fun fetchAiAnalysis(code: String): AiAnalysis = AiAnalysis(
        trendLabel = "短期看涨信号明显",
        trendText = "近5日连续上涨，成交量放大，MACD金叉形成，短期看涨信号明显。",
        riskLevel = "中低风险",
        riskText = "当前评级：中低风险",
        score = 85,
        targetPrice = 192000,
        stopLossPrice = 180000,
    )
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Download icon assets (`sparkles`, `x-circle`, `trending-up`, `shield-alert`, `award`, `arrow-right`)

**Files:**
- Create: `shared/src/commonMain/assets/common/sparkles.svg`, `.../x-circle.svg`, `.../trending-up.svg`, `.../shield-alert.svg`, `.../award.svg`, `.../arrow-right.svg`
- Create (fallback): `.../sparkles@2x.png`, `.../x-circle@2x.png`, `.../trending-up@2x.png`, `.../shield-alert@2x.png`, `.../award@2x.png`, `.../arrow-right@2x.png`

**Interfaces:**
- Produces: files consumed by `Icons.kt` (Task 5). No Kotlin interface.

- [ ] **Step 1: Create the assets directory** (confirm parent exists first)

Run: `ls shared/src/commonMain/assets` — note this is the `shared` module root `commonMain/assets` (already exists). Create the `common` subfolder via the tooling below, or:
`mkdir -p "shared/src/commonMain/assets/common"`

- [ ] **Step 2: Export each node as SVG + PNG@2x** (one call per node, `fileKey = "AOGH9OMqmNFPTeoiuMJzpE"`)

For each row use `download_assets` with `defaultFormat = "svg"`, and again with `defaultFormat = "png", defaultScale = 2`:

| asset `<name>` | nodeId |
|---|---|
| sparkles | `18:143` |
| x-circle | `1:189` |
| trending-up | `1:202` |
| shield-alert | `1:221` |
| award | `1:240` |
| arrow-right | `1:250` |

Save the returned SVG to `shared/src/commonMain/assets/common/<name>.svg` and the PNG@2x to `shared/src/commonMain/assets/common/<name>@2x.png` (use the `format` field's extension; write the bytes exactly as returned).

- [ ] **Step 3: Verify files exist**

Run: `ls -R shared/src/commonMain/assets/common`
Expected: 6 `.svg` + 6 `.png` files present. (Compile does not depend on these files — they load at runtime.)

---

## Task 5: Icon lookup helper `components/Icons.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/Icons.kt`

**Interfaces:**
- Produces: `@Composable fun AppIcon(name: String, modifier: Modifier = Modifier)` that renders `assets/common/<name>.svg`. Consumed by StockCard, MarketOverviewBar (optional), AiBottomSheet, AiBottomBar. If SVG render fails, `AppIcon` falls back to `<name>@2x`.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.resources.DrawableResource
import com.tencent.kuikly.compose.resources.InternalResourceApi
import com.tencent.kuikly.compose.resources.painterResource
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.core.base.attr.ImageUri

@OptIn(InternalResourceApi::class)
fun commonIcon(name: String): DrawableResource =
    DrawableResource(ImageUri.commonAssets(name).toUrl(""))

/**
 * Renders an SVG asset from assets/common/<name>.svg.
 * If SVG rendering is unreliable at runtime, call this with "${name}@2x" instead.
 */
@Composable
fun AppIcon(name: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(commonIcon(name)),
        contentDescription = null,
        modifier = modifier,
    )
}
```

Note: `Modifier.size` lives in `com.tencent.kuikly.compose.foundation.layout.size` — callers import it themselves; `AppIcon` does not need it.

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. `Image` is `com.tencent.kuikly.compose.foundation.Image`. If SVG rendering is unreliable at runtime, switch `AppIcon` callers to `AppIcon("${name}@2x", ...)`.

---

## Task 6: `components/StockCard.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/StockCard.kt`

**Interfaces:**
- Consumes: `AppIcon`, `StockItem`, `Utils.formatPrice2/formatPriceWhole`, `AppColors`.
- Produces: `@Composable fun StockCard(item: StockItem, onClick: () -> Unit, onOpenAi: () -> Unit)`. Auto-selects 80dp AI card vs 46dp normal card by `item.aiEnabled`.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.base.Utils
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun StockCard(item: StockItem, onClick: () -> Unit, onOpenAi: () -> Unit) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(Color.White, cardShape)
            .border(1.dp, AppColors.Border, cardShape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        TopRow(item)
        if (item.aiEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            AiRow(item, onOpenAi)
        }
    }
}

@Composable
private fun TopRow(item: StockItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.name, color = AppColors.MainText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = item.code, color = AppColors.SubGray, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = Utils.formatPrice2(item.price), color = AppColors.RiseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            ChangeBadge(item.changePct)
        }
    }
}

@Composable
private fun ChangeBadge(changePct: Double) {
    Box(
        modifier = Modifier
            .background(AppColors.RiseBadgeBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = Utils.formatPercent(changePct),
            color = AppColors.RiseRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AiRow(item: StockItem, onOpenAi: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAi),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(AppColors.AiLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon("sparkles", modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.aiBrief, color = AppColors.MainText, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
```

> Note: `changePct` values in `SampleStockApi` are already in percent (e.g. `2.35` = +2.35%), so `formatPercent` multiplies by 100 to get hundredths for formatting. This avoids `String.format` (not available in common Kotlin).

- [ ] **Step 2: Add `formatPercent` to `base/Utils.kt`** (common-safe)

Add `import kotlin.math.roundToLong` to `base/Utils.kt`, then add inside `internal object Utils`:

```kotlin
    /** 2.35 -> "+2.35%", -0.80 -> "-0.80%". */
    fun formatPercent(changePct: Double): String {
        val sign = if (changePct >= 0) "+" else ""
        val abs = (kotlin.math.abs(changePct) * 100.0).roundToLong()
        return sign + formatChange(abs)
    }

    private fun formatChange(absHundredths: Long): String {
        val whole = absHundredths / 100
        val frac = (absHundredths % 100).toInt()
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        return "$whole.$fracStr%"
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. `Alignment` comes from `com.tencent.kuikly.compose.ui.Alignment` (already imported); check `TextOverflow` import (`com.tencent.kuikly.compose.ui.text.style.TextOverflow`) if the fully-qualified reference complains — add it and use `TextOverflow.Ellipsis`.

---

## Task 7: `components/TabBar.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/TabBar.kt`

**Interfaces:**
- Consumes: `AppColors`.
- Produces: `@Composable fun TabBar(selected: String, onSelect: (String) -> Unit)`. LazyRow of tabs (自选/全球/港股/期贷/A股/美股/黄金); selected is `sp+1` bigger.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private val Tabs = listOf("自选", "全球", "港股", "期贷", "A股", "美股", "黄金")

@Composable
fun TabBar(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(Tabs, key = { it }) { tab ->
            val sel = tab == selected
            Text(
                text = tab,
                color = if (sel) AppColors.MainText else AppColors.SubGray,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (sel) 21.sp else 20.sp,   // 选中 sp+1
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. If `LazyRow`'s `horizontalArrangement` param name differs, drop it and add `Arrangement` elsewhere; the core `items`+`clickable` is correct.

---

## Task 8: `components/MarketOverviewBar.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/MarketOverviewBar.kt`

**Interfaces:**
- Consumes: `AppColors`.
- Produces: `@Composable fun MarketOverviewBar()`. `+23.45亿` (red 24sp) + `已收盘` + `2026-12-12 星期二` + `涨23`/`跌12` + progress bar (red gradient 267/392, remainder green).

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun MarketOverviewBar() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "+23.45亿", color = AppColors.RiseRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "已收盘", color = AppColors.MainText, fontSize = 13.sp)
        }
        Text(text = "2026-12-12 星期二", color = AppColors.SubGray, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "涨23", color = AppColors.RiseRed, fontSize = 13.sp)
            Text(text = "跌12", color = AppColors.Green, fontSize = 13.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp).background(AppColors.Green, RoundedCornerShape(3.dp))) {
            Box(modifier = Modifier.fillMaxWidth(0.68f).height(6.dp)
                .background(Brush.linearGradient(AppColors.AiGradient), RoundedCornerShape(3.dp)))
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. `Alignment` is imported from `com.tencent.kuikly.compose.ui.Alignment`.

---

## Task 9: `components/BottomNav.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/BottomNav.kt`

**Interfaces:**
- Consumes: `AppColors`.
- Produces: `@Composable fun BottomNav(selected: String = "行情")`. Three text labels 行情/分析/我的; selected bold + red.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

@Composable
fun BottomNav(selected: String = "行情") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("行情", "分析", "我的").forEach { label ->
            val sel = label == selected
            Text(
                text = label,
                color = if (sel) AppColors.RiseRed else AppColors.SubGray,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier.clickable { },
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

---

## Task 10: `components/AiBottomBar.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/AiBottomBar.kt`

**Interfaces:**
- Consumes: `AppIcon`, `AppColors`.
- Produces: `@Composable fun AiBottomBar(onClick: () -> Unit, modifier: Modifier = Modifier)`. sparkles + `分析智窗` (18sp bold) + `拖入股票进入ai分析` (13sp). Clicking anywhere calls `onClick` (the guaranteed-fallback path). The `modifier` is used by WatchlistPage to attach `onGloballyPositioned` for drag bounds.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
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
fun AiBottomBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(AppColors.HeaderBg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp).background(AppColors.AiLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                AppIcon("sparkles", modifier = Modifier.size(16.dp))
            }
            Text(text = "分析智窗", color = AppColors.MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        Text(text = "拖入股票进入ai分析", color = AppColors.SubGray, fontSize = 13.sp)
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. `Alignment` is Kuikly `com.tencent.kuikly.compose.ui.Alignment` (already imported).

---

## Task 11: `components/AiBottomSheet.kt` (shared section composables + sheet)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/components/AiBottomSheet.kt`

**Interfaces:**
- Consumes: `AppIcon`, `StockItem`, `AiAnalysis`, `Utils.formatPrice2/formatPriceWhole`, `AppColors`.
- Produces:
  - `@Composable fun AiBottomSheet(analysis: AiAnalysis, stock: StockItem?, onDismiss: () -> Unit, onViewReport: () -> Unit)` — the ModalBottomSheet body (handle + header + mini card + sections + CTA).
  - `@Composable fun MiniStockCard(stock: StockItem?)` — reused by report page.
  - `@Composable fun TrendSection(analysis: AiAnalysis)`, `@Composable fun RiskSection(analysis: AiAnalysis)`, `@Composable fun BuySection(analysis: AiAnalysis)` — reused by `@Page("aiReport")`.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.base.Utils
import com.example.task1.data.AiAnalysis
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

@Composable
fun AiBottomSheet(analysis: AiAnalysis, stock: StockItem?, onDismiss: () -> Unit, onViewReport: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        // handle
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp).background(Color(0xFFCBCBCB), RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
        // header
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(AppColors.AiLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    AppIcon("sparkles", modifier = Modifier.size(16.dp))
                }
                Text(text = "智能分析", color = AppColors.MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.background(AppColors.AiBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(text = "Power by Ai模型", color = AppColors.RiskText, fontSize = 10.sp)
                }
            }
            Box(
                modifier = Modifier.size(28.dp).background(AppColors.PageBg, RoundedCornerShape(14.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon("x-circle", modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        MiniStockCard(stock)
        Spacer(modifier = Modifier.height(16.dp))
        TrendSection(analysis)
        Spacer(modifier = Modifier.height(16.dp))
        RiskSection(analysis)
        Spacer(modifier = Modifier.height(16.dp))
        BuySection(analysis)
        Spacer(modifier = Modifier.height(20.dp))
        // CTA
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(AppColors.RiseRed, RoundedCornerShape(24.dp)).clickable(onClick = onViewReport).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "查看完整报告", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            AppIcon("arrow-right", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun MiniStockCard(stock: StockItem?) {
    if (stock == null) return
    Row(modifier = Modifier.fillMaxWidth().border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = stock.name, color = AppColors.MainText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = stock.code, color = AppColors.SubGray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = Utils.formatPrice2(stock.price), color = AppColors.RiseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.background(AppColors.RiseBadgeBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text = Utils.formatPercent(stock.changePct), color = AppColors.RiseRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TrendSection(analysis: AiAnalysis) {
    SectionCard {
        SectionHeader(icon = "trending-up", title = "涨势分析", rightText = analysis.trendLabel)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "近5日走势特征", color = AppColors.MainText, fontSize = 13.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "MACD金叉形成", color = AppColors.Green, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Sparkline()
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = analysis.trendText, color = AppColors.MainText, fontSize = 13.sp)
    }
}

@Composable
fun RiskSection(analysis: AiAnalysis) {
    SectionCard {
        SectionHeader(icon = "shield-alert", title = "风险评估", rightText = analysis.riskLevel)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GaugeSegment(width = 108.dp, color = AppColors.RiskText)
            GaugeSegment(width = 108.dp, color = AppColors.RiskOrange)
            GaugeSegment(width = 108.dp, color = AppColors.RiseRed)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "低风险", color = AppColors.SubGray, fontSize = 12.sp)
            Text(text = analysis.riskText, color = AppColors.RiskText, fontSize = 12.sp)
            Text(text = "高风险", color = AppColors.SubGray, fontSize = 12.sp)
        }
    }
}

@Composable
fun BuySection(analysis: AiAnalysis) {
    SectionCard {
        SectionHeader(icon = "award", title = "买入建议", rightText = "推荐指数 ${analysis.score}/100")
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(AppColors.Border, RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(0.68f).height(8.dp).background(AppColors.RecommendPurple, RoundedCornerShape(4.dp)))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "建议分批建仓，目标价位 ${Utils.formatPriceWhole(analysis.targetPrice)}，止损位 ${Utils.formatPriceWhole(analysis.stopLossPrice)}。",
            color = AppColors.MainText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) { content() }
}

@Composable
private fun SectionHeader(icon: String, title: String, rightText: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, color = AppColors.MainText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = rightText, color = AppColors.MainText, fontSize = 13.sp)
    }
}

@Composable
private fun GaugeSegment(width: com.tencent.kuikly.compose.ui.unit.Dp, color: Color) {
    Box(modifier = Modifier.width(width).height(6.dp).background(color, RoundedCornerShape(3.dp)))
}

@Composable
private fun Sparkline() {
    // Rising mini area-chart: 5 bars of increasing height (design reads as a rising signal).
    val heights = listOf(4f, 9f, 12f, 16f, 21f)
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        heights.forEach { h ->
            Box(modifier = Modifier.weight(1f).height(h.dp).background(AppColors.AiLight, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border))
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. If `Color.Transparent` is unknown, replace with `Color(0x00000000)`. If `Text color = Color.White` errors, use `Color(0xFFFFFFFF)`.

---

## Task 12: `pages/WatchlistPage.kt`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/pages/WatchlistPage.kt`

**Interfaces:**
- Consumes: all components, `SampleStockApi`, `AppColors`, `LocalActivity`/`RouterModule` for navigation.
- Produces: `@Page("watchlist") class WatchlistPage : ComposeContainer()`. Exported for Android launch via `pageName=watchlist`.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.task1.components.AiBottomBar
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
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectDragGesturesAfterLongPress
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
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
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.LayoutCoordinates
import com.tencent.kuikly.compose.ui.layout.boundsInWindow
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.platform.LocalActivity
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Page("watchlist")
class WatchlistPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent { WatchlistScreen() }
    }
}

@Composable
fun WatchlistScreen() {
    var stocks by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("自选") }
    var showSheet by remember { mutableStateOf(false) }
    var activeStock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    var barBounds by remember { mutableStateOf<Rect?>(null) }
    var draggingStock by remember { mutableStateOf<StockItem?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var draggingBaseBounds by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    LaunchedEffect(Unit) { stocks = SampleStockApi.fetchWatchlist() }

    fun openPanel(stock: StockItem) {
        activeStock = stock
        scope.launch {
            analysis = SampleStockApi.fetchAiAnalysis(stock.code)
            showSheet = true
        }
    }

    fun openDetail(stock: StockItem) {
        val pj = JSONObject()
        pj.put("code", stock.code)
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stockDetail", pj)
    }

    fun openReport() {
        val pj = JSONObject()
        activity.acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("aiReport", pj)
    }

    Column(modifier = Modifier.fillMaxSize().background(AppColors.PageBg)) {
        // Header: search row + tabs
        Column(modifier = Modifier.background(AppColors.HeaderBg).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f).height(34.dp)
                        .background(Color.White, RoundedCornerShape(17.dp))
                        .padding(horizontal = 12.dp)
                ) {
                    Text(text = "搜索", color = AppColors.SubGray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text(text = "搜索", color = AppColors.MainText, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).clickable { })
            }
            TabBar(selected = selectedTab, onSelect = { selectedTab = it })
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item { MarketOverviewBar() }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            items(stocks, key = { it.id }) { item ->
                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var base by remember { mutableStateOf<Rect?>(null) }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords = it }
                        .offset {
                            if (draggingStock?.id == item.id) IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt())
                            else IntOffset(0, 0)
                        }
                        .pointerInput(item) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingStock = item
                                    dragDelta = Offset.Zero
                                    base = coords?.boundsInWindow()
                                },
                                onDrag = { _, amount -> dragDelta += amount },
                                onDragEnd = {
                                    val b = base
                                    val bar = barBounds
                                    if (b != null && bar != null) {
                                        val cx = b.left + dragDelta.x + b.width / 2
                                        val cy = b.top + dragDelta.y + b.height / 2
                                        val hit = cx >= bar.left && cx <= bar.right && cy >= bar.top && cy <= bar.bottom
                                        if (hit) openPanel(item)
                                    }
                                    draggingStock = null
                                },
                                onDragCancel = { draggingStock = null },
                            )
                        }
                ) {
                    StockCard(item, onClick = { openDetail(item) }, onOpenAi = { openPanel(item) })
                }
            }
            item { Spacer(modifier = Modifier.height(90.dp)) }
        }

        // Bottom area fixed
        Column(modifier = Modifier.background(AppColors.PageBg)) {
            AiBottomBar(
                onClick = { stocks.firstOrNull()?.let { openPanel(it) } },
                modifier = Modifier.onGloballyPositioned { barBounds = it.boundsInWindow() },
            )
            BottomNav(selected = "行情")
        }
    }

    if (showSheet && activeStock != null && analysis != null) {
        ModalBottomSheet(
            visible = showSheet,
            onDismissRequest = { showSheet = false },
            containerColor = AppColors.PageBg,
            scrimColor = Color(0x66000000),
            dismissOnDrag = true,
            dismissThreshold = 0.25f,
            animationDurationMillis = 250,
        ) {
            AiBottomSheet(analysis = analysis!!, stock = activeStock, onDismiss = { showSheet = false }, onViewReport = { openReport() })
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. Verify the `Alignment`, `Color`, `Spacer`, `height`, `fillMaxWidth`, `clickable`, `RoundedCornerShape`, `Text`, `sp` imports (all present in the Step 1 block; `weight(1f)` needs no import — it is a `ColumnScope`/`RowScope` member). If `ModalBottomSheet`'s `scrimColor = Color(0x66000000)` is rejected, drop the `scrimColor` line (defaults are fine).

- [ ] **Step 3: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. Likely needed additions: `import com.example.task1.components.Spacer` (already used as `Spacer`), `import com.tencent.kuikly.compose.foundation.layout.height`, `import com.tencent.kuikly.compose.ui.graphics.Color`, `import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape`, `import com.tencent.kuikly.compose.foundation.clickable`, `import com.tencent.kuikly.compose.foundation.layout.Arrangement`. Add them to the import block. Component `Spacer` is `com.tencent.kuikly.compose.foundation.layout.Spacer` + `height`.

---

## Task 13: `pages/StockDetailPage.kt` (placeholder)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/pages/StockDetailPage.kt`

**Interfaces:**
- Produces: `@Page("stockDetail") class StockDetailPage : ComposeContainer()`. Placeholder.

- [ ] **Step 1: Write the file**

```kotlin
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

@Page("stockDetail")
class StockDetailPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

---

## Task 14: `pages/AiReportPage.kt` (placeholder full report)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/task1/pages/AiReportPage.kt`

**Interfaces:**
- Consumes: `MiniStockCard`, `TrendSection`, `RiskSection`, `BuySection`, `SampleStockApi`, `AppColors`.
- Produces: `@Page("aiReport") class AiReportPage : ComposeContainer()`.

- [ ] **Step 1: Write the file**

```kotlin
package com.example.task1.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.task1.components.BuySection
import com.example.task1.components.MiniStockCard
import com.example.task1.components.RiskSection
import com.example.task1.components.TrendSection
import com.example.task1.data.AiAnalysis
import com.example.task1.data.SampleStockApi
import com.example.task1.data.StockItem
import com.example.task1.theme.AppColors
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page

@Page("aiReport")
class AiReportPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent { AiReportScreen() }
    }
}

@Composable
fun AiReportScreen() {
    var stock by remember { mutableStateOf<StockItem?>(null) }
    var analysis by remember { mutableStateOf<AiAnalysis?>(null) }
    LaunchedEffect(Unit) {
        val first = SampleStockApi.fetchWatchlist().firstOrNull()
        stock = first
        analysis = first?.let { SampleStockApi.fetchAiAnalysis(it.code) }
    }
    Column(modifier = Modifier.fillMaxSize().background(AppColors.PageBg).padding(16.dp)) {
        Text(text = "完整分析报告", color = AppColors.MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (analysis != null && stock != null) {
            MiniStockCard(stock)
            Spacer(modifier = Modifier.height(16.dp))
            TrendSection(analysis!!)
            Spacer(modifier = Modifier.height(16.dp))
            RiskSection(analysis!!)
            Spacer(modifier = Modifier.height(16.dp))
            BuySection(analysis!!)
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`. Remove the unused `fillMaxWidth` import if the compiler flags it (`fillMaxWidth` is not used in this file).

---

## Task 15: Full compile + manual rendering checklist

**Files:** (none — verification only)

- [ ] **Step 1: Full shared-module compile (single authoritative gate)**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL` with zero errors. This is the whole-project gate — all 14 files above must compile together and fix any cross-file import/name mismatches found here.

- [ ] **Step 2: Launch page on Android with `pageName=watchlist`**

Android app reads a `pageName` extra in `KuiklyRenderActivity` (default `router`). Launch with `pageName=watchlist` to render the list page.

- [ ] **Step 3: Manual checklist (compare against Figma frames `1:360` / `1:177` and the design spec):**
  - [ ] Header: search bar + 「搜索」button + LazyRow tabs; selected tab is `sp+1` larger; switching tabs re-renders.
  - [ ] Market overview: `+23.45亿` red 24sp, `已收盘`, date, `涨23`/`跌12`, gradient progress bar (red 267/392, remainder green).
  - [ ] Stock cards: AI cards (80dp, sparkles + green `MACD...` footer) vs normal cards (46dp, top row only) — auto-selected by `aiEnabled`.
  - [ ] Tap card body → navigates to `stockDetail` placeholder page.
  - [ ] Tap card's AI footer row → opens the AI bottom sheet.
  - [ ] Long-press + drag a card onto the 「分析智窗」 bar, release → opens the AI bottom sheet. (If gesture/scroll conflict prevents it, the tap-fallback below must still work.)
  - [ ] Tap 「分析智窗」 bar (no drag) → opens the panel with the default (first) stock — guaranteed reachable.
  - [ ] Panel: handle / header (sparkles + 智能分析 + Power by Ai模型 + × close) / mini card / 涨势分析 (sparkline + MACD金叉形成) / 风险评估 (3-segment gauge + 中低风险) / 买入建议 (progress bar + 目标价/止损) / CTA 「查看完整报告」.
  - [ ] Panel close: × button and tap-outside/dismiss-on-drag both close it with the 250ms animation.
  - [ ] CTA「查看完整报告」→ navigates to `aiReport` page (reuses the same sections).
  - [ ] Icons render (SVG); if an icon is blank, switch `AppIcon` to the `<name>@2x` PNG fallback and re-run.

---

## 明确不做 (out of scope — do not implement)

- No new dependencies / no Tailwind.
- No real market API (only the reserved `StockApi` interface; `SampleStockApi` is the sample).
- No full stock-detail or full-report content (both are placeholder pages).
- Drag-to-智窗 is best-effort; the tap paths (card AI row, 智窗 bar tap) are the guaranteed-reachable entry points to the AI panel.
