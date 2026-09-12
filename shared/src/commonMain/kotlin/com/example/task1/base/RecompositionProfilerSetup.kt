package com.example.task1.base

import com.tencent.kuikly.compose.profiler.RecompositionProfiler

/**
 * Recomposition Profiler 接入封装。
 *
 * Kuikly 2.27.0 内置的 Compose 重组性能分析器（`com.tencent.kuikly.compose.profiler`）。
 * 用法：在入口 Compose 页（[com.example.task1.pages.WatchlistPage]）的 `willInit` 里调用
 * [setupAndStart]，真机操作结束后调用 [stopAndFlush]，再用 `kuikly-recomposition-analyzer`
 * skill 分析 `profiler_report.json` / `profiler_frames.jsonl`。
 *
 * 输出：App cache 目录 `KuiklyProfiler/` 下。
 * Android 拉取：`adb shell run-as <包名> cat cache/KuiklyProfiler/profiler_report.json`
 *
 * 注意：`configure(...)` 的参数类型（RecompositionConfigBuilder）在 Kuikly 内为 internal，
 * 业务模块无法调用，故本封装只用 public 的 start/stop/getReport/excludeByPrefix，
 * 采集配置走框架默认值。
 */
object RecompositionProfilerSetup {

    /** 是否已启动（避免重复 start）。 */
    private var started = false

    /** 启动 Profiler（使用默认配置）。排除框架自身组件，聚焦业务 Composable。 */
    fun setupAndStart() {
        if (started) return
        // 排除框架自身包，只关注业务代码
        RecompositionProfiler.excludeByPrefix(
            "com.tencent.kuikly",
            "androidx.compose",
        )
        RecompositionProfiler.start()
        started = true
    }

    /** 停止采集（数据已落盘，可拉取分析）。 */
    fun stop() {
        if (!started) return
        RecompositionProfiler.stop()
        started = false
    }

    /** 停止并返回本次聚合报告（内存态；文件版见 cache/KuiklyProfiler/）。 */
    fun stopAndFlush(): com.tencent.kuikly.compose.profiler.RecompositionReport {
        stop()
        return RecompositionProfiler.getReport(true)
    }
}
