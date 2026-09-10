# 自选页 AI 分析驱动分组排行 + 腾讯实时行情 — 设计规范

- **日期**: 2026-08-30
- **项目**: `D:\Codes\Task1`(Kuikly Compose KMP,单 codebase → Android/iOS/鸿蒙/H5/小程序)
- **前置**: 基于已落地的 `2026-08-24-watchlist-ai-design.md`(自选页 + AI 面板),本设计在**同一自选页**上做两件事:①扁平列表改为 **AI 分析驱动分组排行**;②数据源由内置 mock 改为 **腾讯实时行情**。

## 目标

1. **AI 分析驱动分组排行**：纵向列表按分组组织——每组一个「X股票建议」标题 + 组内成员;分组方式由客户端提供多种、用户可长按「分析智窗」自由切换。
2. **数据来源改为腾讯实时行情**：行情字段(价/涨跌/高低/开/市值/PE)从 `http://qt.gtimg.cn/q=<代码>` 实时拉取;AI 画像四维(操作建议/信号/评分/场景)由**本地规则引擎从实时行情推导**(腾讯接口不含 AI 分析)。

**范围提示(跨平台限制,已与用户确认)**:本机只有 Android 能编译运行;H5/小程序无构建目录、iOS 需 Xcode+Mac、鸿蒙需 DevEco+华为工具链。实现**以 Android 为验证范围**,其余端代码路径保留但未实测(ATS/CORS/GBK/白名单为各端潜在卡点)。

遵循 `rules/kuiklyComposeDSL.mdc`:仅 `androidx.compose.runtime.*` 用官方包,其余一律 `com.tencent.kuikly.compose.*`;页面继承 `ComposeContainer` + `@Page("name")`。

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 分组方式 | 操作建议 / 信号题材 / 评分分层 / 操作场景,四维均可切换 |
| 切换交互 | **长按「分析智窗」(AiBottomBar)** → `ModalBottomSheet` 选维度 |
| 数据来源 | 腾讯实时行情(`qt.gtimg.cn`)+ 客户端 AI 规则引擎派生画像 |
| 网络传输 | Kuikly `NetworkModule.requestGet`(框架级模块,宿主自动提供) |
| AI 画像来源 | 本地规则引擎 `deriveAiProfile(quote)`(纯函数,阈值可调) |
| 组可折叠 | 否,常驻展开全部成员;标题带数量角标 |
| 智窗文案 | 跟随当前维度,如「全盘AI建议·操作建议」 |
| 失败兜底 | 腾讯拉取失败回退 `SampleStockApi`(离线可跑)+ 弱提示 + 可重试 |

## 配置文件布局

全部位于 `shared/src/commonMain/kotlin/com/example/task1/`(相对现有目录新增/改动):

```
data/StockGrouping.kt    (新) AiProfile/GroupDimension/StockGroup + groupStocks() 纯函数
data/AiProfileDeriver.kt (新) deriveAiProfile(item) 规则引擎(实时行情→四维画像)
data/TencentStockApi.kt  (新) 腾讯行情拉取/解析 + 代码市场前缀 + code→name 映射
data/StockApi.kt         StockItem 加 aiProfile;fetchGlobalAdvice 改按分组生成摘要;SampleStockApi 补 aiProfile
components/AiBottomBar.kt 长按 + dimensionLabel
components/GroupHeader.kt (新) 分组标题(带数量角标)
components/DimensionPickerSheet.kt (新) 维度选择弹层内容
pages/WatchlistPage.kt   分组 LazyColumn 渲染 + 维度切换 + 网络加载/失败兜底
```

## 数据模型(数据层)

```kotlin
data class AiProfile(
    val action: String,   // 操作建议: 重点关注/低吸关注/持股观望/建议回避
    val signal: String,   // 信号: MACD金叉/量能放大/低位企稳/超跌反弹
    val score: Int,       // 评分 0-100
    val scenario: String, // 场景: 建议加自选/建议建仓/建议减仓/继续持有
)
enum class GroupDimension(val label: String) {
    ACTION("操作建议"), SIGNAL("信号题材"), SCORE("评分分层"), SCENARIO("操作场景")
}
data class StockGroup(val title: String, val stocks: List<StockItem>)
fun groupStocks(stocks: List<StockItem>, dimension: GroupDimension): List<StockGroup>
```

- `groupStocks` 纯函数:ACTION/SIGNAL/SCENARIO 按画像字段字符串分桶,标题 `<字段值>股票建议`;SCORE 按 `score` 分档(`>=85` 高分推荐 / `70..84` 中分观察 / `<70` 低分慎入)标题 `<档名>股票建议`;组内按 `score` 降序(并列按 `changePct` 降序);组间按维度定义的规范顺序,未命中追加在后。
- `StockItem` 增加 `val aiProfile: AiProfile`。

## 数据来源(腾讯实时)

