package com.pixiv.api.network

import com.pixiv.api.auth.AuthRefresher
import com.pixiv.api.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Token 自动刷新拦截器
 *
 * 对齐 Pixiv-Shaft `ceui/lisa/http/TokenInterceptor.java`：
 * - 检测 HTTP 400 + "Error occurred at the OAuth process" → access_token 过期
 * - 用 refresh_token 换新 token，重放原请求
 * - "Invalid refresh token" → refresh_token 被吊销，触发登出
 */
class TokenInterceptor(
    private val session: SessionManager,
    private val refresher: AuthRefresher,
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
        }
        return false
    }

    companion object {
        private const val TOKEN_ERROR_OAUTH = "Error occurred at the OAuth process"
        private const val TOKEN_ERROR_INVALID_REFRESH = "Invalid refresh token"
    }
}

/**
 * 图片 CDN 拦截器：i.pximg.net 必须带 Referer 否则 403
 */
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host == "i.pximg.net") {
            val builder = original.newBuilder()
                .header("Referer", "https://app-api.pixiv.net/")
                .header("User-Agent", com.pixiv.api.PixivConstants.APP_USER_AGENT)
            return chain.proceed(builder.build())
        }
        return chain.proceed(original)
    }
}
