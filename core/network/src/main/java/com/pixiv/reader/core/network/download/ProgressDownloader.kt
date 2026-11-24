package com.pixiv.reader.core.network.download

import android.content.Context
import com.pixiv.reader.core.network.R
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分块下载到 `filesDir/Downloads`（走 `imageClient` 自动带 Referer）；已有本地文件时发 Range 续传
 * （206 追加、200 全量重写、416 视为完整）。每 8KB 回调 `(done, total)`（`total` 可能为 -1），调用方自行节流。
 */
@Singleton
class ProgressDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    suspend fun download(
        url: String,
        name: String,
        onProgress: suspend (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "Downloads/$name").apply { parentFile?.mkdirs() }
            val existing = if (file.exists()) file.length() else 0L
            val request = Request.Builder().url(url).apply {
                if (existing > 0L) header("Range", "bytes=$existing-")
            }.build()
            pixivRepository.imageClient.newCall(request)
                .execute()
                .use { resp ->
                    when (resp.code) {
                        HTTP_PARTIAL -> {
                            val body = resp.body ?: throw IllegalStateException(context.getString(R.string.download_error_empty_body))
                            val total = if (body.contentLength() > 0) existing + body.contentLength() else -1L
                            body.source().use { source ->
                                java.io.FileOutputStream(file, true).use { output ->
                                    val buffer = okio.Buffer()
                                    var done = existing
                                    while (true) {
                                        val read = source.read(buffer, CHUNK_SIZE)
                                        if (read == -1L) break
                                        output.write(buffer.readByteArray())
                                        done += read
                                        onProgress(done, total)
                                    }
                                }
                            }
                        }
                        HTTP_OK -> {
                            val body = resp.body ?: throw IllegalStateException(context.getString(R.string.download_error_empty_body))
                            val total = body.contentLength()
                            body.source().use { source ->
                                file.outputStream().use { output ->
                                    val buffer = okio.Buffer()
                                    var done = 0L
                                    while (true) {
                                        val read = source.read(buffer, CHUNK_SIZE)
                                        if (read == -1L) break
                                        output.write(buffer.readByteArray())
                                        done += read
                                        onProgress(done, total)
                                    }
                                }
                            }
                        }
                        // 416 表示现有文件已达或超过目标大小，直接复用
                        HTTP_RANGE_NOT_SATISFIABLE -> {  }
                        else -> throw IllegalStateException("HTTP ${resp.code}")
                    }
                }
            Result.success(file)
        } catch (e: CancellationException) {
            // 透传协程取消：吞成 failure 会落 failed 并触发重试，导致已删除的下载条目「复活」
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8192L
        const val HTTP_PARTIAL = 206
        const val HTTP_OK = 200
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
