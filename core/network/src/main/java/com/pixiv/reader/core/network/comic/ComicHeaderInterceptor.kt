package com.pixiv.reader.core.network.comic

import android.webkit.CookieManager
import com.pixiv.api.PixivConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * pixiv COMIC 请求头拦截器（`/api/app/` 各端点有 WAF：缺浏览器 UA 一律 403）。
 *
 * - `User-Agent` 固定 [PixivConstants.WEB_USER_AGENT]、`Referer` / `Origin` 指向
 *   comic.pixiv.net、`X-Requested-With: pixivcomic`——三者缺一可能被 WAF 拒绝（实测组合）；
 * - Cookie 每个请求从 WebView 的 [CookieManager] 现取、不存副本。v1 免费内容
 *   无需登录此通道为空；二期在 App 内 WebView 登录 comic.pixiv.net 后，付费
 *   章节请求即自动携带 cookie，无需改动本类。
 */
class ComicHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Origin", COMIC_ORIGIN)
            .header("Referer", "$COMIC_ORIGIN/")
            .header("X-Requested-With", X_REQUESTED_WITH)
            .header("Accept", "application/json")
            .header("User-Agent", PixivConstants.WEB_USER_AGENT)
        currentCookie()?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        return chain.proceed(builder.build())
    }

    companion object {
        /** COMIC 站点 origin（请求头 Origin 值、Referer 与 cookie 取值基址）。 */
        const val COMIC_ORIGIN = "https://comic.pixiv.net"

        /** COMIC 站点首页（WebView 壳页 / 站内链接兜底）。 */
        const val COMIC_URL = "https://comic.pixiv.net/"

        /** COMIC API 基址（baseUrl 须以 / 结尾供 Retrofit 拼接）。 */
        const val COMIC_API_BASE = "https://comic.pixiv.net/"

        /** WAF 识别信号头（web 客户端特有值，实测缺它部分端点 403）。 */
        const val X_REQUESTED_WITH = "pixivcomic"

        /**
         * 二期内置 WebView 的 host 白名单：comic.pixiv.net 站内页 +
         * accounts.pixiv.net（登录流跳转域，留在 App 内才拿得到登录态）。
         *
         * @param host 待校验 host（可空）
         * @return 允许在 App 内 WebView 加载为 true
         */
        fun isAllowedWebHost(host: String?): Boolean =
            host != null && (host == "comic.pixiv.net" || host == "accounts.pixiv.net")

        /**
         * WebView 里当前的 comic cookie 串（cookie 属主是 android.webkit 全局
         * CookieManager，与未来登录 WebView 共享同一份状态）。
         *
         * @return cookie 串；WebView 组件未初始化等异常场景返回 null 而非抛出
         */
        fun currentCookie(): String? =
            runCatching { CookieManager.getInstance().getCookie(COMIC_ORIGIN) }.getOrNull()
    }
}
