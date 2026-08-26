package com.pixiv.reader.feature.notification

/**
 * 通知 `target_url`（pixiv:// 深链）的解析结果。
 *
 * 每条通知只有一个跳转目标：整行 / 头像 / 缩略图统一按该结果跳转
 * （数据层无独立 userId/workId 字段，无法按区域拆分目标）。
 */
sealed interface NotificationTarget {
    /** 用户主页（关注类通知）。 */
    data class User(val userId: Long) : NotificationTarget

    /** 插画详情。 */
    data class Illust(val illustId: Long) : NotificationTarget

    /** 小说详情。 */
    data class Novel(val novelId: Long) : NotificationTarget

    /** 未识别（新通知类型 / 非 pixiv scheme）：忽略跳转并打日志。 */
    data object Unknown : NotificationTarget
}

/**
 * 解析通知 `target_url` 为应用内跳转目标。
 *
 * 兼容官方复数形式（`pixiv://users/123`、`pixiv://illusts/456`、`pixiv://novels/789`）
 * 与应用内单数深链形式（`pixiv://user/123` ...），带查询参数亦可。
 * 纯字符串解析（不依赖 android.net.Uri），本地 JVM 单测可覆盖。
 */
fun parseNotificationTarget(url: String?): NotificationTarget {
    if (url.isNullOrBlank()) return NotificationTarget.Unknown
    if (!url.startsWith("pixiv://", ignoreCase = true)) return NotificationTarget.Unknown
    val body = url.substring("pixiv://".length).substringBefore('?')
    val parts = body.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return NotificationTarget.Unknown
    val kind = parts[0].lowercase().removeSuffix("s")
    val id = parts[1].toLongOrNull() ?: return NotificationTarget.Unknown
    return when (kind) {
        "user" -> NotificationTarget.User(id)
        "illust" -> NotificationTarget.Illust(id)
        "novel" -> NotificationTarget.Novel(id)
        else -> NotificationTarget.Unknown
    }
}
