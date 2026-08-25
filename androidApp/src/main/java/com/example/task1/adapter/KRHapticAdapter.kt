package com.example.task1.adapter

import android.app.Activity
import android.view.HapticFeedbackConstants

/**
 * Android 触觉反馈适配器：把 shared 层桥接过来的 vibrateShort(type) 落到系统反馈。
 *
 * 归属说明：本项目 KMP 的 shared/commonMain 无 expect/actual，平台行为一律由 androidApp 宿主承担
 * （adapter/ 放平台能力、module/ 放 Kuikly 桥模块）。本文件即 vibrateShort 的 Android 单独实现。
 *
 * 用 decorView.performHapticFeedback 而非 Vibrator：
 * - 无需 VIBRATE 权限（AndroidManifest 目前无该权限；Vibrator 在 API 31+ 缺权限会抛 SecurityException）；
 * - 属于系统触觉反馈，与「短震 type=heavy」意图一致，且不崩溃。
 */
object KRHapticAdapter {

    /**
     * @param activity 宿主 Activity（用于取 decorView）；为 null 时 no-op。
     * @param type     缺省 "heavy"，可选 "heavy" / "medium" / "light"。
     * @return 是否成功触发反馈。
     */
    fun vibrate(activity: Activity?, type: String?): Boolean {
        if (activity == null) {
            return false
        }
        val feedback = when (type) {
            "heavy" -> HapticFeedbackConstants.LONG_PRESS
            "medium" -> HapticFeedbackConstants.VIRTUAL_KEY
            "light" -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.LONG_PRESS
        }
        return activity.window.decorView.performHapticFeedback(feedback)
    }
}
