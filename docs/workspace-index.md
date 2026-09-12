# 工作区索引 —— 项目结构 + 工作进程

> 日常开发的**唯一入口**。找结构看这里；构建/跑端看 `runbook-platform.md`；编码规范看 `AGENTS.md` + `rules/`。
> 最后更新：2026-09-10

---

## 一、项目结构

Task1 是一个 **Kuikly Kotlin Multiplatform 单代码库 App**（Android / iOS / HarmonyOS / H5 / 小程序）。
业务代码 **100% 在 `shared/commonMain`**，各端只做薄 host 接入。

| 路径 | 职责 | 状态 |
|---|---|---|
| **`shared/`** | **业务库（唯一真相源）** | ✅ |
| `shared/src/commonMain/kotlin/com/example/task1/` | 源码根 | — |
| &nbsp;&nbsp;`base/` | `BasePager`、`BridgeModule`（跨端桥接）、`Utils` | — |
| &nbsp;&nbsp;`components/` | 复用 UI 组件（`StockCard`、`AiBottomSheet`、`TabBar`、`Sparkline`…） | — |
| &nbsp;&nbsp;`pages/` | 页面（`WatchlistPage`、`AiReportPage`、`StockDetailPage`…） | — |
| &nbsp;&nbsp;`data/` | 数据源与推导（`StockApi`、`TencentStockApi`、`FactorDeriver`、`AiProfileDeriver`、`StockGrouping`） | — |
| &nbsp;&nbsp;`theme/` | `AppColors` | — |
| `shared/src/commonMain/assets/` | `common/` 共享图标；`$pageName/` 页面资产 | — |
| `androidApp/` | Android host | ✅ 已跑通 |
| `iosApp/` | iOS host（SwiftUI，非 Gradle 模块，`pod 'shared'`） | ⚠️ 需 macOS |
| `ohosApp/` | 鸿蒙 host（ArkTS，非 Gradle 模块，独立 `settings.ohos.gradle.kts`） | ⚠️ 缺签名/资产 |
| `backend/` | Kotlin 2.1.21 + Ktor：行情代理 + AI 推导演算 | ✅ 有测试源集 |
| `static_server/` | 本地静态/代理服务（Koa），`npm run serve` | — |
| `buildSrc/` · `gradle/` | 构建逻辑与版本目录（`libs.versions.toml`） | — |
| `rules/` | Kuikly Compose / DSL 编码规范（`.mdc`） | — |
| `docs/` | 运行手册 + specs + plans | — |
| `figma_renders/` | 设计稿渲染图（比对用） | gitignored |
| `.claude/skills/` · `.agents/skills/` | Kuikly 官方 AI skills（13 个），版本由 `skills-lock.json` 锁定 | — |
| `.superpowers/sdd/` | 子代理驱动开发台账、任务简报、审查 diff | gitignored |

### 模块边界（改代码前先确认）
- **改 UI / 业务** → `shared/commonMain`，禁止写平台相关代码。
- **改接口契约** → 先改 `docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md`（SSOT），再同步两端。
- **新增 native 能力** → 三端桥接同步补（`base/BridgeModule.kt` ↔ Android `KRBridgeModule.kt` / iOS `HRBridgeModule.m` / 鸿蒙 `KRBridgeModule.ets`）。

### ⚠️ 已知结构不一致（未修，勿被误导）
- `settings.gradle.kts` 里 `include(":h5App")` 与 `include(":miniApp")` 指向的目录**实际不存在**（两者均为空壳，小程序已评估暂不实施）。`:shared` / `:androidApp` 构建不受影响。
- `AGENTS.md` 曾写 skills 位于 `.Codex/skills/`，实际为 `.claude/skills/` 与 `.agents/skills/`（已修正 AGENTS.md）。
- 鸿蒙是**独立 Gradle 世界**（Kotlin `2.0.21-KBA-010`，Kuikly `2.7.0-2.0.21-ohos`），与主构建（Kotlin `2.1.21`）不统一。

---

## 二、工作进程

**分支**：`feat-ui-standardization`（HEAD `7f877ad`）
**工作树**：含「真实数据源 + 评分维度最终优化」的未提交改动；**仓库默认不提交**。

### 最近一轮：评分维度最终优化（对照验收标准）

对照「功能实现完整性 40% / 代码质量 25% / AI 场景 25% / 加分项 10%」补的缺口：

| 维度 | 本轮做了什么 |
|---|---|
| 功能完整性 | 详情页与报告页补齐**加载 / 失败可重试 / 空态**（`components/core/StateBox.kt`）；详情页加显式返回按钮、报告页返回键接上 `RouterModule.closePage()` |
| 页面闭环 | 详情页、报告页不再吃 mock：行情/走势/分析全部走同一条降级链，数值与主列表一致 |
| AI 场景 | App 首次真正调用自研后端，`/watchlist` 的摘要与 `/analysis` 的分析来自后端（可切真实 LLM），不再是本地规则的独角戏 |
| 真实 API | 后端新增 `/chart`（分时 + 日/周/月/年 K，接腾讯真实源）；客户端零解析的契约得以兑现 |
| 代码质量 | 新增 `data/BackendApi.kt`（后端层）/ `data/ApiRouter.kt`（降级路由）/ `data/BackendClient.kt`（基址发现）；删掉 `WatchlistViewModel` 里 `api as? TencentStockApi` 的向下转型，改为 `WatchlistBundle.missing`；均线计算抽成 `movingAverages` 供两条数据源共用 |
| 平台覆盖 | iOS 桥接补 `vibrateShort`/`toast`；鸿蒙补 Compose 插件与依赖、注册资产拷贝插件（**均未在本机验证**，见 `runbook-platform.md` §3/§4） |

