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
 * 时间格式: yyyy-MM-dd'T'HH:mm:ssxxx（如 `+09:00`，零偏移为 `+00:00`）。
 * 服务端仅校验 time 与 hash 自洽（PixivPy 等参考实现一直发送带冒号的 ISO 8601 偏移）。
 * hash = MD5(time + secret)
 *
 * 线程安全：共享的 [DateTimeFormatter] 是不可变实现，OkHttp 多工作线程并发调用
 * [build] 不会产生损坏的时间串（此前的 SimpleDateFormat 非线程安全）。
 */
data class RequestNonce(
    val time: String,
    val hash: String,
) {
    companion object {
        /**
         * 时间格式器（不可变、线程安全）。
         *
         * 必须用 `xxx` 而非 `ZZZZZ`：DateTimeFormatter 的 `ZZZZZ` 零偏移会输出 `Z`。
         * 与旧实现的差异：旧 SimpleDateFormat 的「ZZZZZ」实际按 RFC 822 输出无冒号
         * 偏移（如 `+0900`），此处改为 ISO 8601 带冒号（`+09:00`），与 PixivPy 等
         * 参考实现的 isoformat 一致；服务端只按 time+hash 自洽校验，两种均可接受。
         */
        private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.US)

        /**
         * 生成一次请求签名。
         *
         * @return 含 `x-client-time` 时间串与其 MD5 签名的 [RequestNonce]
         */
        fun build(): RequestNonce {
            // 带偏移量的时间（系统默认时区）
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
