package com.example.task1.backend.ai

import com.example.task1.backend.model.RawStock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AiEngineTest {
    private fun stock(changePct: Double, pe: Double): RawStock = RawStock(
        code = "600519", market = "sh", name = "贵州茅台",
        price = 185600, change = 1230, changePct = changePct,
        high = 190000, low = 182000, open = 183000,
        marketCap = 2_340_000_000_000L, floatCap = 1_000_000_000_000L, pe = pe,
    )

    @Test
    fun `action tiers align with AiThresh`() {
        assertEquals(AiLabels.ACTION_FOCUS, deriveProfile(stock(5.0, 30.0)).action)       // >3%
        assertEquals(AiLabels.ACTION_DIP, deriveProfile(stock(2.0, 30.0)).action)          // 1..3
        assertEquals(AiLabels.ACTION_HOLD, deriveProfile(stock(0.0, 30.0)).action)         // -1..1
        assertEquals(AiLabels.ACTION_AVOID, deriveProfile(stock(-2.0, 30.0)).action)       // <=-1
    }

    @Test
    fun `signal tiers align with AiThresh`() {
        assertEquals(AiLabels.SIGNAL_VOLUME, deriveProfile(stock(3.0, 0.0)).signal)        // 涨幅>2 且 PE<=0
        assertEquals(AiLabels.SIGNAL_MACD, deriveProfile(stock(3.0, 30.0)).signal)         // 涨幅>2
        assertEquals(AiLabels.SIGNAL_BOTTOM, deriveProfile(stock(0.0, 10.0)).signal)       // PE in 1..20
        assertEquals(AiLabels.SIGNAL_OVERSOLD, deriveProfile(stock(0.0, 50.0)).signal)     // else
    }

    @Test
    fun `score tiers compute same bounds as client`() {
        // 大涨幅 + 好估值 -> 高分档(>=85)
        val high = deriveProfile(stock(8.0, 10.0))
        assertEquals(AiLabels.SCENARIO_ADD, high.scenario)
        // 跌幅深 -> 低分/回避
        val low = deriveProfile(stock(-5.0, 50.0))
        assertEquals(AiLabels.SCENARIO_CUT, low.scenario)
    }

    @Test
    fun `profileForAll maps one profile per input in order`() {
        val items = listOf(stock(5.0, 30.0), stock(-2.0, 50.0), stock(0.0, 60.0))
        val profiles = runBlocking { RuleEngineAiProvider.profileForAll(items) }
        assertEquals(3, profiles.size)
        // 首项 changePct=5.0,pe=30: momentum=60*,value=15,risk=8 -> 50+60+15+8=143,coerce 到 100
        assertEquals(100, profiles[0].score)
        assertEquals(AiLabels.ACTION_FOCUS, profiles[0].action)
        assertEquals(AiLabels.ACTION_AVOID, profiles[1].action)
    }

    @Test
    fun `deriveAnalysis uses client deriveAiAnalysis口径`() {
        val r = stock(2.35, 32.0)
        val p = deriveProfile(r)
        val a = deriveAnalysis(r, p)
        // changePct=2.35 < ACTION_FOCUS_PCT(3.0) 且 >0 -> "短期震荡偏强"
        assertEquals("短期震荡偏强", a.trendLabel)
        // score=50+17+15+8=90 >= 85 -> "低风险"
        assertEquals("低风险", a.riskLevel)
        assertEquals((r.price + r.price * AiThresh.TARGET_RATIO).toLong(), a.targetPrice)
        assertEquals((r.price - r.price * AiThresh.STOP_RATIO).toLong(), a.stopLossPrice)
    }
}
