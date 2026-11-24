package com.pixiv.reader.feature.novel.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.CancellationException
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NovelExportWorkerEntryPoint {
    fun novelExporter(): NovelExporter
}

/**
 * 后台小说导出 Worker：普通 [CoroutineWorker] + [NovelExportWorkerEntryPoint] 手动取依赖，
 * 规避 @HiltWorker 多模块聚合空 Map 问题；`seriesId > 0` 导出系列，`chapterIds` 非空则仅导出选中分册。
 */
class NovelExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val novelId = inputData.getLong(KEY_NOVEL_ID, 0L)
        val seriesId = inputData.getLong(KEY_SERIES_ID, 0L).takeIf { it > 0L }
        val chapterIds = inputData.getLongArray(KEY_CHAPTER_IDS)?.toList()?.takeIf { it.isNotEmpty() }
        val format = runCatching {
            NovelExportFormat.valueOf(inputData.getString(KEY_FORMAT) ?: DownloadEntryEntity.FORMAT_TXT)
        }.getOrDefault(NovelExportFormat.TXT)
        if (novelId <= 0L) return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, NovelExportWorkerEntryPoint::class.java)
        val novelExporter = entryPoint.novelExporter()
        return try {
            // 失败状态由 exportResumable 内部写入（markFailed），这里只决定是否重试
            novelExporter.exportResumable(novelId, format, seriesId, chapterIds).getOrThrow()
            Result.success()
        } catch (e: CancellationException) {
            // 协程取消（用户删除任务 / Worker 被系统停止）：不能当失败重试，否则会「复活」已删除的导出条目
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "小说导出失败 novelId=$novelId seriesId=$seriesId chapterIds=$chapterIds format=$format", e)
            // 有限重试（断点续传只补缺失章），超限转 failure 防后台无限重试
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "NovelExportWorker"
        const val KEY_NOVEL_ID = "novelId"
        const val KEY_SERIES_ID = "seriesId"
        const val KEY_FORMAT = "format"
        const val KEY_CHAPTER_IDS = "chapterIds"

        const val MAX_ATTEMPTS = 3
    }
}
