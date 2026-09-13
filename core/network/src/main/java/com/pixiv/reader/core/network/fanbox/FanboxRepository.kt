package com.pixiv.reader.core.network.fanbox

import android.util.Log
import com.google.gson.Gson
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostDetailResponse
import com.pixiv.api.network.FanboxApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FANBOX 数据仓库：聚合 OkHttp 只读接口（[api]）与无屏 WebView 正文通道
 * （[fetchPostInfo]），并暴露登录态判定（[hasSession]）。
 */
@Singleton
class FanboxRepository @Inject constructor(
    private val fanboxApi: FanboxApi,
    private val webBridge: FanboxWebBridge,
) {

    /** OkHttp 只读接口（post.listHome / post.get / post.getComments / plan.listCreator）。 */
    val api: FanboxApi get() = fanboxApi

    /**
     * 是否已在 App WebView 里登录过 FANBOX（cookie 含 FANBOXSESSID）。
     * **存在 ≠ 有效**：cookie 过期后接口 401，由 VM 按响应码引导重登。
     *
     * @return 已登录过为 true
     */
    fun hasSession(): Boolean = FanboxHeaderInterceptor.hasSession()

    /**
     * 取帖子完整信息（唯一带正文的 `post.info`，经无屏 WebView 页内 fetch）。
     * CF 拦截 / 未登录 / 超时 / 解析失败一律返回 null，调用方应退回 [FanboxApi.postGet]。
     *
     * @param postId 帖子 id
     * @return 含 type / body / coverImageUrl 的帖子对象；失败 null
     */
    suspend fun fetchPostInfo(postId: String): FanboxPost? {
        val raw = webBridge.get("$FANBOX_API_BASE/post.info?postId=$postId") ?: return null
        return runCatching {
            gson.fromJson(raw, FanboxPostDetailResponse::class.java).body?.post
        }.getOrElse {
            Log.w(TAG, "fetchPostInfo 解析失败 postId=$postId: $it")
            null
        }
    }

    companion object {
        private const val TAG = "FanboxRepository"

        /** FANBOX API 基址（baseUrl 须以 / 结尾供 Retrofit 拼接）。 */
        const val FANBOX_API_BASE = "https://api.fanbox.cc/"
    }
}

/** fetchPostInfo 的 Gson 实例（无状态、懒加载共享）。 */
private val gson by lazy { Gson() }
