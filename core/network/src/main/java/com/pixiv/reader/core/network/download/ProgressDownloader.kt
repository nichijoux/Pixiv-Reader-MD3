package com.pixiv.reader.core.network.download

import android.content.Context
import com.pixiv.reader.core.network.R
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分块下载工具：下载文件到 `filesDir/Downloads`，分块读取并回调字节进度（供插画原图下载）。
 *
 * 用 OkHttp `source()` 按 8KB 分块读并写文件，进度回调 `(done, total)`；调用方负责节流
 * （如百分比变化 ≥2% 才写数据库）。
 *
 * ## 断点续传（Range）
 * 目标文件已存在部分内容时自动带 `Range: bytes={existing}-`：
 * - **206**：追加写入（`total = existing + 剩余长度`，进度从断点继续）
 * - **200**：服务器忽略 Range（不支持断点）→ 全量重写
 * - **416**：现有文件 ≥ 资源大小（文件已完整）→ 直接成功返回
 * 配合调用方 `.part` 临时文件 + rename 模式可实现可靠的断点重下。
 */
@Singleton
class ProgressDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    /**
     * 分块下载 URL 到 `filesDir/Downloads/{name}`（`name` 可含子目录，如 `pixiv_123/p_1.jpg`，自动建目录）。
     * 目标文件已存在时自动断点续传（见类注释）。
     *
     * @param url 图片地址（自动带 pixiv Referer）
     * @param name 相对文件名（可含 `/` 子目录；父目录自动创建）
     * @param onProgress 进度回调（已读字节，总字节；`total` 可能为 -1 未知）；suspend 便于直接写库
     * @return 下载完成的文件
     */
    suspend fun download(
        url: String,
        name: String,
        onProgress: suspend (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "Downloads/$name").apply { parentFile?.mkdirs() }
            // 已有部分文件 → 从断点续传
            val existing = if (file.exists()) file.length() else 0L
            val request = Request.Builder().url(url).apply {
                if (existing > 0L) header("Range", "bytes=$existing-")
            }.build()
            pixivRepository.imageClient.newCall(request)
                .execute()
                .use { resp ->
                    when (resp.code) {
                        // 206 Partial Content：追加写入，进度从断点继续
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
                        // 200：服务器忽略 Range（不支持断点）→ 全量重写
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
                        // 416 Range Not Satisfiable：现有文件 ≥ 资源大小 → 视为已完整
                        HTTP_RANGE_NOT_SATISFIABLE -> { /* 直接返回现有文件 */ }
                        else -> throw IllegalStateException("HTTP ${resp.code}")
                    }
                }
            file
        }
    }

    private companion object {
        /** 分块大小 8KB。 */
        const val CHUNK_SIZE = 8192L
        const val HTTP_PARTIAL = 206
        const val HTTP_OK = 200
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
