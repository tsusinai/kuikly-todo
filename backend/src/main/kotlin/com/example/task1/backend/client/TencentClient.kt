package com.example.task1.backend.client

import com.example.task1.backend.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** 腾讯行情抓取接缝:只负责取回原始 `~` 串,不解析。query 形如 "sh600519,hk00700"。 */
interface TencentClient {
    suspend fun fetch(query: String): String

    /** 按绝对 URL 取回原文(K 线/分时走的是另一个 host)。 */
    suspend fun fetchUrl(url: String): String
}

/**
 * 用 JDK HttpURLConnection 实现。注入 httpGet 以便单测/换实现。
 * 网络失败抛上游异常(由 service 转 [com.example.task1.backend.service.UpstreamUnavailable] → 502)。
 */
class HttpTencentClient(
    private val config: Config,
    private val httpGet: suspend (url: String, timeoutMs: Long) -> String = ::defaultHttpGet,
) : TencentClient {
    override suspend fun fetch(query: String): String =
        fetchUrl(config.tencentQuoteUrl + query)

    override suspend fun fetchUrl(url: String): String =
        httpGet(url, config.tencentTimeoutMs)
}

/** 默认实现:JDK HttpURLConnection,Referer/UA 头,超时,GBK 解码(name 中文)。IO 调度器执行。 */
private suspend fun defaultHttpGet(url: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutMs.toInt()
    conn.readTimeout = timeoutMs.toInt()
    conn.setRequestProperty("Referer", "https://gu.qq.com/")
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
    conn.inputStream.bufferedReader(Charset.forName("GBK")).readText()
}
