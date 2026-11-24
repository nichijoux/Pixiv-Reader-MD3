package com.pixiv.reader.core.network.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插画单页下载器：先写入 `.part`（支持 Range 续传）再 rename 为 `filesDir/Downloads/pixiv_{id}/p_{n}.jpg`。
 * 前台中断仅残留 `.part`，避免被后台整本下载的「正式文件已存在即跳过」误判为完整页。
 */
@Singleton
class IllustPageDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val progressDownloader: ProgressDownloader,
) {

    suspend fun downloadPage(
        illustId: Long,
        pageIndex: Int,
        url: String,
        onProgress: suspend (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> {
        val dir = File(context.filesDir, "Downloads/pixiv_$illustId").apply { mkdirs() }
        val target = File(dir, "p_${pageIndex + 1}.jpg")
        // 仅正式文件命中断点跳过；中断残留的 .part 不会命中
        if (target.exists() && target.length() > 0L) return Result.success(target)
        val name = "pixiv_$illustId/p_${pageIndex + 1}.jpg.part"
        return progressDownloader.download(url, name, onProgress).map { part ->
            if (!part.renameTo(target)) error("rename ${part.name} -> ${target.name} failed")
            target
        }
    }
}
