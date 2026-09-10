package com.example.task1.backend

import com.example.task1.backend.ai.LlmAiProvider
import com.example.task1.backend.ai.LlmSummaryProvider
import com.example.task1.backend.ai.RuleEngineAiProvider
import com.example.task1.backend.ai.RuleSummaryProvider
import com.example.task1.backend.ai.SummaryProvider
import com.example.task1.backend.client.HttpTencentClient
import com.example.task1.backend.config.Config
import com.example.task1.backend.route.modules
import com.example.task1.backend.service.WatchlistService
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = Config.load()
    val client = HttpTencentClient(config)
    val ai = if (config.aiProvider == "llm") LlmAiProvider() else RuleEngineAiProvider
    val summary: SummaryProvider =
        if (config.aiProvider == "llm" && config.llmBaseUrl.isNotBlank()) LlmSummaryProvider(config)
        else RuleSummaryProvider
    val service = WatchlistService(client, ai, summary)
    embeddedServer(Netty, port = config.port) {
        modules(service)
    }.start(wait = true)
}
