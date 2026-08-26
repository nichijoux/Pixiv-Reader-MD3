package com.pixiv.reader.feature.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeTimeTest {
    /** 固定"当前时间"：2026-08-26T12:00:00+09:00（自解析，避免手算 epoch 出错） */
    private val now: Long =
        java.time.OffsetDateTime.parse("2026-08-26T12:00:00+09:00").toInstant().toEpochMilli()

    @Test
    fun `null 与非法格式返回 null`() {
        assertNull(relativeTime(null, now))
        assertNull(relativeTime("not-a-time", now))
        assertNull(relativeTime("2026/08/26 12:00", now))
    }

    @Test
    fun `未来时间返回 null`() {
        assertNull(relativeTime("2026-08-26T12:01:00+09:00", now))
    }

    @Test
    fun `一分钟内显示刚刚`() {
        val t = relativeTime("2026-08-26T11:59:30+09:00", now)
        assertEquals(RelativeTime.Res(R.string.notification_time_just_now), t)
    }

    @Test
    fun `小时内显示分钟前`() {
        val t = relativeTime("2026-08-26T11:30:00+09:00", now)
        assertEquals(
            RelativeTime.Res(R.string.notification_time_minutes_ago, 30L),
            t,
        )
    }

    @Test
    fun `天内显示小时前`() {
        val t = relativeTime("2026-08-26T07:00:00+09:00", now)
        assertEquals(
            RelativeTime.Res(R.string.notification_time_hours_ago, 5L),
            t,
        )
    }

    @Test
    fun `周内显示天前`() {
        val t = relativeTime("2026-08-24T12:00:00+09:00", now)
        assertEquals(
            RelativeTime.Res(R.string.notification_time_days_ago, 2L),
            t,
        )
    }

    @Test
    fun `超过一周返回日期文本`() {
        // 日期按本地时区格式化，断言类型即可（具体文本随测试机时区变化）
        val t = relativeTime("2026-08-01T12:00:00+09:00", now)
        assert(t is RelativeTime.Date)
    }

    @Test
    fun `UTC 尾缀 Z 可解析`() {
        // now = 03:00Z；00:30Z 即 09:30+09:00，相差 2.5 小时 → 2 小时前
        val t = relativeTime("2026-08-26T00:30:00Z", now)
        assertEquals(
            RelativeTime.Res(R.string.notification_time_hours_ago, 2L),
            t,
        )
    }
}
