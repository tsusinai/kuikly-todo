package com.example.task1.backend.service

import com.example.task1.backend.client.TencentClient
import com.example.task1.backend.config.Config
import com.example.task1.backend.dto.ChartBarDto
import com.example.task1.backend.dto.ChartResponseDto
import com.example.task1.backend.model.ChartBar
import com.example.task1.backend.model.ChartPeriod
import com.example.task1.backend.parse.ChartParser

/**
 * 图表数据编排:分时走 minute 端点,K 线走 fqkline 端点,统一产出 [ChartResponseDto]。
 *
 * 上游网络失败抛 [UpstreamUnavailable](→ 502);返回 null 表示上游没有这只标的的数据(→ 404)。
 * token 与 period 的合法性由 route 先行校验,此处不再重复。
 */
class ChartService(
    private val client: TencentClient,
    private val config: Config,
) {

    suspend fun fetchChart(token: String, period: ChartPeriod): ChartResponseDto? {
        val raw = try {
            if (period == ChartPeriod.INTRADAY) {
                client.fetchUrl(config.tencentMinuteUrl + token)
            } else {
                client.fetchUrl(config.tencentKlineUrl + "$token,${period.tencent},,,${period.count},qfq")
            }
        } catch (e: Throwable) {
            throw UpstreamUnavailable(e)
        }

        if (period == ChartPeriod.INTRADAY) {
            val series = ChartParser.parseMinute(raw, sharesPerVolume = sharesPerVolumeOf(token)) ?: return null
            return ChartResponseDto(
                code = token,
                period = period.wire,
                prevClose = series.prevClose,
                bars = series.bars.map { it.toDto() },
                avgPrice = series.avgPrice,
            )
        }

        val bars = ChartParser.parseKline(raw, token, period.wire) ?: return null
        return ChartResponseDto(
            code = token,
            period = period.wire,
            prevClose = previousClose(bars),
            bars = bars.map { it.toDto() },
        )
    }

    /** 昨收 = 序列倒数第二根收盘(最后一根是当日);不足两根时退回首根开盘。 */
    private fun previousClose(bars: List<ChartBar>): Long =
        if (bars.size >= 2) bars[bars.size - 2].close else bars.first().open

    /**
     * 分时上游一单位成交量对应的股数:A 股以手计(100 股),港股以股计。
     * 港股每手股数各家不同,不能拿 100 去猜,所以只区分「是不是港股」。
     */
    private fun sharesPerVolumeOf(token: String): Int =
        if (token.startsWith("hk")) ChartParser.SHARES_PER_HK_UNIT else ChartParser.SHARES_PER_LOT
}

private fun ChartBar.toDto(): ChartBarDto = ChartBarDto(label, open, high, low, close, volume)
