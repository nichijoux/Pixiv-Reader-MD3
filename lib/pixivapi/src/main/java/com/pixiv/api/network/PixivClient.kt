package com.pixiv.api.network

import ceui.pixiv.login.PixivOAuthClient
import ceui.pixiv.login.PixivOAuthConfig
import ceui.pixiv.login.VerifierStore
import com.pixiv.api.PixivConstants
import com.pixiv.api.network.AppApi
import com.pixiv.api.network.PixivWebApi
import com.pixiv.api.auth.AuthRefresher
import com.pixiv.api.auth.SessionManager
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Pixiv 网络客户端构建器
 *
 * 对齐 Pixiv-Shaft `ceui/loxia/Client.kt` + `ceui/lisa/http/Retro.java`。
 *
 * OAuth 部分完全使用 pixiv-login 库（com.github.SoxiaLiSA:pixiv-login）：
 * - 密钥（clientId / clientSecret / 签名密钥）取自 [PixivOAuthConfig.PIXIV_ANDROID]
 * - PKCE 登录、token 交换/刷新由 [PixivOAuthClient] 处理
 *
 * 构建顺序：PixivOAuthClient → AuthRefresher → AppApi / WebApi → PixivClientBundle
 */
object PixivClient {

    fun build(
        session: SessionManager,
        config: PixivOAuthConfig = PixivOAuthConfig.PIXIV_ANDROID,
        verifierStore: VerifierStore,
        debug: Boolean = false,
        onInvalidRefreshToken: () -> Unit = {},
    ): PixivClientBundle {
        // 1. OAuth 客户端（pixiv-login 库；独立 OkHttp，不参与业务签名/刷新）
        val oauthClient = PixivOAuthClient(
            config = config,
            logHttp = debug,
            verifierStore = verifierStore,
        )

        // 2. Token 刷新器（委托库的 refreshTokenSuspend）
        val refresher = AuthRefresher(
            oauthClient = oauthClient,
            session = session,
            onInvalidRefreshToken = onInvalidRefreshToken,
        )

        // 3. App API（签名头 + token 自动刷新）
        val appOkHttp = baseClient()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .addInterceptor(HeaderInterceptor(session))
            .addInterceptor(TokenInterceptor(session, refresher))
            .apply { if (debug) addLogging() }
            .build()

        // 4. Web API（Cookie 鉴权）
        val webOkHttp = baseClient()
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(WebHeaderInterceptor(session::cookie))
            .apply { if (debug) addLogging() }
            .build()

        // 5. 图片专用 client（i.pximg.net 需 Referer）
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(PixivConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
            .addInterceptor(ImageInterceptor())
            .build()

        return PixivClientBundle(
            appApi = retrofit(PixivConstants.APP_API_HOST, appOkHttp).create(AppApi::class.java),
            webApi = retrofit(PixivConstants.WEB_API_HOST, webOkHttp).create(PixivWebApi::class.java),
            oauthClient = oauthClient,
            refresher = refresher,
            imageClient = imageClient,
        )
    }

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(PixivConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PixivConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(PixivConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private fun OkHttpClient.Builder.addLogging() {
        addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
    }
}

/** 构建结果 */
data class PixivClientBundle(
    val appApi: AppApi,
    val webApi: PixivWebApi,
    val oauthClient: PixivOAuthClient,
    val refresher: AuthRefresher,
    val imageClient: OkHttpClient,
)
