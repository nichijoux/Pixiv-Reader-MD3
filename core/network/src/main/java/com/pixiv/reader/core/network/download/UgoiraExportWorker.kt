package com.pixiv.reader.core.network.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * ugoira 动图导出后台任务（WorkManager）：MP4 / ZIP 由 [UgoiraExportWorker.KEY_FORMAT] 指定。
 * 普通 [CoroutineWorker]（非 @HiltWorker）+ Hilt EntryPoint 手动取依赖——与
 * [com.pixiv.reader.core.network.illust.IllustDownloadWorker] 同款模式。
 *
 * 失败状态与进度由 [UgoiraExporter] 自行写入下载索引；本 Worker 仅负责重试策略：
 * 临时失败（网络/编码器抖动）有限重试，断点续传复用已下载的 zip 与已解压帧。
 */
class UgoiraExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val illustId = inputData.getLong(KEY_ILLUST_ID, 0L)
        val format = UgoiraExportFormat.from(inputData.getString(KEY_FORMAT))
        if (illustId <= 0L) return Result.failure()
        // 手动取依赖（EntryPoint 聚合由 Hilt 生成，确保可用）
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val pixivRepository = entryPoint.pixivRepository()
        val exporter = entryPoint.ugoiraExporter()
        return try {
            // 作品详情仅作卡片快照展示；拉取失败不阻断导出（导出只依赖 metadata）
            val illust = runCatching { pixivRepository.api.getIllust(illustId).illust }.getOrNull()
            exporter.export(illustId, format, illust).getOrThrow()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Worker 被停止（约束丢失/用户取消）：向上传播，状态与断点现场保持原样
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ugoira 导出失败 illustId=$illustId format=$format", e)
            // 有限重试：临时网络/编码失败自动重跑（zip 与已解压帧断点复用，成本低）
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "UgoiraExportWorker"
        const val KEY_ILLUST_ID = "illustId"
        const val KEY_FORMAT = "format"

        /** 最大尝试次数（首次 + 重试）。 */
        const val MAX_ATTEMPTS = 3
    }
}
