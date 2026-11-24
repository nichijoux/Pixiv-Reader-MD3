package com.pixiv.reader.core.ui.component.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHostStateTest {

    @Test
    fun `新通知插入栈顶且最多保留 MAX_VISIBLE 条`() {
        val state = NotificationHostState()

        state.show("one")
        state.show("two")
        state.show("three")
        assertEquals(listOf("three", "two", "one"), state.items.value.map { it.text })

        state.show("four")
        assertEquals(listOf("four", "three", "two"), state.items.value.map { it.text })
    }

    @Test
    fun `相同文案类型与动作的活跃通知合并置顶并刷新 id`() {
        val state = NotificationHostState()

        state.show("dup", type = NotificationType.Info, actionText = "Open")
        val firstId = state.items.value.single().id
        state.show("other", type = NotificationType.Success)

        state.show("dup", type = NotificationType.Info, actionText = "Open")
        val items = state.items.value
        assertEquals(listOf("dup", "other"), items.map { it.text })
        assertNotEquals(firstId, items.first().id)

        state.show("dup", type = NotificationType.Error, actionText = "Open")
        assertEquals(3, state.items.value.size)
        assertEquals(NotificationType.Error, state.items.value.first().type)
    }

    @Test
    fun `按 id 关闭仅移除目标通知并保留其余栈条目`() {
        val state = NotificationHostState()

        state.show("first")
        state.show("second")
        state.show("third")

        val secondId = state.items.value[1].id
        state.dismiss(secondId)
        assertEquals(listOf("third", "first"), state.items.value.map { it.text })

        state.dismiss(-999L)
        assertEquals(listOf("third", "first"), state.items.value.map { it.text })
    }

    @Test
    fun `无参 dismiss 清空全部活跃通知`() {
        val state = NotificationHostState()

        state.show("a")
        state.show("b")
        assertEquals(2, state.items.value.size)

        state.dismiss()
        assertTrue(state.items.value.isEmpty())
    }
}
