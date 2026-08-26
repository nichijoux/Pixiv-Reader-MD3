package com.pixiv.reader.core.network.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插画单页下载器（core 共享）：下载到 `filesDir/Downloads/pixiv_{id}/p_{n}.jpg`。
 *
 * 供前台（viewer 查看器下载当前页）与后台（[com.pixiv.reader.core.network.illust.IllustDownloadWorker]
 * 整本下载）共用，保证 `.part` 语义一致：
 * - 目标正式文件已完整存在（length > 0）→ 直接成功跳过（断点重下不重复拉取）
 * - 否则写入 `p_{n}.jpg.part`（[ProgressDownloader] 对已存在的 .part 自动 Range 续传），
 *   成功后 rename 为正式文件；失败保留 .part 供下次续传。
 *
 * 关键：前台下载中断只会残留 `.part`，不会被整本任务"目标已存在"的断点判定误认为完整页，
 * 避免产出截断损坏图。
 */
@Singleton
class IllustPageDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val progressDownloader: ProgressDownloader,
) {

    /**
     * 下载指定页原图。
     *
     * @param illustId 作品 ID（决定目录 `pixiv_{id}`）
     * @param pageIndex 0-based 页序号（决定文件名 `p_{n}.jpg`）
     * @param url 原图 URL（自动带 pixiv Referer）
     * @param onProgress 页内字节进度回调（已读字节，总字节；目标已完整跳过时不回调）
     * @return 正式文件（新下载完成或已存在）
     */
    suspend fun downloadPage(
        illustId: Long,
        pageIndex: Int,
        url: String,
        onProgress: suspend (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> {
        val dir = File(context.filesDir, "Downloads/pixiv_$illustId").apply { mkdirs() }
        val target = File(dir, "p_${pageIndex + 1}.jpg")
        // 断点重下：正式文件已完整 → 跳过本页（viewer 中断残留的是 .part，不会命中此分支）
        if (target.exists() && target.length() > 0L) return Result.success(target)
        val name = "pixiv_$illustId/p_${pageIndex + 1}.jpg.part"
        return progressDownloader.download(url, name, onProgress).map { part ->
            if (!part.renameTo(target)) error("rename ${part.name} -> ${target.name} failed")
            target
        }
    }
}
