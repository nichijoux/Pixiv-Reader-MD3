package com.pixiv.api.network

import com.pixiv.api.auth.AuthRefresher
import com.pixiv.api.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 检测 400 + "Error occurred at the OAuth process"（token 过期）→ 刷新后重放原请求；
 * "Invalid refresh token"（refresh_token 被吐销）→ 登出并回调 [onInvalidRefreshToken]。
 * 回调必须由装配层注入事件总线：拦截器线程无法直接更新 UI 登录态。
 */
class TokenInterceptor(
    private val session: SessionManager,
    private val refresher: AuthRefresher,
    private val onInvalidRefreshToken: () -> Unit = {},
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)

        if (isTokenExpired(response)) {
            response.close()
            val newBearer = refresher.refreshAndGetBearerBlocking(
                tokenForThisRequest = request.header("authorization")
            ) ?: throw IOException("access token expired and refresh failed")
            response = chain.proceed(
                request.newBuilder()
                    .header("authorization", newBearer)
                    .build()
            )
        }
        return response
    }

    private fun isTokenExpired(response: Response): Boolean {
        if (response.code != 400) return false
        // peekBody 不消费原响应流，可以安全 close 后重放
        val body = runCatching { response.peekBody(1024 * 1024).string() }.getOrNull() ?: return false
        if (body.contains(TOKEN_ERROR_OAUTH)) return true
        if (body.contains(TOKEN_ERROR_INVALID_REFRESH)) {
            session.logout()
            // 清完会话必须通知可观察层：否则 UI 仍以为已登录，后续请求全部失败而无提示
            onInvalidRefreshToken()
        }
        return false
    }

    companion object {
        private const val TOKEN_ERROR_OAUTH = "Error occurred at the OAuth process"
        private const val TOKEN_ERROR_INVALID_REFRESH = "Invalid refresh token"
    }
}

/**
 * 图片 CDN 拦截器：pximg.net 各子域必须带 Referer 否则 403。
 * COMIC 的封面/正文图只认 `comic.pixiv.net`，需按域名分流；正文打乱图另需按页签名头，
 * 由 core:network 的 ComicPageLoader 自行下载，不经过本拦截器。
 */
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host.endsWith(".pximg.net")) {
            val referer = when (original.url.host) {
                "img-comic.pximg.net", "public-img-comic.pximg.net" -> "https://comic.pixiv.net/"
                else -> "https://app-api.pixiv.net/"
            }
            val builder = original.newBuilder()
                .header("Referer", referer)
                .header("User-Agent", com.pixiv.api.PixivConstants.APP_USER_AGENT)
            return chain.proceed(builder.build())
        }
        return chain.proceed(original)
    }
}
