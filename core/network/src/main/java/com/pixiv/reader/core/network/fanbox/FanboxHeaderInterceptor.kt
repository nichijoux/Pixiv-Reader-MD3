package com.pixiv.reader.core.network.fanbox

import android.webkit.CookieManager
import com.pixiv.api.PixivConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * FANBOX 请求头拦截器（OkHttp 链路专用；无屏 WebView 走 [FanboxWebBridge]，不吃本拦截器）。
 *
 * - `Origin: https://www.fanbox.cc` 是**硬性要求**：缺了它 api.fanbox.cc 一律 400，
 *   与登录态无关；
 * - Cookie 每个请求从 WebView 的 [CookieManager] 现取、不存副本——用户在
 *   FANBOX 页面登录 / 登出，下一个请求即同步，不存在两份状态；
 * - UA 固定 [PixivConstants.WEB_USER_AGENT]（cf_clearance 绑定该 UA）。注意 WebView 侧
 *   必须保持其默认 UA、不得复用此常量（版本号与 TLS 指纹不符是 CF 机器人信号，见 D7）。
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
        /** FANBOX 站点 origin（请求头 Origin 值与 cookie 取值基址）。 */
        const val FANBOX_ORIGIN = "https://www.fanbox.cc"

        /** 无屏 / 可见 WebView 的首页壳地址。 */
        const val FANBOX_URL = "https://www.fanbox.cc/"

        /**
         * FANBOX 登录入口。**必须是 `www.fanbox.cc/login`**——它会 302 到
         * accounts.pixiv.net 并携带 fanbox 自己的 SSO 回调（实测地址栏：
         * `login?prompt=select_account&return_to=<fanbox回调>`）。若手工拼
         * `return_to=www.fanbox.cc/` 会跳过该回调：pixiv 登录完成后 fanbox 收不到
         * 换发凭证的信号，只种下游客 FANBOXSESSID（post.listHome 仍 401）。
         */
        const val FANBOX_LOGIN_URL = "https://www.fanbox.cc/login"

        /** FANBOX 登录 cookie 名。 */
        const val SESSION_COOKIE = "FANBOXSESSID"

        /**
         * FANBOX 内置 WebView 的 host 白名单：`*.fanbox.cc` 站内页 + `accounts.pixiv.net`
         * （登录流跳转域，cookie 体系与 CookieManager 共享，留在 App 内才拿得到登录态）。
         *
         * @param host 待校验 host（可空）
         * @return 允许在 App 内 WebView 加载为 true
         */
        fun isAllowedWebHost(host: String?): Boolean =
            host != null && (host == "fanbox.cc" || host.endsWith(".fanbox.cc") || host == "accounts.pixiv.net")

        /**
         * WebView 里当前的 fanbox cookie 串。
         *
         * @return cookie 串；WebView 组件未初始化等异常场景返回 null 而非抛出
         */
        fun currentCookie(): String? =
            runCatching { CookieManager.getInstance().getCookie(FANBOX_ORIGIN) }.getOrNull()

        /**
         * 是否已在 App WebView 里登录过 FANBOX（cookie 含 FANBOXSESSID）。
         * 注意：**存在 ≠ 有效**——cookie 过期后接口返回 401，由调用方按响应码兜底。
         *
         * @return 已登录过为 true
         */
        fun hasSession(): Boolean =
            currentCookie()?.contains("$SESSION_COOKIE=") == true
    }
}
