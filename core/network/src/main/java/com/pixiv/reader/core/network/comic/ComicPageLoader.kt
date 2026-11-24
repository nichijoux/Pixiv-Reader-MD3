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
 * COMIC 正文页加载器：带 Referer（缺则 403）与每页 gridshuffle-key（缺则 400）直下打乱图，
 * 去扰后以 JPEG 缓存在 `cacheDir/comic_pages/{episodeId}/{index}.jpg`。
 */
@Singleton
class ComicPageLoader @Inject constructor(
    @ApplicationContext context: Context,
    @param:ComicHttpClient private val okHttpClient: OkHttpClient,
) {

    private val cacheRoot = File(context.cacheDir, CACHE_DIR_NAME)

    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private val ioContext: CoroutineContext = Dispatchers.IO

    /** 获取还原后的本地 JPEG 缓存文件；下载、解码或落盘失败抛 [IOException]。 */
    suspend fun loadPage(episodeId: Long, index: Int, page: ComicPage): File =
        withContext(ioContext) {
            val cache = cacheFile(episodeId, index)
            if (cache.isFile && cache.length() > 0) return@withContext cache
            downloadSemaphore.withPermit {
                // 二查：并发排队期间可能已由同页请求落盘
                if (cache.isFile && cache.length() > 0) return@withPermit cache
                val scrambledBytes = download(page)
                val scrambled = BitmapFactory.decodeByteArray(scrambledBytes, 0, scrambledBytes.size)
                    ?: throw IOException("page decode failed, episodeId=$episodeId page=$index")
                var clean = scrambled
                try {
                    clean = ComicGridUnscrambler.unscramble(scrambled, page.gridsize, page.key.orEmpty())
                    // 写独立临时文件后原子改名，避免半成品污染缓存
                    val parent = cache.parentFile
                    parent?.mkdirs()
                    val tmp = File.createTempFile("${cache.name}.", ".tmp", parent)
                    try {
                        val compressed = FileOutputStream(tmp).use { out ->
                            clean.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        }
                        if (!compressed) {
                            throw IOException("page encode failed, episodeId=$episodeId page=$index")
                        }
                        if (!tmp.renameTo(cache)) {
                            throw IOException("cache rename failed, episodeId=$episodeId page=$index")
                        }
                    } finally {
                        tmp.delete()
                    }
                } finally {
                    // 未触发去扰时 clean === scrambled，避免重复 recycle
                    if (clean !== scrambled) clean.recycle()
                    scrambled.recycle()
                }
                cache
            }
        }

    private suspend fun download(page: ComicPage): ByteArray = withContext(ioContext) {
        val url = page.url?.takeIf { it.isNotEmpty() } ?: throw IOException("page url missing")
        val key = page.key?.takeIf { it.isNotEmpty() } ?: throw IOException("page key missing")
        val request = Request.Builder()
            .url(url)
            .header("Referer", "${ComicHeaderInterceptor.COMIC_ORIGIN}/")
            // CDN 校验该头与 URL 中 gridshuffle 变换串的对应关系
            .header(GRIDSHUFFLE_KEY_HEADER, key)
            .header("User-Agent", PixivConstants.WEB_USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("page HTTP ${response.code}, url=$url")
            response.body?.bytes() ?: throw IOException("page empty body, url=$url")
        }
    }

    private fun cacheFile(episodeId: Long, index: Int): File =
        File(cacheRoot, episodeId.toString()).let { File(it, "$index.jpg") }

    companion object {
        const val CACHE_DIR_NAME = "comic_pages"

        const val GRIDSHUFFLE_KEY_HEADER = "x-cobalt-thumber-parameter-gridshuffle-key"

        private const val JPEG_QUALITY = 90

        private const val MAX_CONCURRENT_DOWNLOADS = 4
    }
}
