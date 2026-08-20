package com.pixiv.reader.app

import com.pixiv.reader.app.R
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * 全局下载完成通知（app 层）：观察下载索引状态迁移 `downloading/pending → done/failed`，
 * 经 [events] 发出应用内通知。
 *
 * 背景：此前完成通知依赖详情页/查看器 VM 存活监听（`observeDownloadCompletion` 等），
 * 用户下载后立即离开页面即收不到。本观察者在 MainActivity 组合期间常驻，
 * 下载完成无论停留在哪个页面都能收到提示。
 *
 * 语义：仅**状态迁移**发生时通知（`prev[key]` 存在且非终态 → 变为 done/failed）；
 * 首次订阅快照中已完成的条目不通知（避免每次冷启动重复提示历史下载）。
 */
@Singleton
class DownloadCompletionNotifier @Inject constructor(
    private val downloadEntryDao: DownloadEntryDao,
) {

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    /** 订阅下载索引变化；collect 本流以驱动状态扫描（事件经 [events] 发出）。 */
    fun observe(): Flow<Unit> = downloadEntryDao.observeAll()
        .scan(emptyMap<Key, String>()) { prev, entries ->
            val current = entries.associate { Key(it) to it.status }
            entries.forEach { e ->
                val key = Key(e)
                val before = prev[key]
                // 仅在"非终态 → 终态"迁移时通知；未在上一快照中的条目不通知（历史完成/新增中）
                if (before != null && before != "done" && before != "failed") {
                    when (e.status) {
                        "done" -> _events.tryEmit(
                            UiMessage(R.string.download_complete, listOf(e.title.orEmpty()), type = MessageType.SUCCESS),
                        )
                        "failed" -> _events.tryEmit(
                            UiMessage(R.string.download_failed, listOf(e.title.orEmpty()), type = MessageType.ERROR),
                        )
                    }
                }
            }
            current
        }
        .map { }

    private data class Key(val targetType: String, val targetId: Long, val format: String) {
        constructor(e: DownloadEntryEntity) : this(e.targetType, e.targetId, e.format)
    }
}
