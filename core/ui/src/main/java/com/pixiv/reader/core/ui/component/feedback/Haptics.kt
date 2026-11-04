package com.pixiv.reader.core.ui.component.feedback

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 全 App 触觉反馈语义门面：把「业务语义」映射为平台 [HapticFeedbackConstants]，
 * 避免各调用点散落硬编码常量与 API 版本判断。
 *
 * 仅做轻量触觉提示（tick / tap / confirm），不承担长震或波形震动——后者走系统震动 API。
 */
object PixivHaptics {

    /**
     * 触觉语义档位（调用点按意图选档，不关心平台常量差异）。
     *
     * - [SELECT]：列表/页签切换等轻量选中
     * - [TOGGLE]：开关类切换（收藏、追更等状态的轻提示）
     * - [SUCCESS]：操作成功确认（发送、收藏成功）
     * - [GESTURE_START]：手势起始（下拉刷新等）
     */
    enum class HapticCue { SELECT, TOGGLE, SUCCESS, GESTURE_START }

    /** 各语义上一次触发的时间戳（`SystemClock.uptimeMillis`），下标与 [HapticCue.ordinal] 对应。 */
    private val lastFired = LongArray(HapticCue.entries.size)

    /** 同一语义两次触发的最小间隔（ms）；低于该间隔的重复触发直接丢弃，防止连点震动堆积。 */
    private const val THROTTLE_MS = 100L

    /**
     * 按语义触发一次触觉反馈（带 per-cue 节流门）。
     *
     * 节流状态为进程级共享数组：[THROTTLE_MS] 内的重复触发直接丢弃，
     * 避免快速连点把触觉叠成持续震动。
     *
     * 约定：仅主线程调用（UI 事件回调天然满足），因此节流数组无需加锁。
     *
     * @param cue 触觉语义档位
     * @param view 执行触觉反馈的 View（用于 `performHapticFeedback`）
     * @return 无返回值
     */
    fun cue(cue: HapticCue, view: View) {
        // 节流门：同一语义 100ms 内只响一次（仅主线程调用，无需加锁）；
        // 0L 表示该语义尚未触发过（开机后 uptime 可能不足 100ms，避免误丢首次反馈）
        val now = SystemClock.uptimeMillis()
        val last = lastFired[cue.ordinal]
        if (last != 0L && now - last < THROTTLE_MS) return
        lastFired[cue.ordinal] = now

        // 语义 → 平台常量：API 30 才有的档位降级到 VIRTUAL_KEY（minSdk 26）
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

/**
 * 组合期取当前 View 并封装为「语义 → 触觉」回调，供 UI 层直接调用。
 *
 * 返回的 lambda 在组合期已绑定 [LocalView]；调用点无需再持有 View。
 *
 * @return 接收 [PixivHaptics.HapticCue] 并触发一次触觉反馈的回调
 */
@Composable
fun rememberHapticCue(): (PixivHaptics.HapticCue) -> Unit {
    // 仅在 LocalView 变化时重建（Activity 重建 / 窗口迁移）
    val view = LocalView.current
    return remember(view) { { cue: PixivHaptics.HapticCue -> PixivHaptics.cue(cue, view) } }
}
