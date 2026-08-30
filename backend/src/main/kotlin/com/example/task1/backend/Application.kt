package com.example.task1.backend

import com.example.task1.backend.ai.LlmAiProvider
import com.example.task1.backend.ai.RuleEngineAiProvider
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
    val service = WatchlistService(client, ai)
    embeddedServer(Netty, port = config.port) {
        modules(service)
    }.start(wait = true)
}
