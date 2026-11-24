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
 * 按 targetType 重建下载任务并复位为 pending；Worker 自动复用已有断点。
 * 因 feature 间禁止互依，无法下沉至 feature:user，统一放在 app 层调度。
 */
suspend fun retryDownload(context: Context, entry: DownloadEntryEntity) {
    // 缺少分册选择的旧条目保持失败态，避免改变原导出范围
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
            OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to entry.targetId,
                        UgoiraExportWorker.KEY_FORMAT to UgoiraExportFormat.from(entry.format).format,
                    )
                )
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
        // 以原 workTag 唯一入队（KEEP），防止重复并发任务
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
