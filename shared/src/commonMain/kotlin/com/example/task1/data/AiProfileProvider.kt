package com.example.task1.data

/**
 * AI 画像提供方接缝。
 *
 * 现状:腾讯行情接口不含 AI,画像由本地规则引擎 deriveAiProfile 推导(唯一产物)。
 * 之后接入真实大模型时,实现本接口(把行情/quote 发给后端,返回 AiProfile),
 * 并注入 TencentStockApi;调用点与页面层无需改动。
 */
interface AiProfileProvider {
    /** 由个股(含实时行情字段)产出 AI 四维画像。suspend 以支持未来异步 LLM 调用。 */
    suspend fun profileFor(item: StockItem): AiProfile
}

/**
 * 默认实现:本地规则引擎。纯同步推导,行为与现状完全一致。
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
 *    接收返回的 AiProfile(action/signal/score/scenario);失败/超时回退 fallback(本地规则)。
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
