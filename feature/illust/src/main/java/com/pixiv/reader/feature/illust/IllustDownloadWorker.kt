package com.pixiv.reader.feature.illust

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadWorkerEntryPoint
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.model.toPages
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/**
 * 插画下载后台任务（WorkManager）：下载整个作品（全部页）或指定单页到 `filesDir/Downloads/pixiv_{id}/`。
 * inputData：`illustId`（必填）、`pageIndex`（可选；缺省/-1 = 下载全部页，≥0 = 查看器下载单页）。
 *
 * 进度按页加权写入下载索引（`progress` 0-100：`(已完页×100 + 当前页字节%)/总页`）。
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
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val pixivRepository = entryPoint.pixivRepository()
        val pageDownloader = entryPoint.illustPageDownloader()
        val downloadEntryDao = entryPoint.downloadEntryDao()
        return try {
            val illust = pixivRepository.api.getIllust(illustId).illust
                ?: error(applicationContext.getString(R.string.illust_error_work_not_found))
            val pages = illust.toPages()
            if (pages.isEmpty()) return Result.failure()
            if (pageIndex >= 0) {
                downloadPage(
                    downloadEntryDao, pageDownloader, illust, pages[pageIndex.toInt()],
                    pageIndex.toInt(), pages.size, single = true,
                )
            } else {
                pages.forEachIndexed { index, page ->
                    downloadPage(
                        downloadEntryDao,
                        pageDownloader,
                        illust,
                        page,
                        index,
                        pages.size,
                        single = false
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "插画下载失败 illustId=$illustId", e)
            runCatching {
                upsert(
                    downloadEntryDao,
                    illustId,
                    status = "failed",
                    progress = 0,
                    localPath = null,
                    pageCount = 0
                )
            }
            // 有限重试：临时网络失败自动重跑（断点续传补缺失页，成本低），
            // 超限转 failure——避免后台无限重试（耗流量/电）且永不出错通知。
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /** 下载单页：downloading（页加权进度）→ 保存；最后（或单页）标记完成。
     * `.part` + rename 断点语义由共享 [IllustPageDownloader] 提供（viewer 前台下载同款），
     * 前台中断残留 .part 不会被本方法的"目标已存在"判定误认为完整页。 */
    private suspend fun downloadPage(
        dao: com.pixiv.reader.core.database.dao.DownloadEntryDao,
        pageDownloader: com.pixiv.reader.core.network.download.IllustPageDownloader,
        illust: Illust,
        page: IllustPageInfo,
        index: Int,
        total: Int,
        single: Boolean,
    ) {
        val url = page.originalUrl ?: page.displayUrl
        ?: error(applicationContext.getString(R.string.illust_error_page_no_image))
        // 子路径下载到 Downloads/pixiv_{id}/p_{n}.jpg（共享下载器自动建目录）
        val dir =
            File(applicationContext.filesDir, "Downloads/pixiv_${illust.id}").apply { mkdirs() }
        // 页加权进度：基值 = 已完页占比，跨度 = 当前页占比
        val base = if (single) 0 else (index * 100) / total
        val span = if (single) 100 else 100 / total

        upsert(
            dao,
            illust.id,
            status = "downloading",
            progress = base,
            localPath = dir.path,
            pageCount = total,
            title = illust.title.orEmpty(),
            coverUrl = illust.image_urls?.medium ?: illust.image_urls?.square_medium,
            // 首次写入即带作品宽高：下载中卡片按真实比例完整显示（避免回退固定高度）
            width = illust.width,
            height = illust.height,
            payloadJson = illustPayload(illust),
        )
        var lastWritten = base
        // 共享下载器：正式文件已完整 → 直接成功跳过；否则下载 .part（存在则 Range 续传）并 rename
        pageDownloader.downloadPage(illust.id, index, url, onProgress = { done, totalBytes ->
            val pagePct =
                if (totalBytes > 0) ((done * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
            val overall = (base + pagePct * span / 100).coerceIn(0, 100)
            if (overall - lastWritten >= 1) {
                lastWritten = overall
                dao.updateProgress("illust", illust.id, "", overall)
            }
        }).getOrThrow()
        // 解析下载文件的真实宽高（toPages() 不含宽高，避免下载管理页固定高度裁剪）
        val file = File(dir, "p_${index + 1}.jpg")
        val (fileWidth, fileHeight) = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, opts)
            opts.outWidth to opts.outHeight
        }.getOrElse { 0 to 0 }
        // 中途页保持 downloading（管理页显示页加权进度条），仅最后一页标记完成；
        // 单页下载（single=true，查看器「下载本页」）无论页序都视为完成，
        // 否则非末页单页下载会永久卡 downloading 100%（isLast 恒 false）。
        val isLast = single || index == total - 1
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
            payloadJson = illustPayload(illust),
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
        payloadJson: String? = null,
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
                    payloadJson = payloadJson,
                ),
            )
        }.onFailure { Log.w(TAG, "写下载索引失败 id=$id status=$status", it) }
    }

    /** 完整卡片快照（与浏览历史 payloadJson 同格式，下载管理页完整显示用）。 */
    private fun illustPayload(illust: Illust): String = org.json.JSONObject().apply {
        put("id", illust.id)
        put("title", illust.title.orEmpty())
        put("coverUrl", illust.image_urls?.medium ?: illust.image_urls?.square_medium)
        put("width", illust.width)
        put("height", illust.height)
        put("bookmarks", illust.total_bookmarks ?: 0)
        put("pageCount", illust.page_count)
        put("isBookmarked", illust.is_bookmarked == true)
    }.toString()

    companion object {
        private const val TAG = "IllustDownloadWorker"
        const val KEY_ILLUST_ID = "illustId"
        const val KEY_PAGE_INDEX = "pageIndex"

        /** 最大尝试次数（首次 + 重试）：runAttemptCount 达到此值后转 Result.failure。 */
        const val MAX_ATTEMPTS = 3
    }
}
