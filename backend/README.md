# 后端代理服务(task1-backend)

腾讯行情 → 后端(解析 + AI 分析)→ 客户端 UI。**客户端零解析**:`~` 分隔协议、GBK 中文名、
行业归类、四维画像、因子明细、复权 K 线全部在后端完成,App 只搬字段。

## 启动

```bash
cd backend
./gradlew run          # 需要 JDK 17
```

或打 Docker:

```bash
cd backend
docker build -t task1-backend .
docker run -p 8080:8080 -e PORT=8080 task1-backend
```

> Dockerfile 的 COPY 是相对路径,必须在 `backend/` 目录下执行。
> 镜像基于 `eclipse-temurin:17-jre`,以 `installDist` 产物的 `lib/*` 作 classpath 运行,
> 每次容器内 `gradle installDist` 生成的 `build/install/task1-backend` 即运行目录。

## 配置(环境变量)

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | 8080 | 服务端口 |
| `TENCENT_QUOTE_URL` | `http://qt.gtimg.cn/q=` | 行情基址,最终拼 `q=<codes>` |
| `TENCENT_KLINE_URL` | `https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=` | K 线基址,最终拼 `<token>,<period>,,,<count>,qfq` |
| `TENCENT_MINUTE_URL` | `https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=` | 分时基址,最终拼 `<token>` |
| `TENCENT_TIMEOUT_MS` | 5000 | 上游请求超时(毫秒) |
| `AI_PROVIDER` | `rule` | `rule`(本地规则) / `llm`(走 LLM 摘要,需下面的 LLM_*) |
| `LLM_BASE_URL` | 空 | OpenAI 兼容 base,如 `http://host/v1` |
| `LLM_API_KEY` | 空 | 留空则不发送 `Authorization` |
| `LLM_MODEL` | 空 | 模型名 |
| `LLM_TIMEOUT_MS` | 4500 | LLM 请求超时(毫秒) |

`AI_PROVIDER=llm` 但 `LLM_BASE_URL` 为空时,会自动回落到规则引擎,不会因配置不全而 500。

## 接口

| 方法 | 路径 | 成功响应 |
|---|---|---|
| GET | `/health` | `{"status":"ok"}` |
| GET | `/watchlist?codes=sh600519,hk00700,…` | `{"stocks":[…],"missing":0,"summary":{…}}` |
| GET | `/analysis/{token}` | 单股 AI 分析(含 `factors`),不存在 → 404 |
| GET | `/chart/{token}?period=day` | 走势 K 线 / 分时 |

`token` 形如 `sh600519` / `sz300750` / `hk00700`。

### `/chart` 细节

- `period` 取 `intraday` / `day` / `week` / `month` / `year`,缺省 `day`。
- 返回 `{code, period, prevClose, bars:[{label,open,high,low,close,volume}], avgPrice}`,
  价格单位**分**、成交量单位**手**,`label` 已按周期格式化(`10:32` / `09-12` / `26-09` / `2026`)。
- `avgPrice` 仅分时非空(累计成交额 ÷ 累计成交量),与 `bars` 等长。
- 年 K 由月 K 本地聚合(腾讯 `year` 端点只回 1 根,不可用)。
- 前复权把早期价格压到 0 以下的根**会被丢弃**(实测 `sz300750` 2018 年 `open=-542`),
  否则图表 Y 轴范围会被撑爆。

### 错误码

`400 invalid_codes`(token 格式非法)/ `400 invalid_period`(周期非法)/
`502 upstream_unavailable`(腾讯不可达)/ `404 not_found`(上游无此标的)/ `500 internal_error`。

## 测试

```bash
cd backend
./gradlew test
```

解析层的用例夹具全部取自**真实响应**(2026-09-12 抓取),改动字段位号时请一并更新夹具与注释。
