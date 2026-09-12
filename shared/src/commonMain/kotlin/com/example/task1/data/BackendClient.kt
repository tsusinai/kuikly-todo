package com.example.task1.data

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 自研后端(task1-backend)基址。
 *
 * 各端到宿主机的方式不同,因此不写死单一地址,而是按 [hostCandidates] 顺序探测 `/health`,
 * 命中即缓存复用。全部不可达 → [resolvedBaseUrl] 为 null,由 [RoutedStockApi] 整体回退
 * 「直连腾讯 → 本地样例」,页面不会白屏。
 *
 * 候选顺序刻意把 `127.0.0.1` 放首位:真机经 `adb reverse tcp:8080 tcp:8080` 后它秒连,
 * 而模拟器上它只是本机回环、快速失败后落到 `10.0.2.2`,两种环境都很快。
 * 走局域网时把 `http://<本机IP>:8080` 插到首位即可。
 */
object BackendConfig {
    var hostCandidates: List<String> = listOf(
        "http://127.0.0.1:8080",   // 真机 adb reverse / 桌面
        "http://10.0.2.2:8080",    // Android 模拟器访问宿主机
    )

    /** 探活超时(秒)。故意短:后端没起时不拖慢首屏。 */
    var probeTimeoutSec: Int = 3

    /** 业务请求超时(秒)。 */
    var requestTimeoutSec: Int = 10

    internal var resolvedBaseUrl: String? = null
    internal var probed: Boolean = false

    /**
     * 保护 [resolvedBaseUrl]/[probed] 的探测锁。
     *
     * 详情页会并行拉 5 个周期,而「读 probed → 探活(suspend)→ 写 probed」不是原子的,
     * 不加锁时每个并发调用都会各探一次 /health。锁住后进程内只探一次。
     */
    internal val probeLock = Mutex()

    /** 重置探测缓存(调试/切换环境用)。 */
    fun reset() {
        resolvedBaseUrl = null
        probed = false
    }
}

/**
 * 自选清单(本地固定)。
 *
 * 只维护「市场前缀 + 代码」;股票名称与行业由数据源给出——后端按 GBK 解出真名,
 * 直连腾讯时回落到本地兜底名,离线样例源自带名称。
 */
object WatchlistCodes {
    val tokens: List<String> = listOf(
        "sh600519",   // 贵州茅台
        "hk00700",    // 腾讯控股
        "sz300750",   // 宁德时代
        "sz002594",   // 比亚迪
        "sh601318",   // 中国平安
        "sz000858",   // 五粮液
        "sh688981",   // 中芯国际
    )

    /** `/watchlist?codes=` 的查询串。 */
    val query: String get() = tokens.joinToString(",")

    /** 纯代码 → 带市场前缀的 token;不在清单内返回 null。 */
    fun tokenOf(code: String): String? = tokens.firstOrNull { it.substring(2) == code }
}

/**
 * 后端 JSON 取数。
 *
 * Kuikly 的 NetworkModule 对 JSON 回包**直接给出解析后的对象**(只有非 JSON 回包才会被包一层
 * `{"data":"<原文>"}`),因此这里直接读回调的 `data` 字段,不再二次解包。
 *
 * 不可达 / 非 2xx / 不可解析一律返回 null,由调用方决定降级,不抛异常。
 */
internal class BackendHttp(private val network: () -> NetworkModule) {

    suspend fun getJson(path: String, timeoutSec: Int = BackendConfig.requestTimeoutSec): JSONObject? {
        val base = baseUrl() ?: return null
        return request(base + path, timeoutSec)
    }

    /** 按候选顺序探活 /health,首个可用者胜出并缓存;全失败则记 null(下次不再重探)。 */
    private suspend fun baseUrl(): String? = BackendConfig.probeLock.withLock {
        if (BackendConfig.probed) return@withLock BackendConfig.resolvedBaseUrl
        var found: String? = null
        for (candidate in BackendConfig.hostCandidates) {
            if (request(candidate + "/health", BackendConfig.probeTimeoutSec) != null) {
                found = candidate
                break
            }
        }
        BackendConfig.resolvedBaseUrl = found
        BackendConfig.probed = true
        found
    }

    private suspend fun request(url: String, timeoutSec: Int): JSONObject? = suspendCoroutine { cont ->
        network().httpRequest(
            url = url,
            isPost = false,
            param = JSONObject(),
            headers = null,
            cookie = null,
            timeout = timeoutSec,
        ) { data, success, _, _ ->
            cont.resume(if (success) data else null)
        }
    }
}

/** JSON 取字段的小工具:缺字段/类型不符时给出安全默认值。 */
internal fun JSONObject.text(name: String): String = optString(name)
internal fun JSONObject.whole(name: String): Long = optLong(name)
internal fun JSONObject.flag(name: String, fallback: Boolean = false): Boolean = optBoolean(name, fallback)
internal fun JSONObject.real(name: String, fallback: Double = 0.0): Double = optDouble(name, fallback)
