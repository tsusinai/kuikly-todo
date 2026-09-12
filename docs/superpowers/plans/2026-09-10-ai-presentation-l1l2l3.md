# 实施计划：AI 交互呈现升级 L1+L2+L3

> 目标：让 AI 分析「惊艳」——过程可见（L1）、文案会说话（L2）、推理可信（L3）。
> 范围：**仅 `shared/commonMain` 前端呈现层**，不碰后端规则、不动 mock 假数据。
> App 侧验收 = 编译门禁 + mock 手测（`shared` 无测试源集）。

## 现状基线（已读全部相关源码）

- `AiBottomSheet`（弹层）：进入即 `shown=true`，三个 section 错峰 `expandVertically`，但**无「思考」阶段**。
- `AiBottomBar`（智窗）：`thinking` 态只显示静态文字「思考中......」。
- `AiReportPage`（报告页）：数据未加载前是空 Column，**无加载态**。
- `AiSections` 三个 section 文案为模板拼接（`驱动X(N分),Y` 式）。
- 数据可用：`AiBottomSheet` 同时拿到 `stock: StockItem?`（含 `aiProfile`）+ `analysis: AiAnalysis`（含 `factors`），L3 推理链无需改数据模型。
- assets 可用图标：`sparkles / trending-up / shield-alert / award / arrow-right / x-circle`（无 spinner/check 图标，需用文本或 Canvas 自绘）。

---

## 阶段一（L1）：AI「思考」过程可视化

### 任务 1：新建可复用「AI 思考」组件 `AiThinking.kt`

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiThinking.kt`（新建）
- 要做的：一个 `@Composable fun AiThinking(modifier)`，展示 sparkles 徽章脉动 + 思考词轮播。
  用 `remember { mutableStateOf }` + `LaunchedEffect` 循环切换思考词列表：
  `["分析行情数据", "匹配个股画像", "评估风险因素", "生成操作建议"]`（每 700ms 切一个）。
  视觉：`AiIconBadge(Large)` + 下方当前思考词（`AppColors.SubGray`）。
  脉动用 `animateFloatAsState` + `graphicsLayer { scaleX/scaleY }` 在 0.9~1.1 间循环（`rememberInfiniteTransition` 若可用，否则用 LaunchedEffect 循环改 target）。
- 验证：编译通过；组件可独立复用。

### 任务 2：弹层加「思考 → 结论涌现」阶段

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiBottomSheet.kt`
- 要做的：把 `shown` 拆成两阶段。新增 `thinking` 状态：进入后先 `thinking=true`，`delay(900)` 后 `thinking=false; shown=true`。
  弹层主体：`if (thinking) AiThinking(...)` else 现有三个 section（保留错峰动画）。
- 验证：编译通过；打开弹层先看到思考动画，约 0.9s 后 section 展开。

### 任务 3：报告页加「AI 思考」加载态

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/pages/AiReportPage.kt`
- 要做的：`AiReportScreen` 里 `if (analysis == null || stock == null)` 时显示 `AiThinking`（居中），
  替代当前空 Column。数据就绪后显示报告内容。
- 验证：编译通过；进报告页先看到思考动画，数据返回后显示报告。

### 任务 4：智窗思考态升级为思考词轮播

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiBottomBar.kt`
- 要做的：`thinking` 态把静态「思考中......」改为**循环切换的思考词**（复用同一份思考词逻辑，抽到 `AiThinking` 的 companion 或顶层常量 `ThinkingPhrases`）。因智窗空间小，只显示文字轮播（无徽章）。
- 验证：编译通过；思考中文字会轮播。

---

## 阶段二（L2）：文案「会说话」

### 任务 5：新建叙事函数 `narrateAnalysis`

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/data/AiNarrative.kt`（新建，纯函数）
- 要做的：`fun narrateTrend(item, analysis): String`、`narrateRisk`、`narrateBuy` 三个纯函数，
  把模板字段改写成**连贯人话**，关键数值/判断用全角「」或留待 L2 高亮（文本层先不加粗，加粗见任务 6）。
  示例（趋势）：「贵州茅台今日涨 2.35%，近 5 日持续放量上行，MACD 已形成金叉；短期看涨信号明显。」
  （由 `item.name`/`changePct`/`analysis.trendLabel`/`aiProfile.signal` 拼出，逻辑与现有 `deriveAiAnalysis` 一致，仅改「表述方式」。）
- 验证：纯函数，编译通过即可。

### 任务 6：`AiSections` 三 section 关键数字/判断高亮

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiSections.kt`
- 要做的：三个 section 的正文改用 `narrate*` 输出，并用 `buildAnnotatedString` 把**关键判断**（如「看涨」「低风险」「建议分批建仓」）和**关键数字**（涨跌幅、目标价）加粗/标色（`AppColors.RiseRed`/`AppColors.Green`）。
  `SectionHeader` 的 rightText 不变。
