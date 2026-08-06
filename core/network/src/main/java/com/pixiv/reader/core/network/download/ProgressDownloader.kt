package com.pixiv.reader.core.network.download

import android.content.Context
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer

/**
 * 分块下载工具：下载文件到 `filesDir/Downloads`，分块读取并回调字节进度（供插画原图下载）。
 *
 * 用 OkHttp `source()` 按 8KB 分块读并写文件，进度回调 `(done, total)`；调用方负责节流
 * （如百分比变化 ≥2% 才写数据库）。
 */
@Singleton
class ProgressDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    /**
     * 分块下载 URL 到 `filesDir/Downloads/{name}`（`name` 可含子目录，如 `pixiv_123/p_1.jpg`，自动建目录）。
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
            pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("空响应体")
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
            file
        }
    }

    private companion object {
        /** 分块大小 8KB。 */
        const val CHUNK_SIZE = 8192L
    }
}
