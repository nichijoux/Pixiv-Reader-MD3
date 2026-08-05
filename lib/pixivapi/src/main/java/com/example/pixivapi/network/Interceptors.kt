package com.example.pixivapi.network

import com.example.pixivapi.PixivConstants
import com.example.pixivapi.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App API 请求头拦截器
 *
 * 对齐 Pixiv-Shaft `ceui/loxia/HeaderInterceptor.kt`：
 * - iOS 官方客户端身份（UA / app-os / app-version）
 * - x-client-time / x-client-hash 签名
 * - Authorization Bearer token（登录后）
 */
class HeaderInterceptor(
    private val session: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeaders(session)
            .build()
        return chain.proceed(request)
    }

    private fun Request.Builder.addHeaders(session: SessionManager): Request.Builder {
        val nonce = RequestNonce.build()
        val token = session.bearerTokenOrEmpty()
        if (token.isNotEmpty()) {
            addHeader(PixivConstants.HEADER_AUTH, token)
        }
        return addHeader("accept-language", PixivLang.code)
            .addHeader("app-accept-language", PixivLang.code)
            .addHeader("app-os", "ios")
            .addHeader("app-os-version", PixivConstants.APP_OS_VERSION)
            .addHeader("app-version", PixivConstants.APP_VERSION)
            .addHeader("x-client-time", nonce.time)
            .addHeader("x-client-hash", nonce.hash)
            .addHeader("user-agent", PixivConstants.APP_USER_AGENT)
    }
}

/**
 * 网页 API 请求头拦截器（Cookie + Referer + 网页 UA）
 *
 * 对齐 Pixiv-Shaft `ceui/loxia/WebHeaderInterceptor.kt`。
 * 注意：cf_clearance cookie 绑定 UA，WebView 与 OkHttp 必须一致。
 */
class WebHeaderInterceptor(
    private val cookieProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // 网页接口的 lang 查询参数按当前应用语言重写（仅当 URL 已含 lang 参数，避免误加）
        val finalUrl = if (original.url.queryParameter("lang") != null) {
            original.url.newBuilder().setQueryParameter("lang", PixivLang.code).build()
        } else {
            original.url
        }
        val request = original.newBuilder()
            .url(finalUrl)
            .addHeader("Host", "www.pixiv.net")
            .addHeader("accept-language", PixivLang.code)
            .addHeader("Cookie", cookieProvider())
            .addHeader("Referer", "https://www.pixiv.net/")
            .addHeader("User-Agent", PixivConstants.WEB_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

/**
 * 请求签名：x-client-time / x-client-hash
 *
 * 时间格式: yyyy-MM-dd'T'HH:mm:ssZZZZZ
 * hash = MD5(time + secret)
 */
data class RequestNonce(
    val time: String,
    val hash: String,
) {
    companion object {
        private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US)

        fun build(): RequestNonce {
            val time = format.format(Date())
            val hash = md5(time + PixivConstants.CLIENT_TIME_SECRET)
            return RequestNonce(time, hash)
        }
    }
}

internal fun md5(plainText: String): String {
    val md = MessageDigest.getInstance("MD5")
    md.update(plainText.toByteArray())
    return md.digest().joinToString("") { "%02x".format(it) }
}
