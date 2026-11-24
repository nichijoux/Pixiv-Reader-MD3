package com.pixiv.reader.core.network.comic

import android.webkit.CookieManager
import com.pixiv.api.PixivConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * COMIC 站点请求头拦截器：仅向 HTTPS comic.pixiv.net 注入浏览器 UA、Referer/Origin 与
 * `X-Requested-With: pixivcomic`（缺一会被 WAF 403），并从 WebView [CookieManager] 现取 Cookie
 * （v1 免费内容为空，为后续站内 WebView 登录付费章节预留）。
 */
class ComicHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 正文 CDN 由加载器单独设头，跨域重定向也不得泄漏站点 Cookie
        if (request.url.host != COMIC_HOST || !request.url.isHttps) return chain.proceed(request)

        val builder = request.newBuilder()
            .header("Origin", COMIC_ORIGIN)
            .header("Referer", "$COMIC_ORIGIN/")
            .header("X-Requested-With", X_REQUESTED_WITH)
            .header("Accept", "application/json")
            .header("User-Agent", PixivConstants.WEB_USER_AGENT)
        currentCookie()?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        return chain.proceed(builder.build())
    }

    companion object {
        private const val COMIC_HOST = "comic.pixiv.net"

        const val COMIC_ORIGIN = "https://comic.pixiv.net"

        const val COMIC_URL = "https://comic.pixiv.net/"

        const val COMIC_API_BASE = "https://comic.pixiv.net/"

        const val X_REQUESTED_WITH = "pixivcomic"

        /** WebView 站内白名单：含 accounts.pixiv.net 以保证登录回跳留在 App 内拿到登录态。 */
        fun isAllowedWebHost(host: String?): Boolean =
            host != null && (host == "comic.pixiv.net" || host == "accounts.pixiv.net")

        /** 现取 WebView 全局 CookieManager 中的 COMIC Cookie；组件未初始化时返回 null。 */
        fun currentCookie(): String? =
            runCatching { CookieManager.getInstance().getCookie(COMIC_ORIGIN) }.getOrNull()
    }
}
