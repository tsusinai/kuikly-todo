# 后端代理服务(Ktor)设计

- **日期**: 2026-08-30
- **项目**: `D:\Codes\Task1`(Kuikly KMP 股票 App)
- **关联**: `../docs/superpowers/specs/2026-08-30-watchlist-data-source-contract.md`(客户端定义的数据源契约)

## 定位

自建后端代理:腾讯 `qt.gtimg.cn` → 后端(解析 + 分析) → 客户端 UI。客户端**不直连腾讯、零解析**。本仓库现有 App 是 `shared`(Kuikly KMP);本设计新增独立 `backend/`(JVM 服务)。

**本轮回范围(已确认)**:行情代理 `/watchlist` + AI 分析(规则引擎 + LLM 缝) + 弹层 `/analysis/:code` + 运维基建(/health、日志、Docker)。

## 已确认决策

| 决策点 | 结论 |
|---|---|
| 技术栈 | **Kotlin 2.1 + Ktor(Netty)** + kotlinx-serialization + kotlinx-coroutines(JVM) |
| 项目结构 | **单模块分层**(`backend/` 一个 Gradle 项目);多模块(contract-dto + server)记为演进项,本轮不做 |
| 契约共享 | **App 不动**;后端**自建 DTO 镜像契约**;契约单一事实源 = 数据源契约 spec(2026-08-30) |
| 股票清单 | 客户端持有(传 `?codes=…`);后端只按 codes 查腾讯 |
| AI 画像 | 服务端 `AiAnalysisProvider` 接口;默认规则引擎复刻客户端阈值;**预留 `LlmAiProvider` 缝**(失败回退规则) |
| 分析口径 | 弹层 `/analysis/:code` 用与 App 一致的 `deriveAiAnalysis` 逻辑(看涨/风险/目标/止损) |

## 技术栈与依赖(build.gradle.kts)

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    application
}
dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.x")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.x")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.x")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.x")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.x")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.x")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.x")
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.x")
    testImplementation(kotlin("test"))
}
```

## 目录结构(单模块分层)

```
backend/
  build.gradle.kts            # 如上
  settings.gradle.kts
  gradle.properties
  src/main/kotlin/.../          # package: com.example.task1.backend
    Application.kt              # 入口:装插件、路由、启 Ktor
    config/Config.kt            # 端口/腾讯URL/超时/LLM开关(application.conf + 环境变量覆盖)
    dto/                        # 契约 DTO(自建,镜像 App 契约;@Serializable)
      StockItemDto.kt
      AiProfileDto.kt
      AiAnalysisDto.kt
      WatchlistResponseDto.kt
    client/TencentClient.kt     # 只做网络:拉 qt.gtimg.cn 原始串 + 市场前缀拼码 + 超时/重试;不解析
    parse/QuoteParser.kt        # ~ 分隔解析 + 字段位号 + GBK name + HK/A股分支;纯函数
    model/RawStock.kt           # 中间值:已解析行情 + code/name/market(AiEngine 输入;非外部契约)
    ai/AiEngine.kt              # interface AiAnalysisProvider + RuleEngineAiProvider(默认) + LlmAiProvider(缝)
    service/WatchlistService.kt # 编排:拉取→解析→AiEngine→组装响应;单股失败计数
    route/Routes.kt             # GET /watchlist /analysis/:code /health
  src/main/resources/application.conf
  src/test/kotlin/.../          # QuoteParserTest / AiEngineTest / WatchlistServiceTest
  Dockerfile                    # JRE + fat jar
  README.md                     # 启动/联调说明
```

## 组件职责(单一目、可独立测)

- **QuoteParser**:纯函数 `parse(body: String?, market: String): Quote?`。字段位号集中为常量(`RawQuote`),关键位缺失返回 null(该股计入 missing)。只做字符串→结构化,不做网络。
- **TencentClient**:只负责取原始串。`fetch(query: String): String`;拼 URL、加 headers(`Referer: https://gu.qq.com/`、UA),超时、网络失败抛上游错误。可选响应缓存(TTL)防压制腾讯。
- **AiEngine**:`interface AiAnalysisProvider { suspend fun profileForAll(items: List<RawStock>): List<AiProfile> }`(批量,便于 LLM 一次调用)。默认 `RuleEngineAiProvider`:复刻客户端四维阈值(操作建议/信号/评分/场景),字段集中在常量(与 App 的 `AiLabels`/`AiThresh` 对齐为**单一事实源**)。`LlmAiProvider`:预留接真 LLM,失败/超时回退规则。**批量比客户端原来的单股 `profileFor` 更适合服务端 LLM。**
- **WatchlistService**:编排。对每个 code:`TencentClient` 取原文 → `QuoteParser` 解析 → 组 `RawStock` → `AiEngine.profileForAll` → 填 `aiProfile/aiEnabled/aiBrief` → 产出 `WatchlistResponseDto`;单股失败 `missing++` 保留成功项。
- **Routes**:绑定 path,校验 `codes` 参数(格式:市场前缀 + 代码,逗号分隔),序列化/反序列化 JSON。

