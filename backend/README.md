# 后端代理服务(task1-backend)

腾讯 `qt.gtimg.cn` → 后端(解析 + AI 分析)→ 客户端 UI。客户端零解析。

## 启动

```bash
cd backend
./gradlew run          # 依赖 JDK17
```

或打包 Docker:
```bash
docker build -t task1-backend .
docker run -p 8080:8080 -e PORT=8080 task1-backend
```

> 镜像基于 `eclipse-temurin:17-jre`,以 `installDist` 产物的 `lib/*` classpath 运行
> (`java -cp app/lib/* com.example.task1.backend.ApplicationKt`),
> 每次容器内 `gradle installDist` 生成的 `build/install/task1-backend` 目录直接作为运行目录。

## 配置(环境变量)

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | 8080 | 服务端口 |
| `TENCENT_QUOTE_URL` | `http://qt.gtimg.cn/q=` | 腾讯行情基址(最终拼 `q=<codes>`) |
| `TENCENT_TIMEOUT_MS` | 5000 | 请求超时(毫秒) |
| `AI_PROVIDER` | `rule` | `rule`(本地规则) / `llm`(预留缝,未接入) |

## 接口

- `GET /health` → `{"status":"ok"}`
- `GET /watchlist?codes=sh600519,hk00700,sz300750,…` → `{"stocks":[…],"missing":0}`
- `GET /analysis/sh600519` → 单股 AI 分析;不存在 → 404

错误码:`400 invalid_codes` / `502 upstream_unavailable` / `404 not_found`。

## 测试

```bash
cd backend && ./gradlew test
```
