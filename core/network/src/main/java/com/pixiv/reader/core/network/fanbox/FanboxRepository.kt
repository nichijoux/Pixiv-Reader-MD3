package com.pixiv.reader.core.network.fanbox

import android.util.Log
import com.google.gson.Gson
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostDetailResponse
import com.pixiv.api.network.FanboxApi
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** FANBOX 数据仓库：聚合 OkHttp 只读接口与无屏 WebView 正文通道。 */
@Singleton
class FanboxRepository @Inject constructor(
    private val fanboxApi: FanboxApi,
    private val webBridge: FanboxWebBridge,
) {

    val api: FanboxApi get() = fanboxApi

    /** 检查本地 FANBOXSESSID；存在不代表未过期，401 由调用方引导重登。 */
    fun hasSession(): Boolean = FanboxHeaderInterceptor.hasSession()

    /** 经无屏 WebView 拉取带正文的 `post.info`；CF 拦截、超时或解析失败返回 null，调用方应退回 [FanboxApi.postGet]。 */
    suspend fun fetchPostInfo(postId: String): FanboxPost? {
        val encodedId = URLEncoder.encode(postId, "UTF-8")
        val raw = webBridge.get("${FANBOX_API_BASE}post.info?postId=$encodedId") ?: return null
        return runCatching {
            gson.fromJson(raw, FanboxPostDetailResponse::class.java).body?.post
        }.getOrElse {
            Log.w(TAG, "fetchPostInfo 解析失败 postId=$postId: $it")
            null
        }
    }

    companion object {
        private const val TAG = "FanboxRepository"

        const val FANBOX_API_BASE = "https://api.fanbox.cc/"
    }
}

private val gson by lazy { Gson() }