- 验证：编译通过；正文为叙述句且关键信息高亮。

### 任务 7：风险评级加「为什么」小字

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiSections.kt`（`RiskSection`）
- 要做的：风险仪表下方加一行 `riskReason` 小字（`AppTypography.Caption`，`SubGray`），由 `analysis.factors` 推导：如「振幅 2.8% 波动可控 · PE 32 估值适中 · 趋势向上」。
- 验证：编译通过。

---

## 阶段三（L3）：可解释推理链

### 任务 8：新建推理数据纯函数 `buildReasoning`

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/data/AiNarrative.kt`（追加）
- 要做的：`fun buildReasoning(item, analysis): List<ReasonStep>`，`ReasonStep` 为 `data class(标题, List<String> 要点)`。
  三个 section 各一组：涨势（均线/量能/信号）、风险（振幅/估值/利空）、买入（操作建议/评分/目标止损价）。
  要点为「① ② ③」式短句，`✓` 用文本「✓」替代（无 check 图标）。
- 验证：纯函数，编译通过。

### 任务 9：弹层加「AI 推理链」可展开区 + 信心度标签

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/AiBottomSheet.kt`（或抽 `AiReasoning.kt`）
- 要做的：
  1. 弹层 header「Power by Ai模型」旁加**信心度标签**：`data-confidence`，由 `analysis.factors` 非空字段数估算（如 `信心 92%`，简单映射：有 3 因子分→高信心）。放 `AiBadgeBg` 小标签。
  2. 底部 CTA 上方加「展开 AI 推理过程」折叠区（`remember { mutableStateOf(false) }` + `AnimatedVisibility(expandVertically)`），展开后渲染 `buildReasoning` 的三组要点。
- 验证：编译通过；展开/收起正常。

### 任务 10：股票卡片 AI 建议行加「为什么」入口

- 文件路径：`shared/src/commonMain/kotlin/com/example/task1/components/StockCard.kt`（`AiBriefRow`）
- 要做的：建议行 sparkles 徽章旁加一个小「?」或「为什么」文字按钮，点击弹出轻量说明（复用 `ModalBottomSheet` 或 `Tooltip`）。说明内容由 `item.aiProfile` 生成一句「来源」：「基于近 5 日走势 + {signal} 信号」。
  **简化取舍**：为控制范围，先用「点击建议行 → 直接进入 AI 弹层（已有行为）」承载解释，仅在该行文案尾部加「· 依据：{signal}」小字，不新增弹层。
- 验证：编译通过；建议行文案带依据说明。

---

## 收尾验证

1. App 编译门禁：`GRADLE_USER_HOME=D:/gradle-user-home-2`，`:shared:compileDebugKotlinAndroid --rerun-tasks` 应 `BUILD SUCCESSFUL`。
2. mock 手测点（口头核验）：
   - 打开 AI 弹层 → 先思考动画 → 三段错峰展开 → 文案为叙述句 + 关键信息高亮。
   - 报告页 → 有思考加载态 → 展示对应股票（上一轮已修 code 透传）。
   - 弹层可展开「AI 推理过程」，看到三步式要点。
   - 智窗思考态文字轮播。

## 风险与取舍

- **Kuikly 动画能力受限**：`fadeIn/scaleIn` 已知不生效（rules 记录），脉动用 `graphicsLayer` scale 而非 `scaleIn`。`rememberInfiniteTransition` 若不可用，用 `LaunchedEffect` 循环改 `animateFloatAsState` target。
- **无 spinner/check 图标**：思考词轮播用文本，要点「✓」用文本字符。
- **信息密度**：L3 推理链需控制默认折叠，避免弹层过长（已用 `AnimatedVisibility` 默认收起）。
- **YAGNI**：`AiThinking` 与推理链组件仅覆盖当前弹层/报告页/智窗三处，不预建通用框架。
