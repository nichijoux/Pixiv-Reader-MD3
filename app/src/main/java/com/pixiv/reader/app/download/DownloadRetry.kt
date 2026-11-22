package com.pixiv.reader.app.download

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
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
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * 下载重试（app 层）：按 targetType 重建对应后台任务（下载管理页 failed 条目）。
 *
 * 按原范围重建任务后将索引条目复位为 pending，再入队等待网络连通。
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
    // 先校验能否按原主键重建任务；缺少分册选择的旧条目保持失败态。
    val request = when (entry.targetType) {
        "illust" -> OneTimeWorkRequestBuilder<IllustDownloadWorker>()
            .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to entry.targetId))

        "novel" -> {
            val format = NovelExportFormat.entries.firstOrNull { it.name == entry.format } ?: return
            val seriesId = when (entry.scopeKey) {
                "" -> null
                "series", "partial" -> entry.seriesId?.takeIf { it > 0L } ?: return
                else -> return
            }
            val data = mutableListOf<Pair<String, Any?>>()
            data += NovelExportWorker.KEY_NOVEL_ID to entry.targetId
            seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
            data += NovelExportWorker.KEY_FORMAT to format.name
            if (entry.scopeKey == "partial") {
                val chapterIds = retryChapterIds(entry.payloadJson) ?: return
                data += NovelExportWorker.KEY_CHAPTER_IDS to chapterIds
            }
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
    val workTag = DownloadQueue.workTag(entry.targetType, entry.targetId, entry.format, entry.scopeKey)
    val work = request
        .setConstraints(DownloadQueue.networkConstraints())
        .addTag(workTag)
        .build()
    try {
        val dao = EntryPointAccessors
            .fromApplication(context.applicationContext, DownloadWorkerEntryPoint::class.java)
            .downloadEntryDao()
        // 保留原条目的复合主键、快照与断点，校验通过后以唯一队列入队防止重复并发任务。
        DownloadQueue.markPending(dao, entry)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workTag, ExistingWorkPolicy.KEEP, work)
            .await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("DownloadRetry", "无法重试 ${entry.targetType}/${entry.targetId}", e)
    }
}

/**
 * 从下载快照的 `chapterIds` 数组读取完整分册选择；缺失或损坏时保留原失败记录。
 *
 * @param payloadJson 下载条目的快照 JSON
 * @return 有效的分册 ID 数组；无法恢复选中范围时为空
 */
private fun retryChapterIds(payloadJson: String?): LongArray? {
    val payload = payloadJson ?: return null
    val ids = runCatching { JSONObject(payload).optJSONArray(NovelExportWorker.KEY_CHAPTER_IDS) }
        .getOrNull() ?: return null
    if (ids.length() == 0) return null

    // 任一 ID 无效都放弃重试，避免悄悄改变导出范围。
    val chapterIds = LongArray(ids.length()) { index ->
        when (val id = ids.opt(index)) {
            is Int -> id.toLong()
            is Long -> id
            else -> 0L
        }
    }
    return chapterIds.takeIf { values -> values.all { it > 0L } }
}
