package com.pixiv.api.network

import com.pixiv.api.PixivConstants
import com.pixiv.api.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
 * 网页 API 请求头拦截器。注意：cf_clearance cookie 绑定 UA，WebView 与 OkHttp 必须一致。
 */
class WebHeaderInterceptor(
    private val cookieProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // 仅重写已带 lang 的 URL，避免给不需要的网页接口误加参数
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
 * 请求签名：hash = MD5(time + CLIENT_TIME_SECRET)，时间串为 ISO 8601 带冒号偏移。
 * 共享的 [DateTimeFormatter] 不可变，OkHttp 多工作线程并发调用 [build] 是安全的。
 */
data class RequestNonce(
    val time: String,
    val hash: String,
) {
    companion object {
        /**
         * 必须用 `xxx` 而不是 `ZZZZZ`：后者零偏移会输出 `Z`（旧 SimpleDateFormat 还输出无冒号 `+0900`）。
         */
        private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.US)

        fun build(): RequestNonce {
            val time = OffsetDateTime.now().format(format)
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