## 数据流

`GET /watchlist?codes=sh600519,hk00700,sz300750,…`
1. 校验 `codes`(非法 → 400)。
2. `TencentClient.fetch(query)` 拼 `q=` 原文;整体网络失败 → 502(或 `{error:"upstream_unavailable"}`)。
3. `QuoteParser.parse` 逐股解析(命中失败 → `missing++`)。
4. `AiEngine.profileForAll` 为成功股填四维画像 → `aiEnabled = score >= 80`;`aiBrief = "<signal>,<action>(<score>分)"`。
5. 返回 `{"stocks":[…], "missing":0}`。

`GET /analysis/:code`
1. 拉取该 code 行情 → 解析 → `deriveAiAnalysis`(看涨倾向/风险级别/评分/目标价/止损价,口径与 App 一致)。
2. 返回 `AiAnalysisDto`;未找到 → 404。

`GET /health` → `{status:"ok"}`(探活)。

**字段位号(已对照真实响应核实;与 App `TencentStockApi.RawQuote` 一致)**
- `name(1)/code(2)/price(3)/open(5)/change(31)/changePct(32)/high(33)/low(34)/pe(39,A股与HK均为39;40恒为空)/floatCap(44,单位亿)/marketCap(45,单位亿)`。
- 价位字段 元→分(`×100`);市值字段 亿→元(`×1e8`)。HK 与 A 股在 31..45 位号一致(pe 同为 39,40 恒为空)。

**响应(每股,镜像 App `StockItem`)**
```json
{
  "code": "600519", "name": "贵州茅台",
  "price": 185600, "change": 1230, "changePct": 0.67,
  "high": 190000, "low": 182000, "open": 183000,
  "marketCap": 2340000000000, "floatCap": 1000000000000,
  "pe": 30.1,
  "aiProfile": { "action": "重点关注", "signal": "MACD金叉", "score": 95, "scenario": "建议加自选" },
  "aiEnabled": true, "aiBrief": "MACD金叉,重点关注(95分)"
}
```
- 后端不返回 `fetchedAt/source`(那属客户端缓存概念,客户端盖章)。

## 错误处理

| 情形 | 状态 | 响应 |
|---|---|---|
| `codes` 非法/空 | 400 | `{error:"invalid_codes"}` |
| 腾讯整体网络失败 | 502 | `{error:"upstream_unavailable"}` |
| 单股解析失败 | 200 | 保留成功项 + `missing++` |
| `/analysis` 代码不存在 | 404 | `{error:"not_found"}` |

日志:Ktor CallLogging(**INFO**,记录 method/path/耗时/status);异常过滤器统一 `{error:…}` 兜底(不打内部堆栈到响应)。

## 测试(后端有 JVM 单测;与 App 的"无测试源集"不同)

- `QuoteParserTest`:字段位号正确性、`missing`/null 分支、HK vs A股 pe、GBK name,已知样例向量。
- `AiEngineTest`:四维阈值边界(重点/低吸/观望/回避、分档、场景映射),与 App 阈值常量对齐断言。
- `WatchlistServiceTest`:注入 fake `TencentClient`/`AiEngine`,验证编排(成功/部分失败/整体失败)、DTO 字段。
- `RoutesTest`(可选,test-host):`/health`、参数校验、错误码。

## 运维

- `Dockerfile`:多阶段构建 → 运行 JRE(JRE 17),`ENTRYPOINT ["java","-jar","app.jar"]`。
- 配置走环境变量:端口(`PORT`,默认 8080)、腾讯 URL(`TENCENT_QUOTE_URL`)、请求超时(`TENCENT_TIMEOUT_MS`)、LLM 开关(`AI_PROVIDER = rule|llm`)。`application.conf` 提供默认值。
- `/health` 供负载探活;不做鉴权(演示/内网阶段)。

## 明确不做 / 演进项

- **不做**:多模块拆分(contract-dto + server)——范围小,YAGNI。
- **不做**:真实 LLM 接入——只留 `LlmAiProvider` 缝 + `aiProvider=llm` 开关;接入是后续。
- **不做**:客户端(shared)任何改动——App 不动,契约以 spec 为源。
- **不做**:鉴权/限流/多用户——演示阶段。
- **演进项**:(1) 抽独立 `contract` KMP 模块做单一契约源,App 与后端共用;(2) `TencentClient` 加响应缓存/速率限制;(3) `/watchlist` 支持增量/轮询。

---
产出此 spec 后进入实现计划(writing-plans)。文档路径:`D:\Codes\Task1\backend\2026-08-30-backend-design.md`。
