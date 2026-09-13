# OpenSourceTalent 课题作业

---

## 基本信息

| 项目 | 内容 |
| --- | --- |
| ⭐ GitHub ID | [@tsusinai](https://github.com/tsusinai) |
| ⭐ 完成的 Task | Task 1 |

---

## 代码仓库

| 项目 | 链接 |
| --- | --- |
| ⭐ 仓库地址 | https://github.com/tsusinai/kuikly-todo |

---

## 课程说明

本项目完成了 **Task 1**：基于 **Kuikly + Kuikly Compose DSL** 实现了一个智能股票行情 App。

主要做了三件事：**① 行情自选与个股详情**（大盘摘要、按维度分组的自选列表等）；
**② AI 智能分析**（长按股票呼出分析智窗，给出涨势 / 风险 / 买入三段可解释结论与因子明细，
可跳转完整分析报告页）；**③ 后端代理**（把腾讯 `~` 分隔的 GBK 行情文本在后端解析成
契约化 JSON，主路径客户端零解析；为 AI 结论预留了可插拔的 LLM 接缝）。

详细的架构说明、功能清单、构建运行方式与截图见仓库 README。
