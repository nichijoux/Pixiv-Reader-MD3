package com.pixiv.reader.feature.notification

/**
 * 每条通知只有 target_url 一个跳转目标，整行 / 头像 / 缩略图共用
 *（数据层无独立 userId/workId 字段，无法按区域拆分）。
 */
sealed interface NotificationTarget {
    /** 用户主页。 */
    data class User(val userId: Long) : NotificationTarget

    /** 插画详情。 */
    data class Illust(val illustId: Long) : NotificationTarget

    /** 小说详情。 */
    data class Novel(val novelId: Long) : NotificationTarget

    /** 未识别（新类型 / 非 pixiv scheme）：忽略跳转并打日志。 */
    data object Unknown : NotificationTarget
}

/**
 * 兼容官方复数形式（pixiv://users/123）与应用内单数深链形式，带查询参数亦可。
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
