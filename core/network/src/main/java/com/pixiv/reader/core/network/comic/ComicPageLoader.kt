package com.pixiv.reader.core.network.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.ComicPage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * COMIC 正文页加载器：下载打乱图（双签名头）→ 解码 → gridshuffle 去扰 →
 * JPEG 落盘缓存，返回可直接交给 Coil 展示的本地文件。
 *
 * 正文图 CDN（img-comic.pximg.net）的鉴权是「每页一签名」：请求必须同时带
 * `Referer: https://comic.pixiv.net/`（缺则 403）与
 * `x-cobalt-thumber-parameter-gridshuffle-key: {page.key}`（缺则 400），二者
 * 按 ComicPage 动态变化，无法走全局图片拦截器，因此不经 Coil、由本类直下。
 * 已还原页落盘在 `cacheDir/comic_pages/{episodeId}/{index}.jpg`，重读零开销，
 * 目录属系统缓存可随时回收。
 */
@Singleton
class ComicPageLoader @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttpClient: OkHttpClient,
) {

    /** 还原页缓存根目录。 */
    private val cacheRoot = File(context.cacheDir, CACHE_DIR_NAME)

    /** 下载并发闸（弱网下限流，避免整话同时打满连接）。 */
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    /** 去扰 / 编解码所在协程调度上下文（测试可替换）。 */
    private val ioContext: CoroutineContext = Dispatchers.IO

    /**
     * 取一页还原后的本地文件（命中缓存直接返回；未命中则下载 + 去扰 + 落盘）。
     *
     * @param episodeId 章节 id（缓存目录键）
     * @param index 页序号（缓存文件名键，从 0 起）
     * @param page 页数据（url / key / gridsize）
     * @return 还原后的 JPEG 文件
     * @throws IOException 下载失败 / 非 2xx / 解码失败
     */
    suspend fun loadPage(episodeId: Long, index: Int, page: ComicPage): File =
        withContext(ioContext) {
            val cache = cacheFile(episodeId, index)
            // 快路径：已还原过
            if (cache.isFile && cache.length() > 0) return@withContext cache
            downloadSemaphore.withPermit {
                // 慢路径二查：并发下同页可能排队重复，获得闸后再查一次
                if (cache.isFile && cache.length() > 0) return@withPermit cache
                val scrambledBytes = download(page)
                val scrambled = BitmapFactory.decodeByteArray(scrambledBytes, 0, scrambledBytes.size)
                    ?: throw IOException("page decode failed, episodeId=$episodeId page=$index")
                val clean = try {
                    ComicGridUnscrambler.unscramble(scrambled, page.gridsize, page.key.orEmpty())
                } finally {
                    scrambled.recycle()
                }
                try {
                    // 先建目录再写文件；写临时文件后改名，避免中断留下半张图被误命中
                    cache.parentFile?.mkdirs()
                    val tmp = File(cache.parentFile, "${cache.name}.tmp")
                    FileOutputStream(tmp).use { out ->
                        clean.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    if (!tmp.renameTo(cache)) {
                        tmp.delete()
                        throw IOException("cache rename failed, episodeId=$episodeId page=$index")
                    }
                } finally {
                    clean.recycle()
                }
                cache
            }
        }

    /**
     * 下载一页打乱图（带 Referer + 每页 gridshuffle 签名头）。
     *
     * @param page 页数据
     * @return 图片字节
     * @throws IOException 网络 / 非 2xx / url 或 key 缺失
     */
    private suspend fun download(page: ComicPage): ByteArray = withContext(ioContext) {
        val url = page.url?.takeIf { it.isNotEmpty() } ?: throw IOException("page url missing")
        val key = page.key?.takeIf { it.isNotEmpty() } ?: throw IOException("page key missing")
        val request = Request.Builder()
            .url(url)
            .header("Referer", "${ComicHeaderInterceptor.COMIC_ORIGIN}/")
            // 每页签名：CDN 校验该头与 URL 中 gridshuffle 变换串的对应关系
            .header(GRIDSHUFFLE_KEY_HEADER, key)
            .header("User-Agent", PixivConstants.WEB_USER_AGENT)
            .build()
        // 调用方已在 ioContext + 信号量闸内，阻塞执行即可
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("page HTTP ${response.code}, url=$url")
            response.body?.bytes() ?: throw IOException("page empty body, url=$url")
        }
    }

    /** 某话某页的缓存文件路径。 */
    private fun cacheFile(episodeId: Long, index: Int): File =
        File(cacheRoot, episodeId.toString()).let { File(it, "$index.jpg") }

    companion object {
        /** 缓存根目录名（位于 cacheDir 下）。 */
        const val CACHE_DIR_NAME = "comic_pages"

        /** 每页图片签名的请求头名。 */
        const val GRIDSHUFFLE_KEY_HEADER = "x-cobalt-thumber-parameter-gridshuffle-key"

        /** 还原页落盘 JPEG 质量。 */
        private const val JPEG_QUALITY = 90

        /** 同时下载的页数上限。 */
        private const val MAX_CONCURRENT_DOWNLOADS = 4
    }
}
