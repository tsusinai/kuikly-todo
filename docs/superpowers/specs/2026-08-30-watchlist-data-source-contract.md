# 自选页主列表数据来源 — 契约规范

- **日期**: 2026-08-30
- **项目**: `D:\Codes\Task1`(Kuikly Compose KMP)
- **前置**: 基于 `2026-08-30-watchlist-ai-grouping-tencent-design.md`(已落地:腾讯实时 `TencentStockApi` + AI 分组)。本文**只定义主列表 UI 的数据来源契约**,不改分组/卡片逻辑。

## 结论

自选页主列表数据源改为**后端代理**架构:

```
腾讯 qt.gtimg.cn  ──>  自研后端代理(解析 + 分析)  ──>  UI
```

- **契约由客户端定义**:客户端声明它需要的 JSON 形态;后端负责把腾讯原始 `~` 串转换(格式转化)成该形态。
- **客户端零解析**:字段位号(change 32/33、pe 39/40、市值 44/45)、GBK 解码、name 取用 全部留服务端。
- **客户端传 codes 拉取**:客户端持有自选清单,`GET /watchlist?codes=sh600519,hk00700,…` 按需拉。

## 契约

### 请求
```
GET {backend}/watchlist?codes=sh600519,hk00700,sz300750,…
```
- 客户端自选清单(7 只)+ 市场前缀(`sh/sz/hk`)由客户端拼接,后端按 codes 查腾讯。

### 响应(每股归一化 JSON,镜像 `StockItem`)
```json
{
  "code": "600519",
  "name": "贵州茅台",
  "price": 185600, "change": 1230, "changePct": 0.67,
  "high": 190000, "low": 182000, "open": 183000,
  "marketCap": 2340000000000, "floatCap": 1000000000000,
  "pe": 30.1,
  "aiProfile": { "action": "重点关注", "signal": "MACD金叉", "score": 95, "scenario": "建议加自选" },
  "aiEnabled": true,
  "aiBrief": "MACD金叉,重点关注(95分)"
}
```
- 单位:价格/涨跌/高低/开 = **分(Long)**;市值/流通市值 = **元(Long)**;与 `StockItem` 现状一致。
- `name` 由服务端解析(客户端本地 codebook 作废)。
- 后端另做**真实分析**(LLM/后端)填充 `aiProfile`;客户端不再本地 rule-engine 推导。

### StockApi 签名变更
```kotlin
suspend fun fetchWatchlist(): WatchlistBundle

data class WatchlistBundle(
    val stocks: List<StockItem>,
    val fetchedAt: Long,   // 客户端盖章:本次成功(或保持缓存)的更新时间
    val source: DataSource,
)
enum class DataSource { LIVE, CACHE, OFFLINE }
```
- `fetchedAt`/`source` 由客户端计算(缓存层概念,后端响应本身不含)。后端只回 `{stocks:[…]}`(可另带自己的 `dataTime`,是否用再定)。
- 其余 `StockApi` 方法(`fetchAiAnalysis`/`fetchStock`/`fetchGlobalAdvice`)签名不变;`fetchGlobalAdvice` 继续废弃。

## 三态与回退冒泡

| 场景 | 结果 | `source` |
|---|---|---|
| 实时拉取成功 | 更新 `stocks` + `fetchedAt=now` | `LIVE` |
| 拉取失败,有上次缓存 | 保留缓存 + 标「非实时」 | `CACHE` |
| 首载无缓存 + 失败(当前分支) | 回退固定数据(mock)+ 标「离线」 | `OFFLINE` |
| 首载无缓存 + 失败(对接后端后) | **错误态 + 重试**(不再 mock) | — |

- **非实时判定**:`source == CACHE` → 角标 + 显示 `fetchedAt`(更新时间 HH:mm:ss);**不引入时间阈值魔法常量**。
- **刷新形态**:仅**手动下拉**——fork `material3.PullToRefresh` 已支持:外层 `rememberPullToRefreshState(isRefreshing)` + `LazyList` 首项 `pullToRefreshItem(state, onRefresh, scrollState)`。需 `rememberLazyListState()` 并传给 `LazyColumn(state=…)`。
  - 下拉 → `isRefreshing=true` → 重拉 → 成功 `LIVE`/`fetchedAt=now`;失败保留缓存 `source=CACHE`;`isRefreshing=false`。
  - **不做**定时自动刷新。

## 两条分支的分界(关键)

- **当前分支** `feat/watchlist-ai-grouping-tencent`(对接前):预留接口 + 定义契约 + **本地数据替身**。`fetchWatchlist()` 返回 `WatchlistBundle`,填充 mock/规则引擎画像,`source` 依场景 `LIVE`(本地算一次)或 `CACHE`/`OFFLINE`;下拉刷新可用。
- **对接后端分支**(后续):真后端 + 真名 + 真分析;冷启动失败改为**错误态 + 重试**(方案 b)。

## 边界(明确不做 / 不深挖)

- AI 面板(`fetchAiAnalysis`/`deriveAiAnalysis`)的分析来源:只覆盖**主列表**数据来源,面板分析归后端、此次不展开。
- 定时自动刷新。
- **不引入新依赖**(网络仍框架级 `NetworkModule`;下拉刷新用现存 fork `PullToRefresh`)。
- iOS/鸿蒙/H5/小程序不实测(本机 Android 仅验证)。

## 当前分支落地项

1. `StockApi` 接口 `fetchWatchlist()` 返回值改 `WatchlistBundle`。
2. `TencentStockApi.fetchWatchlist()` 改用新签名,成功回 `source=LIVE`;网络失败回 `empty` 由页面回退。
3. `SampleStockApi` 作为 OFFLINE 替身,`fetchWatchlist()` 包成 `WatchlistBundle(source=OFFLINE)`。
4. `WatchlistPage`:消费 `WatchlistBundle`,按 `source` 标 `LIVE/CACHE/OFFLINE`;当前分支拉取失败且有缓存→`CACHE`、无缓存→`OFFLINE`(固定数据)。
5. `material3.PullToRefresh` 下拉刷新接线(需给 `LazyColumn(state=…)` 注入 `LazyListState`,首项 `pullToRefreshItem`)。
