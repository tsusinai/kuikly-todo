package com.example.task1.backend.config

/** 服务配置:端口/腾讯URL/超时/LLM开关。默认值来自 application.conf,可被环境变量覆盖。 */
data class Config(
    val port: Int,
    val tencentQuoteUrl: String,
    val tencentTimeoutMs: Long,
    val aiProvider: String,   // "rule" | "llm"
) {
    companion object {
        fun load(env: Map<String, String> = System.getenv()): Config = Config(
            port = (env["PORT"] ?: "8080").toInt(),
            tencentQuoteUrl = env["TENCENT_QUOTE_URL"] ?: "http://qt.gtimg.cn/q=",
            tencentTimeoutMs = (env["TENCENT_TIMEOUT_MS"] ?: "5000").toLong(),
            aiProvider = env["AI_PROVIDER"] ?: "rule",
        )
    }
}
