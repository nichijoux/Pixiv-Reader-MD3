package com.pixiv.reader.app.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.download.DownloadWorkerEntryPoint
import com.pixiv.reader.core.network.download.UgoiraExportFormat
import com.pixiv.reader.core.network.download.UgoiraExportWorker
import com.pixiv.reader.core.network.illust.IllustDownloadWorker
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportWorker
import dagger.hilt.android.EntryPointAccessors

/**
 * 下载重试（app 层）：按 targetType 重建对应后台任务（下载管理页 failed 条目）。
 *
 * 重试即回「待同步」：先把索引条目复位为 pending（卡片立即反映排队态），再重建任务——
 * 任务带网络约束，断网时停留待同步，联网自动开始。
 *
 * 断点续传：已下载部分（插画 `.part` 文件 / 小说导出章节缓存）由各 Worker 自动复用，只补缺失部分。
 *
 * 注：分发逻辑依赖 core:network 的 IllustDownloadWorker 与 feature:novel 的 NovelExportWorker，
 * 受「feature 之间禁止互相依赖」硬约束无法下沉 feature:user，故落在 app 层独立文件
 * （app 依赖全部 feature，引用合法），使导航文件不承载业务逻辑。
 *
 * @param context 任意 context（WorkManager / Hilt EntryPoint 取应用级依赖）
 * @param entry 待重试的下载索引条目
 * @return 无返回值
 */
suspend fun retryDownload(context: Context, entry: DownloadEntryEntity) {
    // 复位为待同步（卡片由失败态转排队态；本地已下载部分/断点缓存保留）
    runCatching {
        val dao = EntryPointAccessors
            .fromApplication(context.applicationContext, DownloadWorkerEntryPoint::class.java)
            .downloadEntryDao()
        dao.upsert(entry.copy(status = DownloadEntryEntity.STATUS_PENDING, updatedAt = System.currentTimeMillis()))
    }
    val request = when (entry.targetType) {
        "illust" -> OneTimeWorkRequestBuilder<IllustDownloadWorker>()
            .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to entry.targetId))

        "novel" -> {
            val format = runCatching { NovelExportFormat.valueOf(entry.format) }
                .getOrDefault(NovelExportFormat.TXT)
            val data = mutableListOf<Pair<String, Any?>>()
            data += NovelExportWorker.KEY_NOVEL_ID to entry.targetId
            entry.seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
            data += NovelExportWorker.KEY_FORMAT to format.name
            OneTimeWorkRequestBuilder<NovelExportWorker>()
                .setInputData(workDataOf(*data.toTypedArray()))
        }
        "ugoira" ->
            // 动图导出重试：按索引 format 重建导出任务（zip / 已解压帧断点复用）
            OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to entry.targetId,
                        UgoiraExportWorker.KEY_FORMAT to UgoiraExportFormat.from(entry.format).format,
                    )
                )
        // 其他类型（本地文件等）无重试通道
        else -> null
    } ?: return
    WorkManager.getInstance(context).enqueue(
        request
            .setConstraints(DownloadQueue.networkConstraints())
            .addTag(DownloadQueue.workTag(entry.targetType, entry.targetId, entry.format, entry.scopeKey))
            .build(),
    )
}