```kotlin
class TencentStockApi(private val network: () -> NetworkModule) : StockApi { ... }
```

- **固定自选代码 + 市场前缀**:`600519/601318/688981→sh`、`300750/002594/000858→sz`、`00700→hk`;用于拼接 `q=` 参数。
- `fetchWatchlist()`:拼 `http://qt.gtimg.cn/q=sh600519,hk00700,...` → `network().requestGet(url, JSONObject())`。
- 非 JSON 回包被 SDK 包装为 `{"data":"..."}`,用 `data.optString("data")` 取原文;形如 `v_sh600519="1~贵州茅台~600519~1856.00~..."`,按 `~` split 后映射:`name(1)/code(2)/price(3)/open(5)/change(31)/changePct(32)/high(33)/low(34)/pe(39)/floatCap(44,亿)/marketCap(45,亿)`。
  - 价位字段 元→分(`×100`),市值字段 亿→元(`×1e8`);HK 与 A 股位号一致(pe 同为 39,40 恒为空)。2026-09-09 已对照真实响应核实。
- 可能需在 `httpRequest` 加 headers(`Referer: https://gu.qq.com/`、`User-Agent: Mozilla/5.0`)规避校验;实机验证。
- 每只股票取到行情后调 `deriveAiProfile(quote)` 填画像;`aiEnabled` = 画像 `score >= 80`;`aiBrief` 由画像生成一句副标题。

## AI 规则引擎

```kotlin
fun deriveAiProfile(item: StockItem): AiProfile
```

纯函数、阈值可调。示例:`changePct > 3`→重点关注/量能放大/高分;`1..3`→低吸关注;`-1..1`→持股观望;`< -1`→建议回避;`pe` 低→超跌企稳加分;`score` 归一到 0-100;`scenario` 由 action+score 映射。

## 组件与外观

- **AiBottomBar**:建议态文案前缀改为 `全盘AI建议·<dimensionLabel>`,正文仍为 `advice`;用 `detectTapGestures(onLongPress) ` 实现长按呼出维度弹层(若 fork 不支持 `onLongPress` 重载则以 `pointerInput` 自计压时);保留思考态;勿引入拖拽(拖进智窗交互此前已移除)。
- **GroupHeader**:分组标题行 `K股票建议`(MainText 加粗)+ 右侧数量角标(`AppColors.AiBadgeBg`);常驻不折叠、无点击。
- **DimensionPickerSheet**:列出 4 个 `GroupDimension`,当前项高亮,点选回调并闭层;复用 `ModalBottomSheet(visible,onDismissRequest,containerColor,scrimColor)`(4 参版)。

## 交互模型

- 长按底部「分析智窗」→ 弹维度选择 `ModalBottomSheet` → 点选 `GroupDimension` → 关闭并切换列表分组 + 智窗文案同步。
- 列表主体:每个分组先一个 `GroupHeader`,再平铺该组 `StockCard`(原卡片展开/选中/建议行点击逻辑不变,`selectedId` 跨维度保留)。
- 数据加载:`LaunchedEffect(Unit)` 拉腾讯 → 成功 `stocks=实时`;失败回退 `SampleStockApi` 并置 `offline=true`(弱提示 + 可点重试)。
- 智窗建议文案由页面根据当前 `groups` 直接生成摘要 String(如「重点关注3只、低吸2只、观望2只」);不再依赖 `fetchGlobalAdvice()` 固定文案。

## 动画

- 分组/维度切换:直接重排 LazyColumn 项(无逐项滚动动画);文件夹内卡片展开仍用原 `stockcard` 的 `AnimatedVisibility(expandVertically)`。
- 弹层:复用 `ModalBottomSheet`(现有风格),不用 `fadeIn/scaleIn`(layerBlock bug)。

## 验证

1. `./gradlew :shared:compileDebugKotlinAndroid`(JDK17;无测试源集,编译通过即达标)。
2. Android(需联网)手测:默认「操作建议」3 组;长按智窗→弹 4 维度→切「信号题材」4 组、「评分分层」3 组、「操作场景」3 组,组内按评分降序;智窗文案跟随维度;卡片价格为腾讯实时值;展开/建议行弹 AI 面板正常。
3. 失败兜底:断网进页→回退 mock + 弱提示,列表仍分组显示。
4. 跨平台:iOS/鸿蒙/H5/小程序仅保证编译思路正确,实机验证留待对应环境(本机不可测)。

## 明确不做

- 不引入新依赖(网络用框架级 `NetworkModule`,不装 ktor/okhttp 到 shared)。
- 不做真实 AI 服务(腾讯不含 AI,用本地规则引擎替代;并非调用大模型)。
- 不做跨端实测(iOS/鸿蒙/H5/小程序仅代码保留)。
- 不做拉新—刷新交互(仅进页加载 + 失败重试;下拉刷新等留作后续)。
