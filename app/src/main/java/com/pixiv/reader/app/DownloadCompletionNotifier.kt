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
 * 全局下载通知协调器：按 `events.subscriptionCount` 自动分流前后台终态提醒避免双响；
 * 首次快照仅建对比基线防止冷启动重复提示；进度更新经 500ms 节流与尾随刷新防系统丢弃通知。
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
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    private val _hasActiveDownloads = MutableStateFlow(false)
    val hasActiveDownloads: StateFlow<Boolean> = _hasActiveDownloads.asStateFlow()

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

    /** 刚授予 POST_NOTIFICATIONS 权限后补发当前活跃进度，使通知立即上栏。 */
    fun refreshNotification() {
        val scope = notifierScope ?: return
        scope.launch {
            mutex.withLock {
                lastShownState?.let { notificationSink.showProgress(it) }
            }
        }
    }

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

    private fun buildProgressState(rows: List<DownloadStatusRow>): DownloadProgressState? {
        val active = rows.filter {
            it.status == DownloadEntryEntity.STATUS_DOWNLOADING ||
                it.status == DownloadEntryEntity.STATUS_PENDING
        }
        if (active.isEmpty()) return null

        val downloading = active.filter { it.status == DownloadEntryEntity.STATUS_DOWNLOADING }
        val pendingCount = active.size - downloading.size
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
        const val DEFAULT_THROTTLE_MS = 500L
    }
}
