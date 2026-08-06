package com.pixiv.reader.feature.novel.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小说导出 worker 的依赖入口（普通 Worker + Hilt EntryPoint 手动取依赖）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NovelExportWorkerEntryPoint {
    fun novelExporter(): NovelExporter
}

/**
 * 小说导出后台任务（WorkManager）：TXT / EPUB 导出（单本或整个系列），支持断点续传。
 * inputData：`novelId`（必填）、`seriesId`（可选，>0 时导出整个系列）、`format`（"TXT"/"EPUB"）。
 *
 * 导出过程由 [NovelExporter.exportResumable] 逐章缓存到临时目录；失败返回 [Result.retry]，
 * WorkManager 自动重跑时只补缺失章节（断点重下）。完成/失败状态写入下载索引（下载管理页可见）。
 * 普通 [CoroutineWorker]（非 @HiltWorker）+ EntryPoint 手动取依赖——规避 @HiltWorker 聚合空 Map 问题。
 */
class NovelExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val novelId = inputData.getLong(KEY_NOVEL_ID, 0L)
        val seriesId = inputData.getLong(KEY_SERIES_ID, 0L).takeIf { it > 0L }
        val format = runCatching {
            NovelExportFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "TXT")
        }.getOrDefault(NovelExportFormat.TXT)
        if (novelId <= 0L) return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, NovelExportWorkerEntryPoint::class.java)
        val novelExporter = entryPoint.novelExporter()
        return try {
            // 失败状态由 exportResumable 内部写入（markFailed），这里只决定是否重试
            novelExporter.exportResumable(novelId, format, seriesId).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "小说导出失败 novelId=$novelId seriesId=$seriesId format=$format", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NovelExportWorker"
        const val KEY_NOVEL_ID = "novelId"
        const val KEY_SERIES_ID = "seriesId"
        const val KEY_FORMAT = "format"
    }
}
