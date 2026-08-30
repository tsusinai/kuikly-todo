package com.example.task1.backend.config

/** 服务配置:端口/腾讯URL/超时/LLM开关。默认值硬编码于 Config.load(),可被环境变量覆盖。 */
data class Config(
    val port: Int,
    val tencentQuoteUrl: String,
    val tencentTimeoutMs: Long,
    val aiProvider: String,   // "rule" | "llm"
) {
    companion object {
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
            return Config(
                port = port,
                tencentQuoteUrl = env["TENCENT_QUOTE_URL"] ?: "http://qt.gtimg.cn/q=",
                tencentTimeoutMs = tencentTimeoutMs,
                aiProvider = aiProvider,
            )
        }
    }
}
