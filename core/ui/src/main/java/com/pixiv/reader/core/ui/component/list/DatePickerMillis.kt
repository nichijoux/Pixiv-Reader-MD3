package com.pixiv.reader.core.ui.component.list

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** M3 DatePicker 契约为 UTC 零点，禁止用系统时区换算（否则 UTC+ 时区会高亮前一天）；格式非法返回 null。 */
fun parseDatePickerMillis(date: String): Long? = runCatching {
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

fun formatDatePickerMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
