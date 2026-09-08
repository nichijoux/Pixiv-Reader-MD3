package com.pixiv.reader.core.network.feed

import com.google.gson.reflect.TypeToken
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.dao.FeedSnapshotDao
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 信息流快照存取 round-trip 单测（内存 fake DAO；Gson 序列化 / 损坏自愈）。 */
class FeedSnapshotStoreTest {

    /** 内存版 FeedSnapshotDao（单 key 覆盖语义与 Room REPLACE 一致）。 */
    private class FakeDao : FeedSnapshotDao {
        val rows = mutableMapOf<String, FeedSnapshotEntity>()
        override suspend fun get(feedKey: String): FeedSnapshotEntity? = rows[feedKey]
        override suspend fun upsert(entity: FeedSnapshotEntity) {
            rows[entity.feedKey] = entity
        }

        override suspend fun delete(feedKey: String) {
            rows.remove(feedKey)
        }

        override suspend fun clearAll() = rows.clear()
    }

    private fun store() = FeedSnapshotStore(FakeDao())

    @Test
    fun `Illust 列表保存后可恢复`() = runTest {
        val s = store()
        val illusts = listOf(
            Illust(id = 1L, title = "one", width = 800, height = 600),
            Illust(id = 2L, title = "two", width = 1200, height = 900),
        )
        val type = object : TypeToken<List<Illust>>() {}.type
        s.save(FeedSnapshotStore.KEY_HOME_RECOMMEND, illusts, type, nextUrl = "next://1")
        val restored = s.restore<Illust>(FeedSnapshotStore.KEY_HOME_RECOMMEND, type)
        assertTrue(restored != null)
        assertEquals(2, restored!!.first.size)
        assertEquals(1L, restored.first[0].id)
        assertEquals("one", restored.first[0].title)
        assertEquals("next://1", restored.second)
    }

    @Test
    fun `损坏 JSON 删行自愈返回 null`() = runTest {
        val dao = FakeDao()
        dao.upsert(FeedSnapshotEntity(feedKey = FeedSnapshotStore.KEY_HOME_RECOMMEND, payloadJson = "{broken"))
        val s = FeedSnapshotStore(dao)
        val type = object : TypeToken<List<Illust>>() {}.type
        assertNull(s.restore<Illust>(FeedSnapshotStore.KEY_HOME_RECOMMEND, type))
        assertTrue("损坏快照应被删除", dao.rows.isEmpty())
    }

    @Test
    fun `空列表不保存`() = runTest {
        val dao = FakeDao()
        val s = FeedSnapshotStore(dao)
        val type = object : TypeToken<List<Illust>>() {}.type
        s.save(FeedSnapshotStore.KEY_HOME_RECOMMEND, emptyList<Illust>(), type)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `同 key 重复保存覆盖旧快照`() = runTest {
        val dao = FakeDao()
        val s = FeedSnapshotStore(dao)
        val type = object : TypeToken<List<Illust>>() {}.type
        s.save(FeedSnapshotStore.KEY_HOME_RECOMMEND, listOf(Illust(id = 1L)), type)
        s.save(FeedSnapshotStore.KEY_HOME_RECOMMEND, listOf(Illust(id = 2L)), type)
        val restored = s.restore<Illust>(FeedSnapshotStore.KEY_HOME_RECOMMEND, type)!!
        assertEquals(2L, restored.first.single().id)
    }

    @Test
    fun `clearAll 清空全部快照`() = runTest {
        val dao = FakeDao()
        val s = FeedSnapshotStore(dao)
        val type = object : TypeToken<List<Illust>>() {}.type
        s.save("k1", listOf(Illust(id = 1L)), type)
        s.save("k2", listOf(Illust(id = 2L)), type)
        s.clearAll()
        assertTrue(dao.rows.isEmpty())
    }
}
