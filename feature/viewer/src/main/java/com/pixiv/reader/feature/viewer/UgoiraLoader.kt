package com.pixiv.reader.feature.viewer

import android.content.Context
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 单帧：本地文件 + 延迟毫秒 */
data class UgoiraFrame(val file: File, val delayMs: Int)

/**
 * ugoira 动图准备：下载 zip → 解压到 cache → 返回帧列表。
 * zip / 图片 URL 均走 imageClient（带 Referer）。
 */
@Singleton
class UgoiraLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    /** 返回帧列表；失败返回 null */
    suspend fun prepare(illustId: Long): List<UgoiraFrame>? = withContext(Dispatchers.IO) {
        runCatching {
            val meta = pixivRepository.api.getUgoiraMetadata(illustId)
            val metadata = meta.ugoira_metadata ?: return@runCatching null
            val zipUrl = metadata.zip_urls?.medium ?: return@runCatching null
            val frames = metadata.frames.orEmpty()
            if (frames.isEmpty()) return@runCatching null

            val dir = File(context.cacheDir, "ugoira/$illustId").apply { mkdirs() }
            val zipFile = File(dir, "data.zip")

            if (!zipFile.exists() || zipFile.length() == 0L) {
                pixivRepository.imageClient.newCall(Request.Builder().url(zipUrl).build())
                    .execute()
                    .use { resp ->
                        if (!resp.isSuccessful) return@use
                        resp.body?.byteStream()?.use { input ->
                            zipFile.outputStream().use { input.copyTo(it) }
                        }
                    }
            }
            if (!zipFile.exists()) return@runCatching null

            java.util.zip.ZipFile(zipFile).use { zf ->
                frames.forEach { frame ->
                    val entryName = frame.file ?: return@forEach
                    val name = entryName.substringAfterLast('/')
                    val out = File(dir, name)
                    if (!out.exists()) {
                        zf.getInputStream(zf.getEntry(entryName)).use { it.copyTo(out.outputStream()) }
                    }
                }
            }

            frames.mapNotNull { frame ->
                val entryName = frame.file ?: return@mapNotNull null
                UgoiraFrame(
                    file = File(dir, entryName.substringAfterLast('/')),
                    delayMs = (frame.delay ?: 80).coerceAtLeast(10),
                )
            }
        }.getOrNull()
    }
}
