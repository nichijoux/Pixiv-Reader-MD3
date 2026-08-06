package com.pixiv.reader.feature.illust

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.download.DownloadWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/**
 * 插画下载后台任务（WorkManager）：下载整个作品（全部页）或指定单页到 `filesDir/Downloads/pixiv_{id}/`。
 * inputData：`illustId`（必填）、`pageIndex`（可选；缺省/-1 = 下载全部页，≥0 = 查看器下载单页）。
 *
 * 进度按页加权写入下载索引（`progress` 0-100：`(已完页×100 + 当前页字节%)/总页`）；
 * 完成/失败发系统通知。
 * 普通 [CoroutineWorker]（非 @HiltWorker）+ Hilt EntryPoint 手动取依赖——规避 @HiltWorker 聚合空 Map 问题。
 */
class IllustDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val illustId = inputData.getLong(KEY_ILLUST_ID, 0L)
        val pageIndex = inputData.getLong(KEY_PAGE_INDEX, -1L)
        if (illustId <= 0L) return Result.failure()
        // 手动取依赖（EntryPoint 聚合由 Hilt 生成，确保可用）
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java)
        val pixivRepository = entryPoint.pixivRepository()
        val progressDownloader = entryPoint.progressDownloader()
        val downloadEntryDao = entryPoint.downloadEntryDao()
        return try {
            val illust = pixivRepository.api.getIllust(illustId).illust ?: error("作品不存在")
            val pages = illust.toPages()
            if (pages.isEmpty()) return Result.failure()
            if (pageIndex >= 0) {
                downloadPage(
                    downloadEntryDao, progressDownloader, illust, pages[pageIndex.toInt()],
                    pageIndex.toInt(), pages.size, single = true,
                )
            } else {
                pages.forEachIndexed { index, page ->
                    downloadPage(downloadEntryDao, progressDownloader, illust, page, index, pages.size, single = false)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "插画下载失败 illustId=$illustId", e)
            runCatching { upsert(downloadEntryDao, illustId, status = "failed", progress = 0, localPath = null, pageCount = 0) }
            Result.retry()
        }
    }

    /** 下载单页：downloading（页加权进度）→ 保存；最后（或单页）标记完成。
     * 断点重下：目标文件已存在（上次完整下载）→ 跳过本页；否则下载到 `.part` 临时文件
     * （ProgressDownloader 对已存在的 `.part` 自动 Range 续传），成功后 rename 为正式文件。 */
    private suspend fun downloadPage(
        dao: com.pixiv.reader.core.database.dao.DownloadEntryDao,
        downloader: com.pixiv.reader.core.network.download.ProgressDownloader,
        illust: Illust,
        page: IllustPageInfo,
        index: Int,
        total: Int,
        single: Boolean,
    ) {
        val url = page.originalUrl ?: page.displayUrl ?: error("该页无图片地址")
        // 子路径下载到 Downloads/pixiv_{id}/p_{n}.jpg（ProgressDownloader 自动建目录）
        val dir = File(applicationContext.filesDir, "Downloads/pixiv_${illust.id}").apply { mkdirs() }
        val target = File(dir, "p_${index + 1}.jpg")
        val part = File(dir, "p_${index + 1}.jpg.part")
        // 页加权进度：基值 = 已完页占比，跨度 = 当前页占比
        val base = if (single) 0 else (index * 100) / total
        val span = if (single) 100 else 100 / total

        // 断点重下：目标文件已存在（上次完成）→ 跳过本页，仅推进进度
        if (target.exists() && target.length() > 0L) {
            val isLast = index == total - 1
            upsert(
                dao, illust.id,
                status = if (isLast) "done" else "downloading",
                progress = (base + span).coerceIn(0, 100),
                localPath = dir.path,
                pageCount = total,
                width = illust.width,
                height = illust.height,
                title = illust.title.orEmpty(),
                coverUrl = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
            )
            return
        }

        upsert(
            dao, illust.id, status = "downloading", progress = base, localPath = dir.path, pageCount = total,
            title = illust.title.orEmpty(),
            coverUrl = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
            // 首次写入即带作品宽高：下载中卡片按真实比例完整显示（避免回退固定高度）
            width = illust.width,
            height = illust.height,
        )
        var lastWritten = base
        // 下载到 .part（存在则 Range 续传）；成功后 rename 为正式文件
        downloader.download(url, "pixiv_${illust.id}/p_${index + 1}.jpg.part", onProgress = { done, totalBytes ->
            val pagePct = if (totalBytes > 0) ((done * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
            val overall = (base + pagePct * span / 100).coerceIn(0, 100)
            if (overall - lastWritten >= 1) {
                lastWritten = overall
                dao.updateProgress("illust", illust.id, "", overall)
            }
        }).getOrThrow()
        part.renameTo(target)
        // 解析下载文件的真实宽高（toPages() 不含宽高，避免下载管理页固定高度裁剪）
        val file = target
        val (fileWidth, fileHeight) = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, opts)
            opts.outWidth to opts.outHeight
        }.getOrElse { 0 to 0 }
        // 中途页保持 downloading（管理页显示页加权进度条），仅最后一页标记完成
        val isLast = index == total - 1
        upsert(
            dao, illust.id,
            status = if (isLast) "done" else "downloading",
            progress = (base + span).coerceIn(0, 100),
            localPath = dir.path,
            pageCount = total,
            width = if (page.width > 0) page.width else fileWidth,
            height = if (page.height > 0) page.height else fileHeight,
            title = illust.title.orEmpty(),
            coverUrl = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
        )
    }

    private suspend fun upsert(
        dao: com.pixiv.reader.core.database.dao.DownloadEntryDao,
        id: Long,
        status: String,
        progress: Int,
        localPath: String?,
        pageCount: Int,
        width: Int = 0,
        height: Int = 0,
        title: String = "",
        coverUrl: String? = null,
    ) {
        runCatching {
            dao.upsert(
                DownloadEntryEntity(
                    targetId = id,
                    targetType = "illust",
                    title = title,
                    coverUrl = coverUrl,
                    localPath = localPath,
                    status = status,
                    progress = progress,
                    pageCount = pageCount,
                    width = width,
                    height = height,
                ),
            )
        }.onFailure { Log.w(TAG, "写下载索引失败 id=$id status=$status", it) }
    }

    companion object {
        private const val TAG = "IllustDownloadWorker"
        const val KEY_ILLUST_ID = "illustId"
        const val KEY_PAGE_INDEX = "pageIndex"
    }
}
