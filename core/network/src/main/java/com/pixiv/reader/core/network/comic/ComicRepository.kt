package com.pixiv.reader.core.network.comic

import android.util.Log
import com.pixiv.api.model.ComicReadEpisode
import com.pixiv.api.network.ComicApi
import java.io.IOException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** COMIC 数据仓库：提供只读 JSON 接口与阅读两步流（取 viewer salt → 签名调 read_v4）。 */
@Singleton
class ComicRepository @Inject constructor(
    private val comicApi: ComicApi,
    private val okHttpClient: OkHttpClient,
) {

    val api: ComicApi get() = comicApi

    /**
     * 阅读两步流：拉 viewer HTML 解析随机 salt，再以 `X-Client-Hash = SHA256(time+salt)` 调 read_v4。
     * viewer 不可达、salt 解析失败或响应为空抛 [IOException]；被 WAF 拒签抛 [retrofit2.HttpException]。
     */
    suspend fun fetchReadEpisode(episodeId: Long): ComicReadEpisode {
        val html = fetchViewerHtml(episodeId)
            ?: throw IOException("viewer page unavailable, episodeId=$episodeId")
        val salt = ComicViewerSaltParser.parse(html)
            ?: throw IOException("viewer salt parse failed, episodeId=$episodeId")

        // 服务端按时刻差校验，RFC3339 带本地时区偏移即可通过
        val time = rfc3339Now()
        val hash = sha256Hex(time + salt)
        return comicApi.readEpisode(episodeId, time, hash).data?.readingEpisode
            ?: throw IOException("empty reading episode, episodeId=$episodeId")
    }

    /** 单点拉取 viewer HTML（若日后风控拦 OkHttp 可在此切换 WebView 页内 fetch）；失败返回 null。 */
    suspend fun fetchViewerHtml(episodeId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${ComicHeaderInterceptor.COMIC_URL}viewer/stories/$episodeId")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "viewer 页 HTTP ${response.code}, episodeId=$episodeId")
                    return@use null
                }
                response.body?.string()
            }
        }.getOrElse {
            Log.w(TAG, "viewer 页请求失败, episodeId=$episodeId: $it")
            null
        }
    }

    private fun rfc3339Now(): String =
        ZonedDateTime.now().format(RFC3339_SECONDS)

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ComicRepository"

        private val RFC3339_SECONDS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    }
}
