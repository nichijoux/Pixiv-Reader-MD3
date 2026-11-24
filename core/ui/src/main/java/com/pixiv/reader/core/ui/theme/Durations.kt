package com.pixiv.reader.core.ui.theme

/**
 * 全局固定时长（ms）。过渡类动画不在此定义：M3 组件由 MotionScheme 接管，业务过渡取 motionScheme 的 spring 规格。
 */
object Durations {
    const val NOTIFICATION_TIMEOUT = 2600L

    const val NOTIFICATION_ACTION_TIMEOUT = 4200L

    const val SKELETON_PULSE_MS = 1000
}
