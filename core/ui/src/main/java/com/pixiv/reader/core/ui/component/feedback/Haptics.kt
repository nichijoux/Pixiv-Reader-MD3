package com.pixiv.reader.core.ui.component.feedback

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** 全 App 轻触觉语义门面（长震/波形震动走系统震动 API，不归此处）。 */
object PixivHaptics {

    enum class HapticCue { SELECT, TOGGLE, SUCCESS, GESTURE_START }

    private val lastFired = LongArray(HapticCue.entries.size)

    private const val THROTTLE_MS = 100L

    /** 进程级 per-cue 100ms 节流（防连点震动堆积）；仅主线程调用，不加锁。 */
    fun cue(cue: HapticCue, view: View) {
        // 0L 表示尚未触发过，避免设备开机 uptime 不足 100ms 时误丢首次反馈
        val now = SystemClock.uptimeMillis()
        val last = lastFired[cue.ordinal]
        if (last != 0L && now - last < THROTTLE_MS) return
        lastFired[cue.ordinal] = now

        // API 30 以下降级到 VIRTUAL_KEY（minSdk 26）
        val constant = when (cue) {
            HapticCue.SELECT -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.TOGGLE -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticCue.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }

            HapticCue.GESTURE_START -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHapticCue(): (PixivHaptics.HapticCue) -> Unit {
    val view = LocalView.current
    return remember(view) { { cue: PixivHaptics.HapticCue -> PixivHaptics.cue(cue, view) } }
}
