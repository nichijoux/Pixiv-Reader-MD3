package com.pixiv.reader.app

import com.pixiv.reader.app.download.DownloadNotificationSink
import com.pixiv.reader.app.download.DownloadProgressState
import com.pixiv.reader.app.download.DownloadTaskKey
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.DownloadStatusRow
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadCompletionNotifierTest {

    private fun row(
        id: Long,
        status: String,
        title: String = "t$id",
        format: String = "",
        scopeKey: String = "",
        targetType: String = "illust",
        progress: Int = 0,
    ) = DownloadStatusRow(
        targetType = targetType,
        targetId = id,
        format = format,
        status = status,
        title = title,
        scopeKey = scopeKey,
        progress = progress,
    )

    /** 按序发射状态投影快照的假 DAO（其余方法不可达）。 */
    private class FakeDao(private val snapshotsFlow: Flow<List<DownloadStatusRow>>) : DownloadEntryDao {
        constructor(snapshots: List<List<DownloadStatusRow>>) : this(flowOf(*snapshots.toTypedArray()))

        override fun observeAll(): Flow<List<DownloadEntryEntity>> = throw UnsupportedOperationException()
        override fun observeAllStatuses(): Flow<List<DownloadStatusRow>> = snapshotsFlow
        override fun observeStatuses(targetType: String, targetId: Long): Flow<List<String>> =
            throw UnsupportedOperationException()
        override fun observeStatus(
            targetType: String,
            targetId: Long,
            format: String,
            scopeKey: String,
        ): Flow<String?> = throw UnsupportedOperationException()
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

    /** 记录所有系统通知调用的假输出端口。 */
    private class FakeSink : DownloadNotificationSink {
        data class ResultCall(val key: DownloadTaskKey, val title: String, val isSuccess: Boolean)

        var channelsEnsured = 0
        val shownProgress = mutableListOf<DownloadProgressState>()
        var cancelCount = 0
        val results = mutableListOf<ResultCall>()

        override fun ensureChannels() {
            channelsEnsured++
        }

        override fun showProgress(state: DownloadProgressState) {
            shownProgress += state
        }

        override fun cancelProgress() {
            cancelCount++
        }

        override fun showResult(key: DownloadTaskKey, title: String, isSuccess: Boolean) {
            results += ResultCall(key, title, isSuccess)
        }
    }

    private fun collectForegroundEvents(
        snapshots: List<List<DownloadStatusRow>>,
        sink: FakeSink = FakeSink(),
    ): List<UiMessage> {
        val events = mutableListOf<UiMessage>()
        runTest {
            val notifier = DownloadCompletionNotifier(
                downloadEntryDao = FakeDao(snapshots),
                notificationSink = sink,
                nowMs = { testScheduler.currentTime },
            )
            // 先让 events 订阅运行到挂起点，确保前台 subscriptionCount > 0
            val job = launch { notifier.events.collect { events += it } }
            runCurrent()
            notifier.start(backgroundScope)
            runCurrent()
            job.cancel()
        }
        return events
    }

    @Test
    fun `downloading 到 done 迁移发完成通知且前台不重发系统结果`() = runTest {
        val sink = FakeSink()
        val events = collectForegroundEvents(
            listOf(
                listOf(row(1, "downloading")),
                listOf(row(1, "done")),
            ),
            sink = sink,
        )
        assertEquals(1, events.size)
        assertEquals(MessageType.SUCCESS, events[0].type)
        assertTrue(events[0].args.contains("t1"))
        assertTrue(sink.results.isEmpty())
    }

    @Test
    fun `downloading 到 failed 迁移发失败通知`() = runTest {
        val events = collectForegroundEvents(
            listOf(
                listOf(row(2, "downloading")),
                listOf(row(2, "failed")),
            ),
        )
        assertEquals(1, events.size)
        assertEquals(MessageType.ERROR, events[0].type)
    }

    @Test
    fun `首快照即终态的历史条目不通知也不展示进度`() = runTest {
        val sink = FakeSink()
        val events = collectForegroundEvents(
            listOf(
                listOf(row(3, "done", progress = 100), row(4, "failed", progress = 30)),
                listOf(row(3, "done", progress = 100), row(4, "failed", progress = 30)),
            ),
            sink = sink,
        )
        assertTrue(events.isEmpty())
        assertTrue(sink.shownProgress.isEmpty())
        assertTrue(sink.results.isEmpty())
        assertEquals(0, sink.cancelCount)
    }

    @Test
    fun `downloading 中途快照不发终态通知`() = runTest {
        val sink = FakeSink()
        val events = collectForegroundEvents(
            listOf(
                listOf(row(4, "downloading", progress = 10)),
                listOf(row(4, "downloading", progress = 60)),
            ),
            sink = sink,
        )
        assertTrue(events.isEmpty())
        assertTrue(sink.results.isEmpty())
    }

    @Test
    fun `pending 到 done 通知且同作品多格式与作用域互不干扰`() = runTest {
        val events = collectForegroundEvents(
            listOf(
                listOf(
                    row(5, "pending", targetType = "novel", format = "TXT", scopeKey = ""),
                    row(5, "downloading", targetType = "novel", format = "EPUB", scopeKey = "series", progress = 50),
                ),
                listOf(
                    row(5, "done", targetType = "novel", format = "TXT", scopeKey = "", progress = 100),
                    row(5, "done", targetType = "novel", format = "EPUB", scopeKey = "series", progress = 100),
                ),
            ),
        )
        assertEquals(2, events.size)
    }

    @Test
    fun `后台无前台订阅时终态转为系统结果通知`() = runTest {
        val flow = MutableSharedFlow<List<DownloadStatusRow>>()
        val sink = FakeSink()
        val notifier = DownloadCompletionNotifier(
            downloadEntryDao = FakeDao(flow),
            notificationSink = sink,
            nowMs = { testScheduler.currentTime },
        )
        notifier.start(backgroundScope)
        runCurrent()

        assertEquals(1, sink.channelsEnsured)
        flow.emit(listOf(row(10, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 20, title = "BgWork")))
        assertTrue(notifier.hasActiveDownloads.value)

        flow.emit(listOf(row(10, DownloadEntryEntity.STATUS_DONE, progress = 100, title = "BgWork")))
        assertFalse(notifier.hasActiveDownloads.value)
        assertEquals(1, sink.cancelCount)
        assertEquals(1, sink.results.size)
        assertEquals("BgWork", sink.results[0].title)
        assertTrue(sink.results[0].isSuccess)
    }

    @Test
    fun `进度按节流窗口更新并在窗口结束后尾随刷新`() = runTest {
        val flow = MutableSharedFlow<List<DownloadStatusRow>>()
        val sink = FakeSink()
        val notifier = DownloadCompletionNotifier(
            downloadEntryDao = FakeDao(flow),
            notificationSink = sink,
            throttleMs = 500L,
            nowMs = { testScheduler.currentTime },
        )
        notifier.start(backgroundScope)
        runCurrent()

        // 1. pending 入队 -> 立即显示不确定进度
        flow.emit(listOf(row(20, DownloadEntryEntity.STATUS_PENDING, progress = 0, title = "Art 20")))
        assertEquals(1, sink.shownProgress.size)
        assertTrue(sink.shownProgress[0].indeterminate)

        // 2. 50ms 后切为 downloading -> 形态改变立即更新
        advanceTimeBy(50)
        flow.emit(listOf(row(20, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 10, title = "Art 20")))
        assertEquals(2, sink.shownProgress.size)
        assertEquals(10, sink.shownProgress[1].progress)
        assertFalse(sink.shownProgress[1].indeterminate)

        // 3. 150ms 后纯进度更新 -> 节流抑制，不立即发送
        advanceTimeBy(150)
        flow.emit(listOf(row(20, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 35, title = "Art 20")))
        assertEquals(2, sink.shownProgress.size)

        // 4. 满 500ms 尾随刷新触发 -> 补发 35%
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3, sink.shownProgress.size)
        assertEquals(35, sink.shownProgress[2].progress)

        // 5. 超出节流窗口后再更新 -> 立即发送
        advanceTimeBy(600)
        flow.emit(listOf(row(20, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 80, title = "Art 20")))
        assertEquals(4, sink.shownProgress.size)
        assertEquals(80, sink.shownProgress[3].progress)
    }

    @Test
    fun `多任务聚合计算平均进度且单任务失败时其余任务继续显示`() = runTest {
        val flow = MutableSharedFlow<List<DownloadStatusRow>>()
        val sink = FakeSink()
        val notifier = DownloadCompletionNotifier(
            downloadEntryDao = FakeDao(flow),
            notificationSink = sink,
            throttleMs = 500L,
            nowMs = { testScheduler.currentTime },
        )
        notifier.start(backgroundScope)
        runCurrent()

        flow.emit(listOf(row(1, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 60, title = "First")))
        assertEquals(60, sink.shownProgress.last().progress)

        // 100ms 后增加第二个排队任务：活跃数变化立即刷新，平均进度 (60 + 0) / 2 = 30
        advanceTimeBy(100)
        flow.emit(
            listOf(
                row(1, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 60, title = "First"),
                row(2, DownloadEntryEntity.STATUS_PENDING, progress = 0, title = "Second"),
            ),
        )
        val combined = sink.shownProgress.last()
        assertEquals(2, combined.activeCount)
        assertEquals(1, combined.downloadingCount)
        assertEquals(1, combined.pendingCount)
        assertEquals(30, combined.progress)

        // 任务 1 失败，任务 2 开始下载 40%：撤销计数不变为 0，改为展示任务 2 且发出任务 1 失败通知
        advanceTimeBy(50)
        flow.emit(
            listOf(
                row(1, DownloadEntryEntity.STATUS_FAILED, progress = 60, title = "First"),
                row(2, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 40, title = "Second"),
            ),
        )
        assertEquals(0, sink.cancelCount)
        assertEquals("Second", sink.shownProgress.last().primaryTitle)
        assertEquals(40, sink.shownProgress.last().progress)
        assertEquals(1, sink.results.size)
        assertFalse(sink.results[0].isSuccess)
    }

    @Test
    fun `任务取消删除后立即撤销通知且待刷新尾随任务不复活`() = runTest {
        val flow = MutableSharedFlow<List<DownloadStatusRow>>()
        val sink = FakeSink()
        val notifier = DownloadCompletionNotifier(
            downloadEntryDao = FakeDao(flow),
            notificationSink = sink,
            throttleMs = 500L,
            nowMs = { testScheduler.currentTime },
        )
        notifier.start(backgroundScope)
        runCurrent()

        flow.emit(listOf(row(30, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 20, title = "CancelArt")))
        advanceTimeBy(100)
        flow.emit(listOf(row(30, DownloadEntryEntity.STATUS_DOWNLOADING, progress = 30, title = "CancelArt")))
        assertEquals(1, sink.shownProgress.size)

        // 在尾随刷新触发前删除条目
        advanceTimeBy(100)
        flow.emit(emptyList())
        assertEquals(1, sink.cancelCount)
        assertFalse(notifier.hasActiveDownloads.value)
        assertTrue(sink.results.isEmpty())

        // 推进超过节流时间：已取消的任务不得再次触发 showProgress
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, sink.shownProgress.size)
    }
}
