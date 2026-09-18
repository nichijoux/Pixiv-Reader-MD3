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

/**
 * pixiv COMIC 数据仓库：JSON 只读接口（[api]）+ 阅读两步流
 * （viewer 页取随机 salt → 计算 X-Client 签名头 → read_v4 取页数据）。
 */
@Singleton
class ComicRepository @Inject constructor(
    private val comicApi: ComicApi,
    private val okHttpClient: OkHttpClient,
) {

    /** COMIC JSON 只读接口（排行 / 首页 / 作品 / 章节 / 搜索）。 */
    val api: ComicApi get() = comicApi

    /**
     * 阅读两步流：① OkHttp 拉 viewer 页 HTML 解析随机 salt；② 以
     * `X-Client-Time`（RFC3339 秒级当前时间）+ `X-Client-Hash`（SHA256(time+salt)）
     * 签名调 read_v4。
     *
     * @param episodeId 章节 id
     * @return 阅读页数据（含打乱图列表）
     * @throws IOException viewer 页不可达 / salt 解析失败 / 响应体缺失
     * @throws retrofit2.HttpException read_v4 被 WAF 拒绝（403）或签名被拒
     */
    suspend fun fetchReadEpisode(episodeId: Long): ComicReadEpisode {
        // ① salt 每次页面加载随机生成，必须先取 viewer 页；失败直接抛给 VM 统一兜底
        val html = fetchViewerHtml(episodeId)
            ?: throw IOException("viewer page unavailable, episodeId=$episodeId")
        val salt = ComicViewerSaltParser.parse(html)
            ?: throw IOException("viewer salt parse failed, episodeId=$episodeId")

        // ② 签名：X-Client-Hash = hex(SHA256(X-Client-Time + salt))；
        //    服务端按时刻差校验，本地时区偏移即可（实测 +00:00 与本地偏移均通过）
        val time = rfc3339Now()
        val hash = sha256Hex(time + salt)
        return comicApi.readEpisode(episodeId, time, hash).data?.readingEpisode
            ?: throw IOException("empty reading episode, episodeId=$episodeId")
    }

    /**
     * 拉 viewer 页 HTML。**单点化**：若日后 Cloudflare 升级风控拦截 OkHttp
     * （参照 FANBOX post.info 的先例），仅需把本方法替换为无屏 WebView 页内
     * fetch（照 FanboxWebBridge 模式），签名与解析链路不变。
     *
     * @param episodeId 章节 id
     * @return HTML 全文；网络失败 / 非 2xx 返回 null
     */
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

    /** 当前时间的 RFC3339 秒级表示（带本地时区偏移，如 `2026-09-15T16:41:59+08:00`）。 */
    private fun rfc3339Now(): String =
        ZonedDateTime.now().format(RFC3339_SECONDS)

    /** SHA-256 摘要的小写 hex 串。 */
    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ComicRepository"

        /** RFC3339 秒级精度 + 时区偏移。 */
        private val RFC3339_SECONDS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    }
}