**验收**：后端 `11 suites / 89 tests / 0 failures`；App `:shared:compileDebugKotlinAndroid --rerun-tasks` `BUILD SUCCESSFUL`。
**联调步骤**见 `runbook-platform.md` §9。**`shared` 无测试源集**，App 侧仍以「编译门禁 + 手测」验收。

| 计划 | 状态 |
|---|---|
| `plans/2026-08-24-watchlist-ai.md` | ✅ 已完成 |
| `plans/2026-08-30-watchlist-ai-grouping-tencent.md` | ✅ 已完成 |
| `plans/2026-08-30-backend.md` | ✅ 已完成 |
| `plans/2026-09-09-watchlist-ai-factors-summary.md`（M1–M5，13 任务） | ✅ 全部完成 |

**最近一轮（AI 因子与摘要 A1+A3 / D1–D4）**
- M1–M5 共 13 个任务全部完成并通过任务级验收（子代理驱动开发）。
- 最终整体审查结论：**Ready to merge: With fixes**，5 项发现（1 Critical + 4 Important）。
- 5 项发现**已全部修复并核验**：后端 1C+2I（HK `amount` 恒 0、HK `volumeRatio` 误读、LLM 响应取 `choices[0].message.content`）；App 2I+1Minor（D4 因子区恒 0、LIVE 主路径缺 D1/D2/D3、港股路由、契约文档重复标题）。
- 后端门禁：`9 suites / 69 tests / 0 failures`。
- App 门禁：`:shared:compileDebugKotlinAndroid` `BUILD SUCCESSFUL`（12 tasks executed）。

**权威台账**：`.superpowers/sdd/RESUME-ai-factors.md`（当前恢复点，优先级最高）· `.superpowers/sdd/progress-ai-factors.md`（任务级台账）

**下一步（按序）**
1. ⏸ **后端门禁复跑** —— 用户指示"测试下次说"，**勿重复触发**。
2. ⏭ 进入 `superpowers:finishing-a-development-branch`，向用户给出收尾选项（**严禁擅自 merge / commit / push**）。
3. 🚫 M6（App 切后端数据源）—— 明确**不在本次范围**。

**已知残留（记入 M6，勿擅自扩范围）**
- LIVE 条目 `industry` 仍为「未分类」（App 侧无 code→行业表）。
- `industryRank` 两端恒 -1（UI 分支不生效）。
- `StockCard.TagRow` 不换行，窄屏 5 标签有溢出风险。
- 配置 LLM 时「大盘基准数据缺失。」注记可能被 LLM 文案覆盖。

---

## 三、门禁与约束（勿漂移）

```powershell
# App 编译门禁（本机唯一可跑目标）
cd D:\Codes\Task1
$env:GRADLE_USER_HOME = "D:/gradle-user-home"
.\gradlew.bat :shared:compileDebugKotlinAndroid --rerun-tasks

# 后端测试门禁
cd D:\Codes\Task1\backend
$env:GRADLE_USER_HOME = "D:/gradle-user-home"
.\gradlew.bat test --rerun-tasks
```

- **Windows PowerShell**：`&&` 不可用；**必须**设 `GRADLE_USER_HOME`（用户 home 含撇号 `a'su's`，否则 JDK 17 `@argfile` 崩溃）。
- **仓库默认不提交**；禁止 `git add -A` / `git add .`。若需提交，按任务粒度 `git add <精确文件>`。
- **包导入铁律**：仅 `androidx.compose.runtime.*` 用官方包，其余一律 `com.tencent.kuikly.compose.*`。
- **fork 坑清单**见 `runbook-platform.md` §8（禁用 `fadeIn/scaleIn/alpha`、`boundsInWindow()`、`animateColorAsState`；无 `statusBarsPadding()`）。
- **腾讯行情字段位号**（2026-09-09 实测，勿漂移）见 `docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md` 与 `RESUME-ai-factors.md` §六。
- **`shared` 无测试源集** → App 侧以「编译门禁 + mock 手测」验收；TDD 仅用于 `backend`。

---

## 四、文档索引

| 想知道 | 看 |
|---|---|
| 各端怎么构建/运行、已知卡点 | `docs/runbook-platform.md` |
| 编码规范（Compose DSL / DSL） | `AGENTS.md` · `rules/kuiklyComposeDSL.mdc` · `rules/kuiklyDSL.mdc` |
| 接口字段契约（SSOT） | `docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md` |
| 产品/设计意图 | `设计规范思路.md` · `figma_renders/` · `反馈.png` |
| 当前进度 / 恢复点 | `.superpowers/sdd/RESUME-ai-factors.md` |
| 历史计划与规格 | `docs/superpowers/plans/` · `docs/superpowers/specs/` |
| 后端自身说明 | `backend/README.md` · `backend/2026-08-30-backend-design.md` |
| Kuikly 框架用法 | `.claude/skills/kuikly-*/SKILL.md` |
