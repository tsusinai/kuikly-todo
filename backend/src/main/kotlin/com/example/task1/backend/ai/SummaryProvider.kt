package com.example.task1.backend.ai

import com.example.task1.backend.config.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 摘要接缝:规则兜底或 LLM。context=行情上下文,fallback=规则文案。 */
interface SummaryProvider {
    suspend fun summarize(context: String, fallback: String): String
}

/** 默认:直接返回规则兜底文案(与 App 端 deriveSummary 文本一致)。 */
object RuleSummaryProvider : SummaryProvider {
    override suspend fun summarize(context: String, fallback: String): String = fallback
}

/**
 * OpenAI 兼容 /chat/completions 实现。失败(未配置 baseUrl / 非 2xx / 超时 / 解析失败 / 无 text 字段)
 * 一律回退 [fallback],绝不把异常抛给上层。
 */
class LlmSummaryProvider(
    private val config: Config,
    private val fallback: SummaryProvider = RuleSummaryProvider,
    private val httpPost: suspend (url: String, apiKey: String, body: String, timeoutMs: Long) -> String = ::defaultHttpPost,
) : SummaryProvider {
    override suspend fun summarize(context: String, fallback: String): String {
        return try {
            if (config.llmBaseUrl.isBlank()) return this.fallback.summarize(context, fallback)
            val url = config.llmBaseUrl.trimEnd('/') + "/chat/completions"
            val body = buildJsonObject {
                put("model", config.llmModel)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", context)
                    }
                }
            }.toString()
            val raw = httpPost(url, config.llmApiKey, body, config.llmTimeoutMs)
            extractText(raw) ?: this.fallback.summarize(context, fallback)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            this.fallback.summarize(context, fallback)
        }
    }

    /**
     * 按优先级从响应取文本:
     * 1) OpenAI 兼容 `choices[0].message.content`:能二次解析出 `text` 字段则用它,否则用 content 原文;
     * 2) 根节点 `text`;都取不到则返回 null。
     */
    private fun extractText(raw: String): String? {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val content = runCatching {
            root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (!content.isNullOrBlank()) {
            val nested = runCatching {
                Json.parseToJsonElement(content).jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return nested?.takeIf { it.isNotBlank() } ?: content
        }
        return root["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是行情点评助手。只返回 JSON,形如 {\"text\":\"不超过60字的中文点评\"},不要输出其他内容。"
    }
}

/** 默认实现:JDK HttpURLConnection POST,超时,非 2xx 抛 IOException。IO 调度器执行。 */
private suspend fun defaultHttpPost(url: String, apiKey: String, body: String, timeoutMs: Long): String =
    withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = timeoutMs.toInt()
        conn.readTimeout = timeoutMs.toInt()
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }
