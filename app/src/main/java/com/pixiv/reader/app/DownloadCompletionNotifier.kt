package com.pixiv.reader.app

import com.pixiv.reader.app.download.DownloadNotificationManager
import com.pixiv.reader.app.download.DownloadNotificationSink
import com.pixiv.reader.app.download.DownloadProgressState
import com.pixiv.reader.app.download.DownloadTaskKey
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.DownloadStatusRow
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 全局下载通知协调器（app 层进程单例，在 [PixivApp.onCreate] 启动）。
 *
 * 职责：
 * 1. 单次订阅 [DownloadEntryDao.observeAllStatuses] 轻量投影（不含 `payloadJson` 等大列），
 *    同时驱动系统实时进度通知（含 Android 16 Live Updates）与完成/失败提醒；
 * 2. 进度通知在前后台均持续聚合更新（500ms 节流 + 尾随刷新），离开页面或切后台均不中断；
 * 3. 终态通知按 `_events.subscriptionCount` 自动分流：应用在前台（`MainActivity` `STARTED` 订阅中）
 *    发往应用内 [events]，退至后台无订阅时由 [DownloadNotificationSink.showResult] 发系统通知，避免前后台双响；
 * 4. 首次快照仅建基线，防止冷启动重复提示历史下载。
 *
 * @param downloadEntryDao 下载索引 DAO
 * @param notificationSink 系统通知输出端口
 * @param throttleMs 进度纯数值变化的最小通知间隔（毫秒）
 * @param nowMs 当前时间戳提供器（便于单测注入虚拟时钟）
 */
@Singleton
class DownloadCompletionNotifier internal constructor(
    private val downloadEntryDao: DownloadEntryDao,
    private val notificationSink: DownloadNotificationSink,
    private val throttleMs: Long = DEFAULT_THROTTLE_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    @Inject
    constructor(
        downloadEntryDao: DownloadEntryDao,
        notificationManager: DownloadNotificationManager,
    ) : this(
        downloadEntryDao = downloadEntryDao,
        notificationSink = notificationManager,
        throttleMs = DEFAULT_THROTTLE_MS,
        nowMs = System::currentTimeMillis,
    )

    private val started = AtomicBoolean(false)
    private val mutex = Mutex()
    private var notifierScope: CoroutineScope? = null
    private var trailingFlushJob: Job? = null

    private var initialized = false
    private var prevStatuses: Map<DownloadTaskKey, String> = emptyMap()
    private var lastShownState: DownloadProgressState? = null
    private var lastShownAtMs: Long = 0L

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

    /** 前台应用内完成/失败提示流（由 `MainActivity` 在 `STARTED` 状态订阅）。 */
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    private val _hasActiveDownloads = MutableStateFlow(false)

    /** 当前是否存在活跃下载任务（供首次触发下载时请求 `POST_NOTIFICATIONS` 运行时权限）。 */
    val hasActiveDownloads: StateFlow<Boolean> = _hasActiveDownloads.asStateFlow()

    /**
     * 在进程级作用域内启动下载状态观察与通知分发（幂等，重复调用无效）。
     *
     * @param scope 进程级协程作用域
     * @return 无返回值
     */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        notifierScope = scope
        notificationSink.ensureChannels()
        scope.launch {
            downloadEntryDao.observeAllStatuses().collect { rows ->
                handleSnapshot(rows, scope)
            }
        }
    }

    /**
     * 立即补发一次当前活跃进度的系统通知（供刚授予 `POST_NOTIFICATIONS` 权限后立即上栏/上岛）。
     *
     * @return 无返回值
     */
    fun refreshNotification() {
        val scope = notifierScope ?: return
        scope.launch {
            mutex.withLock {
                lastShownState?.let { notificationSink.showProgress(it) }
            }
        }
    }

    /**
     * 消费一次全表状态快照：分发终态事件并聚合节流更新系统进度通知。
     *
     * @param rows 当前数据库状态投影列表
     * @param scope 调度节流尾随刷新的协程作用域（为空时不调度延迟刷新）
     * @return 无返回值
     */
    internal suspend fun handleSnapshot(
        rows: List<DownloadStatusRow>,
        scope: CoroutineScope? = null,
    ) {
        mutex.withLock {
            // 首快照仅建立终态对比基线，避免冷启动对历史已完成/失败条目重复提醒
            if (initialized) {
                rows.forEach { row ->
                    val key = DownloadTaskKey(row)
                    val before = prevStatuses[key]
                    if (before != null && !isTerminal(before) && isTerminal(row.status)) {
                        val isSuccess = row.status == DownloadEntryEntity.STATUS_DONE
                        if (_events.subscriptionCount.value > 0) {
                            _events.tryEmit(
                                UiMessage(
                                    if (isSuccess) R.string.download_complete else R.string.download_failed,
                                    listOf(row.title.orEmpty()),
                                    type = if (isSuccess) MessageType.SUCCESS else MessageType.ERROR,
                                ),
                            )
                        } else {
                            notificationSink.showResult(key, row.title.orEmpty(), isSuccess)
                        }
                    }
                }
            }
            initialized = true
            prevStatuses = rows.associate { DownloadTaskKey(it) to it.status }

            val activeState = buildProgressState(rows)
            _hasActiveDownloads.value = activeState != null
            if (activeState == null) {
                trailingFlushJob?.cancel()
                trailingFlushJob = null
                if (lastShownState != null) {
                    lastShownState = null
                    notificationSink.cancelProgress()
                }
                return@withLock
            }

            val prevShown = lastShownState
            if (prevShown == activeState) return@withLock

            val now = nowMs()
            // 任务数/形态/主标题变更、满百收敛或超出节流间隔时立即刷新，防止频繁 notify 被系统丢弃
            val shouldEmitImmediately = prevShown == null ||
                activeState.activeCount != prevShown.activeCount ||
                activeState.indeterminate != prevShown.indeterminate ||
                activeState.primaryTitle != prevShown.primaryTitle ||
                (activeState.progress == 100 && prevShown.progress != 100) ||
                (now - lastShownAtMs >= throttleMs)

            if (shouldEmitImmediately) {
                trailingFlushJob?.cancel()
                trailingFlushJob = null
                lastShownState = activeState
                lastShownAtMs = now
                notificationSink.showProgress(activeState)
            } else if (scope != null) {
                // 节流窗口内的纯进度变更调度一次尾随刷新，确保最终进度得以展现
                trailingFlushJob?.cancel()
                trailingFlushJob = scope.launch {
                    delay(throttleMs)
                    mutex.withLock {
                        if (!isActive) return@withLock
                        trailingFlushJob = null
                        val currentShown = lastShownState ?: return@withLock
                        if (activeState != currentShown) {
                            lastShownState = activeState
                            lastShownAtMs = nowMs()
                            notificationSink.showProgress(activeState)
                        }
                    }
                }
            }
        }
    }

    /** 将活跃的 `downloading` / `pending` 条目聚合为单一通知进度状态；无活跃条目返回 null。 */
    private fun buildProgressState(rows: List<DownloadStatusRow>): DownloadProgressState? {
        val active = rows.filter {
            it.status == DownloadEntryEntity.STATUS_DOWNLOADING ||
                it.status == DownloadEntryEntity.STATUS_PENDING
        }
        if (active.isEmpty()) return null

        val downloading = active.filter { it.status == DownloadEntryEntity.STATUS_DOWNLOADING }
        val pendingCount = active.size - downloading.size
        // 优先展示正在下载中任务标题，全排队时回退至首个活跃任务标题
        val primaryTitle = downloading.firstOrNull { !it.title.isNullOrBlank() }?.title
            ?: active.firstOrNull { !it.title.isNullOrBlank() }?.title
            ?: ""
        val indeterminate = downloading.isEmpty()
        val progress = if (indeterminate) {
            0
        } else {
            active.sumOf { row ->
                if (row.status == DownloadEntryEntity.STATUS_DOWNLOADING) {
                    row.progress.coerceIn(0, 100)
                } else {
                    0
                }
            } / active.size
        }

        return DownloadProgressState(
            activeCount = active.size,
            downloadingCount = downloading.size,
            pendingCount = pendingCount,
            primaryTitle = primaryTitle,
            progress = progress,
            indeterminate = indeterminate,
        )
    }

    private fun isTerminal(status: String): Boolean =
        status == DownloadEntryEntity.STATUS_DONE || status == DownloadEntryEntity.STATUS_FAILED

    companion object {
        /** 进度纯数字更新的默认节流间隔（毫秒）。 */
        const val DEFAULT_THROTTLE_MS = 500L
    }
}
