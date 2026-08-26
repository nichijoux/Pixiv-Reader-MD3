package com.pixiv.reader.feature.notification

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 相对时间结果：[Res] 走 stringResource 格式化，[Date] 为直接展示的日期文本。 */
sealed interface RelativeTime {
    /** 文案资源（刚刚 / N 分钟前 / N 小时前 / N 天前）。 */
    data class Res(val resId: Int, val arg: Long = 0L) : RelativeTime

    /** 超过一周：直接显示日期（yyyy-MM-dd，本地时区）。 */
    data class Date(val text: String) : RelativeTime
}

/**
 * 通知时间（ISO 8601，如 `2026-08-26T12:30:00+09:00`）转相对时间。
 * 解析失败或时间在未来（时钟偏差）返回 null，UI 不显示时间。
 */
fun relativeTime(iso: String?, nowMs: Long): RelativeTime? {
    val t = iso?.let {
        runCatching { OffsetDateTime.parse(it.trim()).toInstant().toEpochMilli() }.getOrNull()
    } ?: return null
    val diffMin = (nowMs - t) / 60_000
    return when {
        diffMin < 0 -> null
        diffMin < 1 -> RelativeTime.Res(R.string.notification_time_just_now)
        diffMin < 60 -> RelativeTime.Res(R.string.notification_time_minutes_ago, diffMin)
        diffMin < 60 * 24 -> RelativeTime.Res(R.string.notification_time_hours_ago, diffMin / 60)
        diffMin < 60L * 24 * 7 -> RelativeTime.Res(R.string.notification_time_days_ago, diffMin / (60L * 24))
        else -> RelativeTime.Date(
            Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        )
    }
}
