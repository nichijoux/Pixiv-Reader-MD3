package com.pixiv.reader.core.network.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/** 动图导出后台 Worker（经 EntryPoint 取依赖）；临时失败有限重试并复用已下载 zip 与解压帧。 */
class UgoiraExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val illustId = inputData.getLong(KEY_ILLUST_ID, 0L)
        val format = UgoiraExportFormat.from(inputData.getString(KEY_FORMAT))
        if (illustId <= 0L) return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val pixivRepository = entryPoint.pixivRepository()
        val exporter = entryPoint.ugoiraExporter()
        return try {
            // 详情仅作卡片快照，拉取失败不阻断导出
            val illust = runCatching { pixivRepository.api.getIllust(illustId).illust }.getOrNull()
            exporter.export(illustId, format, illust).getOrThrow()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 取消时透传，保留状态与断点现场
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ugoira 导出失败 illustId=$illustId format=$format", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "UgoiraExportWorker"
        const val KEY_ILLUST_ID = "illustId"
        const val KEY_FORMAT = "format"

        const val MAX_ATTEMPTS = 3
    }
}
