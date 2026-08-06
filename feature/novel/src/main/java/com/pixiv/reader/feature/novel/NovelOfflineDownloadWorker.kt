package com.pixiv.reader.feature.novel

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.offline.OfflineNovelRepository
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小说离线下载 worker 的依赖入口（普通 Worker + Hilt EntryPoint 手动取依赖）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NovelOfflineWorkerEntryPoint {
    fun novelContentLoader(): NovelContentLoader
    fun offlineNovelRepository(): OfflineNovelRepository
    fun downloadEntryDao(): DownloadEntryDao
    fun pixivRepository(): PixivRepository
}

/**
 * 小说离线下载后台任务（WorkManager）：单本或整个系列缓存到应用，失败自动重试。
 * inputData：`novelId`（必填）、`seriesId`（可选，>0 时下载整个系列）。
 *
 * 进度写入下载索引（`progress` 0-100，系列按章进度）；完成/失败发系统通知。
 * 普通 [CoroutineWorker]（非 @HiltWorker）+ EntryPoint 手动取依赖——规避 @HiltWorker 聚合空 Map 问题。
 */
class NovelOfflineDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val novelId = inputData.getLong(KEY_NOVEL_ID, 0L)
        val seriesId = inputData.getLong(KEY_SERIES_ID, 0L)
        if (novelId <= 0L) return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, NovelOfflineWorkerEntryPoint::class.java)
        val novelContentLoader = entryPoint.novelContentLoader()
        val offlineNovelRepository = entryPoint.offlineNovelRepository()
        val downloadEntryDao = entryPoint.downloadEntryDao()
        val pixivRepository = entryPoint.pixivRepository()
        // 记录当前正在下载的分册，失败时标记 failed（避免卡死在 downloading）
        var currentId = novelId
        return try {
            if (seriesId > 0L) {
                val novels = fetchSeriesNovels(pixivRepository, seriesId)
                if (novels.isEmpty()) return Result.retry()
                novels.forEachIndexed { index, chapter ->
                    currentId = chapter.id
                    downloadOne(novelContentLoader, offlineNovelRepository, downloadEntryDao, chapter.id, index, novels.size)
                }
            } else {
                downloadOne(novelContentLoader, offlineNovelRepository, downloadEntryDao, novelId, 0, 1)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "小说离线下载失败 novelId=$novelId", e)
            runCatching { upsert(downloadEntryDao, currentId, status = "failed", progress = 0) }
            Result.retry()
        }
    }

    /** 下载单本：downloading（章进度）→ 保存 → done；返回 Novel 供标题取用。 */
    private suspend fun downloadOne(
        novelContentLoader: NovelContentLoader,
        offlineNovelRepository: OfflineNovelRepository,
        downloadEntryDao: DownloadEntryDao,
        id: Long,
        index: Int,
        total: Int,
    ): Novel? {
        upsert(downloadEntryDao, id, status = "downloading", progress = if (total > 1) (index * 100) / total else 0)
        val (novel, doc) = novelContentLoader.load(id).getOrThrow()
        offlineNovelRepository.save(novel, doc)
        upsert(downloadEntryDao, id, status = "done", progress = if (total > 1) ((index + 1) * 100) / total else 100)
        return novel
    }

    private suspend fun upsert(downloadEntryDao: DownloadEntryDao, id: Long, status: String, progress: Int) {
        runCatching {
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = id,
                    targetType = "novel_offline",
                    title = null,
                    coverUrl = null,
                    status = status,
                    progress = progress,
                    pageCount = 1,
                ),
            )
        }.onFailure { Log.w(TAG, "写下载索引失败 id=$id status=$status", it) }
    }

    private suspend fun fetchSeriesNovels(pixivRepository: PixivRepository, seriesId: Long): List<Novel> {
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
        private const val TAG = "NovelOfflineWorker"
        const val KEY_NOVEL_ID = "novelId"
        const val KEY_SERIES_ID = "seriesId"
    }
}
