package com.pixiv.reader.core.ui.theme

/**
 * 全局时长常量（ms）。
 *
 * 自动消失 / 骨架脉冲等**固定时长**统一引用本常量，禁止裸数字；
 * 过渡类动画不在此定义时长——M3 组件由 `MotionScheme.expressive()`（见 Theme.kt）接管，
 * 业务层手写过渡引用 `MaterialTheme.motionScheme` 的 spring 规格（阅读器翻页等
 * 领域特化手感除外）。
 */
object Durations {
    /** 通知自动消失时间（Notification.kt） */
    const val NOTIFICATION_TIMEOUT = 2600L

    /** 骨架呼吸脉冲单程时长（Skeleton.kt；Material 加载节奏，比页面切换更缓） */
    const val SKELETON_PULSE_MS = 1000
}
