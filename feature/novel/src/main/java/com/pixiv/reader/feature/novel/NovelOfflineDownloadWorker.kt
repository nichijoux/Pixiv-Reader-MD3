package com.pixiv.reader.feature.novel

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.offline.OfflineNovelRepository
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 小说离线下载后台任务（WorkManager）：单本或整个系列缓存到应用，失败自动重试。
 * inputData：`novelId`（必填）、`seriesId`（可选，>0 时下载整个系列）。
 */
@HiltWorker
class NovelOfflineDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val novelContentLoader: NovelContentLoader,
    private val offlineNovelRepository: OfflineNovelRepository,
    private val downloadEntryDao: DownloadEntryDao,
    private val pixivRepository: PixivRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val novelId = inputData.getLong(KEY_NOVEL_ID, 0L)
        val seriesId = inputData.getLong(KEY_SERIES_ID, 0L)
        if (novelId <= 0L) return Result.failure()
        return try {
            if (seriesId > 0L) {
                val novels = fetchSeriesNovels(seriesId)
                if (novels.isEmpty()) return Result.retry()
                novels.forEach { downloadOne(it.id) }
            } else {
                downloadOne(novelId)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun downloadOne(id: Long) {
        upsert(id, status = "downloading")
        val (novel, doc) = novelContentLoader.load(id).getOrThrow()
        offlineNovelRepository.save(novel, doc)
        upsert(id, status = "done")
    }

    private suspend fun upsert(id: Long, status: String) {
        runCatching {
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = id,
                    targetType = "novel_offline",
                    title = null,
                    coverUrl = null,
                    status = status,
                    pageCount = 1,
                ),
            )
        }
    }

    private suspend fun fetchSeriesNovels(seriesId: Long): List<Novel> {
        val result = mutableListOf<Novel>()
        var lastOrder: Int? = null
        repeat(20) {
            val resp = pixivRepository.api.getNovelSeries(seriesId, lastOrder)
            resp.novels?.let { result.addAll(it) }
            val next = resp.next_url
            if (next.isNullOrBlank()) return result
            lastOrder = next.substringAfter('?', "").split('&')
                .firstOrNull { it.startsWith("last_order=") }
                ?.substringAfter('=')
                ?.toIntOrNull()
            if (lastOrder == null) return result
        }
        return result
    }

    companion object {
        const val KEY_NOVEL_ID = "novelId"
        const val KEY_SERIES_ID = "seriesId"
    }
}
