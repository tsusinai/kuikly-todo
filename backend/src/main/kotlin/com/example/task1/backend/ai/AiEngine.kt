package com.example.task1.backend.ai

import com.example.task1.backend.dto.AiProfileDto
import com.example.task1.backend.model.RawStock

/**
 * AI 画像提供方接缝。
 *
 * 当前默认规则引擎复刻客户端四维阈值,是演示模型。未来接入真 LLM:把批量行情发往后端 LLM 服务
 * 一次返回 List<AiProfile>(批量profileForAll 比单股 profileFor 更适合服务端 LLM);失败/超时回退规则。
 * 调用点与 DTO 无改。
 */
interface AiAnalysisProvider {
    suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto>
}

/** 默认:本地规则引擎,纯同步推导,行为与客户端 deriveAiProfile 完全一致。 */
object RuleEngineAiProvider : AiAnalysisProvider {
    override suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto> =
        items.map { deriveProfile(it) }
}

/** 真实大模型实现占位。默认未接入,当前实例化不会真正走 LLM。 */
class LlmAiProvider(
    private val fallback: AiAnalysisProvider = RuleEngineAiProvider,
) : AiAnalysisProvider {
    override suspend fun profileForAll(items: List<RawStock>): List<AiProfileDto> {
        // TODO(接真实 LLM):把 items 行情打包发往自研 LLM 服务 → 返回 List<AiProfileDto>;
        //  失败/超时回退 fallback。
        return fallback.profileForAll(items)
    }
}
