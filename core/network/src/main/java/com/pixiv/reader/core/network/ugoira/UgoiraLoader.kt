package com.pixiv.reader.core.network.ugoira

import android.content.Context
import android.util.Log
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request

data class UgoiraFrame(val file: File, val delayMs: Int)

/**
 * ugoira 动图准备：下载 zip → 解压到 cache → 返回帧列表；zip/图片 URL 均走 imageClient（带 Referer）。
 *
 * 只缓存完成结果（进程内，命中校验首帧文件仍在）：不能做 in-flight Deferred 共享——取消的加载
 * 若残留已取消 Deferred，后续同 id await 会立即抛 CancellationException，查看器永久停在加载中。
 */
@Singleton
class UgoiraLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    private val completed = ConcurrentHashMap<Long, List<UgoiraFrame>>()

    /**
     * 失败返回 null；成功结果进程内缓存，同 id 重复调用不重新请求。
     * onProgress 在 IO 线程回调（0..1，按整百分比降频），磁盘命中时不回调。
     */
    suspend fun prepare(illustId: Long, onProgress: ((Float) -> Unit)? = null): List<UgoiraFrame>? {
        completed[illustId]?.let { cached ->
            // 缓存可能因「清除缓存」被清理：校验首帧文件仍存在，失效则移除重载
            if (cached.isNotEmpty() && cached.first().file.exists()) return cached
            completed.remove(illustId)
        }
        val result = load(illustId, onProgress)
        if (!result.isNullOrEmpty()) completed[illustId] = result
        return result
    }

    private suspend fun load(
        illustId: Long,
        onProgress: ((Float) -> Unit)? = null,
    ): List<UgoiraFrame>? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "ugoira/$illustId").apply { mkdirs() }
        val zipFile = File(dir, "data.zip")

        // 元数据获取失败/缺帧列表：无本地现场可清理，直接返回
        val (frames, zipUrl) = runCatching {
            val meta = pixivRepository.api.getUgoiraMetadata(illustId)
            val metadata = meta.ugoira_metadata ?: return@runCatching null
            val zipUrl = metadata.zip_urls?.medium ?: return@runCatching null
            val frames = metadata.frames.orEmpty()
            if (frames.isEmpty()) return@runCatching null
            frames to zipUrl
        }.getOrElse { error ->
            // 元数据请求允许取消，取消时交由调用方协程结束。
            if (error is CancellationException) throw error
            null
        } ?: return@withContext null

        if (!zipFile.exists() || zipFile.length() == 0L) {
            try {
                downloadZip(zipUrl, zipFile, onProgress)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // 直接调用 prepare 的界面依赖 null 表示失败，且取消优先于 I/O 错误。
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "ugoira 下载失败，illustId=$illustId", error)
                return@withContext null
            }
        }

        // 解压阶段独立 try：zip 损坏（包括旧版下载残留的半截文件）或缺 entry 时删除该 zip，
        // 下次 prepare 重新下载——避免损坏 zip 被"exists && length>0"判定永久缓存，
        // 否则该动图每次进入都加载失败（直到用户手动清缓存）。
        // 已解压出的帧文件保留（zip 重下后跳过已存在帧），天然断点续解压。
        try {
            UgoiraFrameExtractor.unzip(zipFile, dir, frames)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "ugoira 解压失败，删除损坏 zip（下次重新下载）illustId=$illustId", e)
            runCatching { zipFile.delete() }
            null
        }
    }

    /** 写入同目录独立临时文件，完整接收后原子发布（ATOMIC_MOVE 不支持时降级为普通覆盖）。 */
    private suspend fun downloadZip(zipUrl: String, zipFile: File, onProgress: ((Float) -> Unit)?) {
        val coroutineContext = currentCoroutineContext()
        // 并发下载使用各自的临时文件，避免互相覆盖或清理对方的写入。
        val temporary = File.createTempFile("data-", ".part", zipFile.parentFile)
        try {
            val request = Request.Builder().url(zipUrl).build()
            pixivRepository.imageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("ugoira zip HTTP ${response.code}")
                val body = response.body ?: throw IOException("ugoira zip empty body")
                val total = body.contentLength()
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            // 按整百分比回调，避免每次读取都触发界面刷新。
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress?.invoke(read.toFloat() / total)
                                }
                            }
                        }
                    }
                }
                if (read == 0L || (total >= 0 && read != total)) {
                    throw IOException("ugoira zip incomplete body")
                }
            }
            // 优先使用原子覆盖移动；若文件系统不支持 ATOMIC_MOVE 则降级为普通覆盖移动。
            coroutineContext.ensureActive()
            try {
                Files.move(
                    temporary.toPath(),
                    zipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    zipFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            // 失败或取消仅清理本次下载，保留并发调用已发布的缓存。
            temporary.delete()
        }
    }

    private companion object {
        const val TAG = "UgoiraLoader"
    }
}
