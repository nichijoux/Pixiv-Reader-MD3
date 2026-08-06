package com.pixiv.reader.core.ui.theme

/**
 * 全局时长常量（ms）。
 *
 * 动画 / 自动消失 / 延迟统一引用本常量，禁止裸数字。
 */
object Durations {
    /** 通知自动消失时间（Notification.kt） */
    const val NOTIFICATION_TIMEOUT = 2600L

    /** 页面切换动画时长（RankingList / DiscoverResults / UserRoute 的 AnimatedContent） */
    const val PAGE_SWITCH_ANIM_MS = 700

    /** 阅读器 UI 淡出延迟 */
    const val READER_BAR_HIDE_MS = 3000L

    /** 阅读器进入页面后的短暂延迟 */
    const val READER_UI_DELAY_MS = 800L
}
