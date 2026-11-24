package com.pixiv.reader.feature.novel.data

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import org.json.JSONArray
import org.json.JSONObject

internal object NovelExportQueue {

    fun pendingEntry(
        novelId: Long,
        seriesId: Long?,
        chapterIds: List<Long>,
        format: NovelExportFormat,
        title: String?,
        coverUrl: String?,
        seriesTitle: String?,
        authorName: String?,
        authorAvatarUrl: String?,
        wordCount: Int,
        favoriteCount: Int,
        publishDate: String?,
    ): DownloadEntryEntity = DownloadEntryEntity(
        targetId = novelId,
        targetType = "novel",
        title = title?.let { "$it（${format.name}）" },
        coverUrl = coverUrl,
        format = format.name,
        scopeKey = novelScopeKey(seriesId, chapterIds.takeIf { it.isNotEmpty() }),
        seriesId = seriesId?.takeIf { it > 0L },
        seriesTitle = seriesTitle,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        wordCount = wordCount,
        favoriteCount = favoriteCount,
        publishDate = publishDate,
        // 部分分册：分册清单必须随快照落库——失败重试时只能从这里还原导出范围，
        // 否则 DownloadRetry 拿不到 chapterIds，会把部分导出退化成整系列
        payloadJson = chapterIds.takeIf { it.isNotEmpty() }?.let(::partialChaptersPayload),
    )

    /** 复合主键 tag 与下载管理页取消逻辑共用，须保持一致。 */
    fun buildRequest(
        novelId: Long,
        seriesId: Long?,
        chapterIds: List<Long>,
        format: NovelExportFormat,
    ): OneTimeWorkRequest {
        val scopeKey = novelScopeKey(seriesId, chapterIds.takeIf { it.isNotEmpty() })
        val data = mutableListOf<Pair<String, Any?>>()
        data += NovelExportWorker.KEY_NOVEL_ID to novelId
        data += NovelExportWorker.KEY_FORMAT to format.name
        seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
        if (chapterIds.isNotEmpty()) {
            data += NovelExportWorker.KEY_CHAPTER_IDS to chapterIds.toLongArray()
        }
        return OneTimeWorkRequestBuilder<NovelExportWorker>()
            .setInputData(workDataOf(*data.toTypedArray()))
            .setConstraints(DownloadQueue.networkConstraints())
            .addTag(DownloadQueue.workTag("novel", novelId, format.name, scopeKey))
            .build()
    }

    private fun partialChaptersPayload(chapterIds: List<Long>): String = JSONObject()
        .put(NovelExportWorker.KEY_CHAPTER_IDS, JSONArray(chapterIds))
        .toString()
}
