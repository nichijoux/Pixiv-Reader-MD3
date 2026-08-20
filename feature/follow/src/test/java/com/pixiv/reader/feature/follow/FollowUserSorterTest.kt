package com.pixiv.reader.feature.follow

import com.pixiv.api.model.Illust
import com.pixiv.api.model.User
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.feature.follow.data.FollowUserSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUserSorterTest {

    private fun preview(id: Long, name: String?, latestDate: String?) = UserPreview(
        user = if (name != null) User(id = id, name = name) else null,
        illusts = latestDate?.let { listOf(Illust(id = id * 100, create_date = it)) } ?: emptyList(),
    )

    @Test
    fun `FOLLOW_TIME 保持 API 顺序`() {
        val users = listOf(preview(3, "C", "2026-08-10T00:00:00+09:00"), preview(1, "A", "2026-08-12T00:00:00+09:00"))
        assertEquals(listOf(3L, 1L), FollowUserSorter.sort(users, FollowSortMode.FOLLOW_TIME).map { it.user?.id })
    }

    @Test
    fun `NAME_ASC 按名称升序且空白名排最后`() {
        val users = listOf(
            preview(3, "C", null),
            preview(1, "A", null),
            preview(2, "B", null),
            preview(4, "", null),
            preview(5, null, null),
        )
        val sorted = FollowUserSorter.sort(users, FollowSortMode.NAME_ASC)
        assertEquals(listOf("A", "B", "C", "", ""), sorted.map { it.user?.name ?: "" })
    }

    @Test
    fun `NAME_DESC 按名称降序且空白名排最后`() {
        val users = listOf(
            preview(1, "A", null),
            preview(3, "C", null),
            preview(4, "", null),
            preview(2, "B", null),
        )
        val sorted = FollowUserSorter.sort(users, FollowSortMode.NAME_DESC)
        assertEquals(listOf("C", "B", "A", ""), sorted.map { it.user?.name ?: "" })
    }

    @Test
    fun `LATEST_WORK 按代表作发布时间倒序且无代表作排最后`() {
        val users = listOf(
            preview(2, "B", "2026-08-10T00:00:00+09:00"),
            preview(1, "A", "2026-08-12T00:00:00+09:00"),
            preview(3, "C", null),
        )
        val sorted = FollowUserSorter.sort(users, FollowSortMode.LATEST_WORK)
        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.user?.id })
    }

    @Test
    fun `NAME_ASC 中文按拼音排序`() {
        val users = listOf(
            preview(3, "陈晨", null),
            preview(1, "阿酱", null),
            preview(2, "白桃", null),
        )
        val sorted = FollowUserSorter.sort(users, FollowSortMode.NAME_ASC)
        assertEquals(listOf("阿酱", "白桃", "陈晨"), sorted.map { it.user?.name })
    }

    @Test
    fun `NAME_ASC 忽略大小写混合排序`() {
        val users = listOf(
            preview(1, "Banana", null),
            preview(2, "apple", null),
            preview(3, "Cherry", null),
        )
        val sorted = FollowUserSorter.sort(users, FollowSortMode.NAME_ASC)
        assertEquals(listOf("apple", "Banana", "Cherry"), sorted.map { it.user?.name })
    }

    @Test
    fun `空输入返回空`() {
        assertEquals(emptyList<UserPreview>(), FollowUserSorter.sort(emptyList(), FollowSortMode.NAME_ASC))
    }
}
