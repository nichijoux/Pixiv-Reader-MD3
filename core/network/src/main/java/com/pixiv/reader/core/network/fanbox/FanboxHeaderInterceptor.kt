package com.pixiv.reader.core.network.fanbox

import android.webkit.CookieManager
import com.pixiv.api.PixivConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * FANBOX OkHttp 请求头拦截器（无屏 WebView 走 [FanboxWebBridge]）：缺 `Origin` 必 400；
 * Cookie 独立于 pixiv OAuth，每请求从 WebView [CookieManager] 现取不存副本；UA 固定
 * [PixivConstants.WEB_USER_AGENT]（WebView 侧须保持默认 UA，避免指纹不符触发 CF 风控）。
 */
class FanboxHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Origin", FANBOX_ORIGIN)
            .header("Referer", "$FANBOX_ORIGIN/")
            .header("Accept", "application/json")
            .header("User-Agent", PixivConstants.WEB_USER_AGENT)
        currentCookie()?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        return chain.proceed(builder.build())
    }

    companion object {
        const val FANBOX_ORIGIN = "https://www.fanbox.cc"

        const val FANBOX_URL = "https://www.fanbox.cc/"

        /**
         * 必须走 `www.fanbox.cc/login` 触发 302 携带官方 SSO 回调；手工拼 `return_to` 会跳过凭证换发，
         * 导致仅种游客 FANBOXSESSID、接口仍 401。
         */
        const val FANBOX_LOGIN_URL = "https://www.fanbox.cc/login"

        const val SESSION_COOKIE = "FANBOXSESSID"

        /** 内置 WebView 白名单：含 accounts.pixiv.net 以保证登录回跳留在 App 内拿到登录态。 */
        fun isAllowedWebHost(host: String?): Boolean =
            host != null && (host == "fanbox.cc" || host.endsWith(".fanbox.cc") || host == "accounts.pixiv.net")

        /** 现取 WebView 全局 CookieManager 中的 FANBOX Cookie；组件未初始化时返回 null。 */
        fun currentCookie(): String? =
            runCatching { CookieManager.getInstance().getCookie(FANBOX_ORIGIN) }.getOrNull()

        /** 检查本地是否存在 FANBOXSESSID；存在不代表未过期，401 由调用方兜底重登。 */
        fun hasSession(): Boolean =
            currentCookie()?.contains("$SESSION_COOKIE=") == true
    }
}
