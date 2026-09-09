package com.pixiv.reader.core.network.download

import androidx.work.Constraints
import androidx.work.NetworkType
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity

/**
 * 下载队列助手：统一「待同步」生命周期的基础设施。
 *
 * 下载生命周期：待同步（入队即建索引条目）→ 下载中（Worker 起跑覆写）→ 下载完成 / 失败。
 * Worker 一律带网络约束（断网时任务挂起、条目停留待同步，联网自动开始）；
 * 任务按条目复合主键打 tag，删除未完成条目时可精确取消后台任务（防复活重建）。
 */
object DownloadQueue {

    /** WorkManager 任务 tag 前缀（tag 按条目复合主键拼接，入队与删除取消两端共用）。 */
    private const val TAG_PREFIX = "pixiv_download"

    /**
     * 下载任务的网络约束：断网时 Worker 挂起（条目停留「待同步」），联网自动开始。
     *
     * @return 网络连通约束
     */
    fun networkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * 下载任务 tag：按条目复合主键（类型/id/格式/范围）拼出，入队与删除取消两端共用。
     *
     * @param targetType 目标类型（illust / ugoira / novel）
     * @param targetId 目标 id
     * @param format 导出格式（插画空串 / MP4、ZIP / TXT 等）
     * @param scopeKey 下载范围键（单本 "" / series / partial）
     * @return tag 字符串
     */
    fun workTag(targetType: String, targetId: Long, format: String, scopeKey: String): String =
        "$TAG_PREFIX:$targetType:$targetId:$format:$scopeKey"

    /**
     * 入队即建「待同步」索引条目：下载管理卡片立即可见，断网期间停留待同步，
     * Worker 起跑时覆写为 downloading（快照由 Worker 取到详情后补全）。
     *
     * @param dao 下载索引 DAO
     * @param entry 待入队的索引条目（status 会被强制置为 pending）
     * @return 无返回值
     */
    suspend fun markPending(dao: DownloadEntryDao, entry: DownloadEntryEntity) {
        dao.upsert(
            entry.copy(
                status = DownloadEntryEntity.STATUS_PENDING,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
