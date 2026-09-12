package com.example.task1.backend.service

import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.config.Config
import com.example.task1.backend.model.ChartPeriod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 记录请求到的绝对 URL,返回预置原文的 fake;行情端点被调用即失败(职责隔离)。 */
private class FakeChartClient(private val raw: String? = null) : TencentClient {
    val urls = mutableListOf<String>()
    override suspend fun fetch(query: String): String = error("行情端点不应被图表服务调用")
    override suspend fun fetchUrl(url: String): String {
        urls += url
        return raw ?: throw RuntimeException("boom")
    }
}

private val CFG = Config(
    port = 8080,
    tencentQuoteUrl = "http://qt.gtimg.cn/q=",
    tencentTimeoutMs = 5000L,
    aiProvider = "rule",
)

private const val DAY_JSON = """{"code":0,"data":{"sz300750":{"qfqday":[["2026-09-09","332.480","336.840","337.660","327.100","390240.000"],["2026-09-10","335.900","338.060","340.800","330.500","291184.000"],["2026-09-11","333.060","330.510","335.660","328.300","276600.000"]]}}}"""

private const val MINUTE_JSON = """{"code":0,"data":{"sz300750":{"data":{"data":["0930 333.06 5328 177454368.00","0931 335.22 16386 545610452.56"],"date":"20260911"},"qt":{"v_ff_sz300750":[],"sz300750":["51","宁德时代","300750","330.51","338.06","333.06"]}}}}"""

private const val HK_MINUTE_JSON = """{"code":0,"data":{"hk00700":{"data":{"data":["0930 419.400 1340974 564579788.800","0931 422.200 1805825 760723409.200"],"date":"20260912"},"qt":{"v_ff_hk00700":[],"hk00700":["100","腾讯控股","00700","428.400","425.600","419.400","15628379.0","0"]}}}}"""

class ChartServiceTest {

    @Test
    fun `day requests kline endpoint with period count and qfq`() {
        val client = FakeChartClient(DAY_JSON)
        runBlocking { ChartService(client, CFG).fetchChart("sz300750", ChartPeriod.DAY) }
        assertEquals(
            "${Config.DEFAULT_KLINE_URL}sz300750,day,,,320,qfq",
            client.urls.single(),
        )
    }

    @Test
    fun `year requests month bars so it can aggregate locally`() {
        val client = FakeChartClient(DAY_JSON)
        runBlocking { ChartService(client, CFG).fetchChart("sz300750", ChartPeriod.YEAR) }
        assertTrue(client.urls.single().endsWith("sz300750,month,,,130,qfq"))
    }

    @Test
    fun `intraday requests minute endpoint and exposes avg price`() {
        val client = FakeChartClient(MINUTE_JSON)
        val dto = runBlocking { ChartService(client, CFG).fetchChart("sz300750", ChartPeriod.INTRADAY) }!!

        assertEquals("${Config.DEFAULT_MINUTE_URL}sz300750", client.urls.single())
        assertEquals("intraday", dto.period)
        assertEquals(33806L, dto.prevClose)
        assertEquals(2, dto.bars.size)
        assertEquals(listOf(33306L, 33297L), dto.avgPrice)
        assertEquals(16386L - 5328L, dto.bars[1].volume)
    }

    @Test
    fun `hk intraday passes shares-per-unit so avg price lands in fen`() {
        val client = FakeChartClient(HK_MINUTE_JSON)
        val dto = runBlocking {
            ChartService(client, CFG).fetchChart("hk00700", ChartPeriod.INTRADAY)
        }!!

        assertEquals("${Config.DEFAULT_MINUTE_URL}hk00700", client.urls.single())
        assertEquals(42560L, dto.prevClose)
        assertEquals(listOf(42102L, 42126L), dto.avgPrice)
    }

    @Test
    fun `day prev close is the second to last bar and bars are mapped in order`() {
        val dto = runBlocking {
            ChartService(FakeChartClient(DAY_JSON), CFG).fetchChart("sz300750", ChartPeriod.DAY)
        }!!

        assertEquals(listOf("09-09", "09-10", "09-11"), dto.bars.map { it.label })
        assertEquals(33806L, dto.prevClose)          // 倒数第二根收盘,不是首根
        assertEquals(33248L, dto.bars[0].open)       // 开
        assertEquals(33684L, dto.bars[0].close)      // 收
        assertEquals(33766L, dto.bars[0].high)       // 高
        assertEquals(32710L, dto.bars[0].low)        // 低
        assertTrue(dto.avgPrice.isEmpty())
    }

    @Test
    fun `upstream network failure becomes UpstreamUnavailable`() {
        assertFailsWith<UpstreamUnavailable> {
            runBlocking { ChartService(FakeChartClient(null), CFG).fetchChart("sz300750", ChartPeriod.DAY) }
        }
    }

    @Test
    fun `payload without bars returns null so route can answer 404`() {
        val empty = """{"code":0,"data":{"sz300750":{"qfqday":[]}}}"""
        assertNull(runBlocking { ChartService(FakeChartClient(empty), CFG).fetchChart("sz300750", ChartPeriod.DAY) })
    }
}
