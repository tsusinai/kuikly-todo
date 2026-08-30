package com.example.task1.data

/**
 * AI 画像提供方接缝。
 *
 * 【定位:演示模型 → 预留真 LLM】
 * 当前 deriveAiProfile 只用当日涨跌 + 市盈率机械推导,是**演示模型**,非真实 AI 分析。
 * 本接缝即未来接入真大模型的插入点:把个股行情发往后端 → 返回 AiProfile,
 * 注入 TencentStockApi;调用点与页面层无需改动。该边界是刻意预留的。
 *
 * 【作用域】本接缝**只对分组使用的四维 AiProfile 负责**。
 * 弹层 AiAnalysis 不在其内(由 deriveAiAnalysis 派生,真 LLM 阶段另行设计接缝)。
 *
 * 【形状】当前为单股 (profileFor)。未来真 LLM 走网络、单股串行既慢又贵,可演进为批量
 * `profileForAll(stocks): List<AiProfile>` 一次返回;调用点仍在 TencentStockApi 一处,重构成本低。
 */
interface AiProfileProvider {
    /** 由个股(含实时行情字段)产出 AI 四维画像。suspend 以支持未来异步 LLM 调用。 */
    suspend fun profileFor(item: StockItem): AiProfile
}

/**
 * 默认实现:本地规则引擎。纯同步推导,行为与现状完全一致(演示模型)。
 */
object RuleEngineAiProvider : AiProfileProvider {
    override suspend fun profileFor(item: StockItem): AiProfile = deriveAiProfile(item)
}

/**
 * 真实大模型实现占位。
 *
 * TODO(接真实 LLM):
 *  - 构造注入后端客户端/请求工厂。
 *  - profileFor:把 item 的行情字段(价格/涨跌/PE/市值等)打包发送给自研 LLM 服务,
 *    接收返回的 AiProfile;失败/超时回退 fallback(本地规则)。可演进为批量 profileForAll。
 * 默认未接入,当前不实例化。
 */
class LlmAiProfileProvider(
    private val fallback: AiProfileProvider = RuleEngineAiProvider,
) : AiProfileProvider {
    override suspend fun profileFor(item: StockItem): AiProfile {
        // TODO 接入真实 LLM:向服务端发 item 行情 → 返回 AiProfile。
        return fallback.profileFor(item)
    }
}
