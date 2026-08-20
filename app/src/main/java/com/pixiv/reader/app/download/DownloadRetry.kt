package com.pixiv.reader.app.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.feature.illust.IllustDownloadWorker
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportWorker

/**
 * 下载重试（app 层）：按 targetType 重建对应后台任务（下载管理页 failed 条目）。
 *
 * 断点续传：已下载部分（插画 `.part` 文件 / 小说导出章节缓存）由各 Worker 自动复用，只补缺失部分。
 *
 * 注：分发逻辑依赖两个 feature 的 Worker 类（feature:illust / feature:novel），
 * 受「feature 之间禁止互相依赖」硬约束无法下沉 feature:user，故落在 app 层独立文件
 * （app 依赖全部 feature，引用合法），使导航文件不承载业务逻辑。
 */
fun retryDownload(context: Context, entry: DownloadEntryEntity) {
    when (entry.targetType) {
        "illust" -> WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<IllustDownloadWorker>()
                .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to entry.targetId))
                .build(),
        )
        "novel" -> {
            val format = runCatching { NovelExportFormat.valueOf(entry.format) }
                .getOrDefault(NovelExportFormat.TXT)
            val data = mutableListOf<Pair<String, Any?>>()
            data += NovelExportWorker.KEY_NOVEL_ID to entry.targetId
            entry.seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
            data += NovelExportWorker.KEY_FORMAT to format.name
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<NovelExportWorker>()
                    .setInputData(workDataOf(*data.toTypedArray()))
                    .build(),
            )
        }
        // ugoira / 其他类型暂不支持重试
    }
}
