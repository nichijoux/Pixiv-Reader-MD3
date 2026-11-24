package com.pixiv.reader.core.network.download

import androidx.work.Constraints
import androidx.work.NetworkType
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity

/**
 * 下载队列助手：入队建 pending 条目，Worker 按网络约束在联网时起跑并覆写状态；
 * 任务 tag 按条目复合主键拼接，删除未完成条目时可精确取消后台任务防复活。
 */
object DownloadQueue {

    private const val TAG_PREFIX = "pixiv_download"

    fun networkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun workTag(targetType: String, targetId: Long, format: String, scopeKey: String): String =
        "$TAG_PREFIX:$targetType:$targetId:$format:$scopeKey"

    suspend fun markPending(dao: DownloadEntryDao, entry: DownloadEntryEntity) {
        dao.upsert(
            entry.copy(
                status = DownloadEntryEntity.STATUS_PENDING,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
