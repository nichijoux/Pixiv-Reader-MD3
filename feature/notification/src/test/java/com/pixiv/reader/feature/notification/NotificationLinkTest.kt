package com.pixiv.reader.feature.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLinkTest {

    @Test
    fun `null 与空串返回 Unknown`() {
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget(null))
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget(""))
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("   "))
    }

    @Test
    fun `非 pixiv scheme 返回 Unknown`() {
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("https://users/123"))
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("pixiv://"))
    }

    @Test
    fun `官方复数形式可解析`() {
        assertEquals(NotificationTarget.User(123L), parseNotificationTarget("pixiv://users/123"))
        assertEquals(NotificationTarget.Illust(456L), parseNotificationTarget("pixiv://illusts/456"))
        assertEquals(NotificationTarget.Novel(789L), parseNotificationTarget("pixiv://novels/789"))
    }

    @Test
    fun `应用内单数形式可解析`() {
        assertEquals(NotificationTarget.User(1L), parseNotificationTarget("pixiv://user/1"))
        assertEquals(NotificationTarget.Illust(2L), parseNotificationTarget("pixiv://illust/2"))
        assertEquals(NotificationTarget.Novel(3L), parseNotificationTarget("pixiv://novel/3"))
    }

    @Test
    fun `带查询参数可解析`() {
        assertEquals(
            NotificationTarget.Illust(42L),
            parseNotificationTarget("pixiv://illusts/42?title=abc"),
        )
    }

    @Test
    fun `未知类型与非法 id 返回 Unknown`() {
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("pixiv://stamps/3"))
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("pixiv://users/abc"))
        assertEquals(NotificationTarget.Unknown, parseNotificationTarget("pixiv://users"))
    }
}
