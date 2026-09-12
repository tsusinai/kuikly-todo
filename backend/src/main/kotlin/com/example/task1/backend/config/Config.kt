package com.example.task1.backend.config
/** 服务配置:端口/腾讯URL/超时/LLM开关。默认值硬编码于 Config.load(),可被环境变量覆盖。 */
data class Config(
    val port: Int,
    val tencentQuoteUrl: String,
    val tencentTimeoutMs: Long,
    val aiProvider: String,          // "rule" | "llm"
    val tencentKlineUrl: String = DEFAULT_KLINE_URL,
    val tencentMinuteUrl: String = DEFAULT_MINUTE_URL,
    val llmBaseUrl: String = "",     // OpenAI 兼容 base,如 http://host/v1
    val llmApiKey: String = "",
    val llmModel: String = "",
    val llmTimeoutMs: Long = 4500L,
) {
    companion object {
        /** 腾讯 K 线基址,最终拼 `<token>,<period>,,,<count>,qfq`。 */
        const val DEFAULT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param="
        /** 腾讯分时基址,最终拼 `<token>`。 */
        const val DEFAULT_MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query?code="

        fun load(env: Map<String, String> = System.getenv()): Config {
            val aiProvider = (env["AI_PROVIDER"] ?: "rule").also {
                require(it == "rule" || it == "llm") { "Unknown AI_PROVIDER '$it': must be 'rule' or 'llm'" }
            }
            val port = requireNotNull((env["PORT"] ?: "8080").toIntOrNull()) {
                "Invalid PORT '${env["PORT"]}': must be an integer"
            }
            val tencentTimeoutMs = requireNotNull((env["TENCENT_TIMEOUT_MS"] ?: "5000").toLongOrNull()) {
                "Invalid TENCENT_TIMEOUT_MS '${env["TENCENT_TIMEOUT_MS"]}': must be a long"
            }
            val llmTimeoutMs = requireNotNull((env["LLM_TIMEOUT_MS"] ?: "4500").toLongOrNull()) {
                "Invalid LLM_TIMEOUT_MS '${env["LLM_TIMEOUT_MS"]}': must be a long"
            }
            return Config(
                port = port,
                tencentQuoteUrl = env["TENCENT_QUOTE_URL"] ?: "http://qt.gtimg.cn/q=",
                tencentTimeoutMs = tencentTimeoutMs,
                aiProvider = aiProvider,
                tencentKlineUrl = env["TENCENT_KLINE_URL"] ?: DEFAULT_KLINE_URL,
                tencentMinuteUrl = env["TENCENT_MINUTE_URL"] ?: DEFAULT_MINUTE_URL,
                llmBaseUrl = env["LLM_BASE_URL"] ?: "",
                llmApiKey = env["LLM_API_KEY"] ?: "",
                llmModel = env["LLM_MODEL"] ?: "",
                llmTimeoutMs = llmTimeoutMs,
            )
        }
    }
}
