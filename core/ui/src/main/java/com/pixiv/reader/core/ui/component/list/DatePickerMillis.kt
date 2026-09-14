package com.pixiv.reader.core.ui.component.list

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * yyyy-MM-dd → DatePicker 使用的 UTC 零点毫秒（M3 DatePicker 契约为 UTC 零点，
 * 禁止用系统时区换算，否则 UTC+ 时区会高亮前一天）。
 *
 * @param date 日期字符串（yyyy-MM-dd）
 * @return 对应 UTC 零点毫秒；格式非法时返回 null
 */
fun parseDatePickerMillis(date: String): Long? = runCatching {
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

/**
 * DatePicker 的 UTC 零点毫秒 → yyyy-MM-dd。
 *
 * @param millis DatePicker 选中值的 UTC 毫秒
 * @return yyyy-MM-dd 日期字符串
 */
fun formatDatePickerMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
