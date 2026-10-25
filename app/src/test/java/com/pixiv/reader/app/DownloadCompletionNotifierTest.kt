package com.pixiv.reader.app

import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.DownloadStatusRow
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCompletionNotifierTest {

    private fun row(id: Long, status: String, title: String = "t$id", format: String = "") =
        DownloadStatusRow(targetType = "illust", targetId = id, format = format, status = status, title = title)

    /** 按序发射状态投影快照的假 DAO（其余方法不可达）。 */
    private class FakeDao(private val snapshots: List<List<DownloadStatusRow>>) : DownloadEntryDao {
        override fun observeAll(): Flow<List<DownloadEntryEntity>> = throw UnsupportedOperationException()
        override fun observeAllStatuses(): Flow<List<DownloadStatusRow>> = flowOf(*snapshots.toTypedArray())
        override fun observeStatuses(targetType: String, targetId: Long): Flow<List<String>> =
            throw UnsupportedOperationException()
        override suspend fun getDone(): List<DownloadEntryEntity> = throw UnsupportedOperationException()
        override suspend fun get(type: String, targetId: Long, format: String, scopeKey: String): DownloadEntryEntity? =
            throw UnsupportedOperationException()
        override suspend fun upsert(entity: DownloadEntryEntity) = throw UnsupportedOperationException()
        override suspend fun updateProgress(
            type: String,
            targetId: Long,
            format: String,
            progress: Int,
            updatedAt: Long,
            scopeKey: String,
        ) = throw UnsupportedOperationException()
        override suspend fun delete(entity: DownloadEntryEntity) = throw UnsupportedOperationException()
    }

    private fun collectEvents(
        snapshots: List<List<DownloadStatusRow>>,
    ): List<UiMessage> {
        val events = mutableListOf<UiMessage>()
        runTest {
            val notifier = DownloadCompletionNotifier(FakeDao(snapshots))
            val job = launch { notifier.events.collect { events += it } }
            // 先让 events 订阅运行到挂起点，避免 scan tryEmit 时无订阅者被丢弃
            testScheduler.runCurrent()
            notifier.observe().collect { }
            // 让 events job 消费缓冲中的事件后再取消
            testScheduler.runCurrent()
            job.cancel()
        }
        return events
    }

    @Test
    fun `downloading 到 done 迁移发完成通知`() = runTest {
        val events = collectEvents(
            listOf(
                listOf(row(1, "downloading")),
                listOf(row(1, "done")),
            ),
        )
        assertEquals(1, events.size)
        assertEquals(MessageType.SUCCESS, events[0].type)
        assertTrue(events[0].args.contains("t1"))
    }

    @Test
    fun `downloading 到 failed 迁移发失败通知`() = runTest {
        val events = collectEvents(
            listOf(
                listOf(row(2, "downloading")),
                listOf(row(2, "failed")),
            ),
        )
        assertEquals(1, events.size)
        assertEquals(MessageType.ERROR, events[0].type)
    }

    @Test
    fun `首快照即终态的历史条目不通知`() = runTest {
        val events = collectEvents(
            listOf(
                listOf(row(3, "done")),
                listOf(row(3, "done")),
            ),
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `downloading 中途快照不通知`() = runTest {
        val events = collectEvents(
            listOf(
                listOf(row(4, "downloading")),
                listOf(row(4, "downloading")),
            ),
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `pending 到 done 同样通知且多格式互不干扰`() = runTest {
        val events = collectEvents(
            listOf(
                listOf(row(5, "pending"), row(5, "downloading", format = "PDF")),
                listOf(row(5, "done"), row(5, "done", format = "PDF")),
            ),
        )
        // 同 targetId 两种 format 各一条迁移
        assertEquals(2, events.size)
    }
}
