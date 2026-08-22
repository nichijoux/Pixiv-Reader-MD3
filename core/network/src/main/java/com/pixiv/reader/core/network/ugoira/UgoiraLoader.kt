package com.pixiv.reader.core.network.ugoira

import android.content.Context
import android.util.Log
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
 *
 * 进程内**完成结果**缓存：成功帧列表驻留内存（瀑布流卡片滚动反复进出直接命中），
 * 命中时校验首帧文件仍存在（「清除缓存」清掉 cacheDir/ugoira 后自动失效重载）；
 * 失败不缓存（下次重试）。
 *
 * 刻意不做 in-flight Deferred 共享：取消的加载（如卡片滚出视口导致 LaunchedEffect 取消）
 * 若残留已取消的 Deferred，后续同 id 调用 await 会立即抛 CancellationException——
 * 造成查看器「动图加载中…」永久显示（frames 永远空）。完成结果缓存下取消只影响调用方自身，
 * 不污染、不传播；并发同 id 重复加载概率极低（LazyGrid key=id 保证同 id 单实例），且 zip
 * 有磁盘缓存兜底。
 */
@Singleton
class UgoiraLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    private val completed = ConcurrentHashMap<Long, List<UgoiraFrame>>()

    /**
     * 返回帧列表；失败返回 null（成功结果进程内缓存，同 id 重复调用不重新请求）。
     *
     * @param onProgress zip 下载进度回调（0..1，按整百分比降频；IO 线程调用，需自行切线程写状态）；
     *   磁盘 zip 命中或非下载路径不回调。
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
        }.getOrNull() ?: return@withContext null

        if (!zipFile.exists() || zipFile.length() == 0L) {
            pixivRepository.imageClient.newCall(Request.Builder().url(zipUrl).build())
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    val total = body.contentLength()
                    var read = 0L
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        zipFile.outputStream().use { out ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                // 进度按整百分比降频（避免每 8KB 一次回调刷屏重组）
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
                }
        }
        if (!zipFile.exists()) return@withContext null

        // 解压阶段独立 try：zip 损坏（下载中断残留半截文件 / 磁盘异常）时删除该 zip，
        // 下次 prepare 重新下载——避免损坏 zip 被"exists && length>0"判定永久缓存，
        // 否则该动图每次进入都加载失败（直到用户手动清缓存）。
        // 已解压出的帧文件保留（zip 重下后 `!out.exists()` 跳过），天然断点续解压。
        try {
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
        } catch (e: Exception) {
            Log.w(TAG, "ugoira 解压失败，删除损坏 zip（下次重新下载）illustId=$illustId", e)
            runCatching { zipFile.delete() }
            null
        }
    }

    private companion object {
        const val TAG = "UgoiraLoader"
    }
}
