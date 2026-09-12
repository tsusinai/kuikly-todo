# 重组性能分析报告

> 数据来源：Kuikly Recomposition Profiler 2.27.0 采集（真机 `89a5f9e1`）
> 数据文件：`profiler_frames.jsonl`（2631 行 / 2239 帧 / 7644 次重组）
> 分析时间：2026-09-11

## 一、数据概览

| 指标 | 数值 | 判断 |
|---|---|---|
| 总帧数 | 2239 | 正常 |
| 总重组次数 | 7644 | ⚠️ 偏高 |
| 单帧最高重组数 | 44 次 | 🔴 远超 16ms 卡顿线 |
| 耗时 >5ms 的重组 | 若干（StockCard 7-11ms、AiBottomSheet 21-23ms） | 🟡 需关注 |

## 二、核心问题（按严重度降序）

### 🔴 问题 1：`ExpandedBlock`/`RiseSparkline`/`BottomRow` 重组风暴（占总重组 85%）

- **组件**：`ExpandedBlock`（2190 次）、`RiseSparkline`（2165 次）、`BottomRow`（2162 次）
- **合计**：6517 次，占全部 7644 次重组的 **85%**
- **根因**：这三个组件是 `StockCard` 选中展开态的内容，其参数在父级每次重组时**全部判定为「变化」**：
  - `ExpandedBlock(item, selected, onEnterDetail, sparkProgress)` 的 `changedParams = [0,1,2,3]`（4 个参数全变）
  - `RiseSparkline(modifier, progress)` 的 `changedParams = [0,1]`（2 个参数全变）
- **参数变化本质**：
  1. `onEnterDetail = { openDetail(item) }` —— **lambda 每次新建**，未 `remember` 缓存（`WatchlistPage` 第 364 行）
  2. `sparkProgress` —— `animateFloatAsState(if(selected) 1f else 0f, tween(220))` 的动画中间值，补间期间每帧变化
  3. `item` —— `StockItem` 含 `List<String>`/`AiProfile` 非稳定类型，`===` 比较失败
- **影响**：列表滚动/任何父级状态变化时，这 7 张卡片的展开态子树整体重建，导致单帧 44 次重组、107ms 的卡顿帧。

### 🟡 问题 2：`cardScale`/`staggerCellModifier` 按压缩放动画

- `staggerCellModifier` 258 次、`HighLowOpenRow` 86 次
- 每张卡片内的 `animateFloatAsState` + `tween(180, delayMillis = index*60)` 错峰动画，在列表 item 复用时反复触发
- 属于「正常动画」但叠加在问题 1 之上，放大了重组规模

### 🟡 问题 3：`AiBottomSheet` 单次耗时 21-23ms

- 弹层打开时 `AiBottomSheet` 重组耗时 21-23ms，接近单帧预算
- 根因：弹层内 `TrendSection`/`RiskSection`/`BuySection` 的 `animateFloatAsState` 补间 + `AiThinking` 脉动动画叠加

## 三、优化建议（按优先级）

### 方案 A（最高优先）：缓存 lambda + 稳定 sparkProgress

**问题 1 的直接修复。** 在 `WatchlistPage` 的卡片渲染处，把 lambda 用 `remember` 缓存，并让 sparkProgress 只驱动 sparkline 而非整棵子树。

```kotlin
// ❌ 现状（WatchlistPage 卡片渲染处）
StockCard(
    item = item,
    selected = selectedId == item.id,
    onOpenAi = { openPanel(item) },       // lambda 每次新建
    onEnterDetail = { openDetail(item) },  // lambda 每次新建
)

// ✅ 优化：lambda 用 remember(item) 缓存，key 依赖 item
val onOpenAi = remember(item) { { openPanel(item) } }
val onEnterDetail = remember(item) { { openDetail(item) } }
StockCard(item, selected, onOpenAi, onEnterDetail)
```

**为什么有效**：`remember(item)` 让 lambda 只在 `item` 变化时重建，父级重组时引用稳定，`ExpandedBlock` 的参数不再「全变」，子树可被 Compose 跳过（skip）。

### 方案 B：sparkProgress 动画下沉到 RiseSparkline 内部

**问题 1 的彻底修复。** 当前 `sparkProgress` 在 `StockCard` 层 `animateFloatAsState`，导致 `ExpandedBlock`/`BottomRow` 都依赖这个动画值。应把动画下沉到 `RiseSparkline` 内部：

```kotlin
// ❌ 现状：sparkProgress 在 StockCard 层，传给 ExpandedBlock → BottomRow → RiseSparkline
val sparkProgress by animateFloatAsState(if (selected) 1f else 0f, tween(220))
ExpandedBlock(item, selected, onEnterDetail, sparkProgress)

// ✅ 优化：RiseSparkline 内部自己处理 progress，只传 selected 布尔
@Composable
fun RiseSparkline(selected: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(220))
    // ... 用 progress 画线
}
ExpandedBlock(item, selected, onEnterDetail)  // 不再传动画值
```

**为什么有效**：动画值只在 `RiseSparkline` 内部变化，`ExpandedBlock`/`BottomRow` 收到的是稳定的布尔 `selected`，不再被每帧动画值驱动重组。

### 方案 C：给 StockItem 加 @Immutable（需评估）

`StockItem` 含 `List<String> tags` 和 `AiProfile`，Compose 编译器推断为不稳定类型。若确认这些字段在创建后不变，可加 `@Immutable` 注解，让编译器信任其稳定，跳过无意义重组。

⚠️ **需满足前提**：`StockItem` 是 `data class`，字段创建后不变（实际是 immutable 的）。但 `List`/`AiProfile` 类型本身不稳定，需要确认是否真的每次传入新实例。

## 四、结论

**最严重的问题是 `ExpandedBlock` 子树的重组风暴（占 85%）**，根因是「lambda 未缓存 + sparkProgress 动画值上浮到 StockCard 层」。

**建议立即实施方案 A + B**（缓存 lambda + 动画下沉），这是低风险、高收益的修复，预计能把重组次数从 7644 降到千次以内，消除卡顿帧。

## 附：过滤配置

- 已通过 `excludeByPrefix("com.tencent.kuikly", "androidx.compose")` 排除框架组件
- 分析阈值：单次重组 >5ms 标记关注，单帧重组 >10 标记卡顿
