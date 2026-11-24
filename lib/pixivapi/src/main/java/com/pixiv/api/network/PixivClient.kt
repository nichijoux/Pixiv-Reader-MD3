package com.pixiv.api.network

import ceui.pixiv.login.PixivOAuthClient
import ceui.pixiv.login.PixivOAuthConfig
import ceui.pixiv.login.VerifierStore
import com.pixiv.api.PixivConstants
import com.pixiv.api.network.AppApi
import com.pixiv.api.network.PixivWebApi
import com.pixiv.api.auth.AuthRefresher
import com.pixiv.api.auth.SessionManager
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络客户端构建器：OAuth 密钥与 PKCE 均由 pixiv-login 库处理，不在此硬编码。
 */
object PixivClient {

    fun build(
        session: SessionManager,
        config: PixivOAuthConfig = PixivOAuthConfig.PIXIV_ANDROID,
        verifierStore: VerifierStore,
        debug: Boolean = false,
        onInvalidRefreshToken: () -> Unit = {},
    ): PixivClientBundle {
        // 独立 OkHttp，不参与业务签名/刷新
        val oauthClient = PixivOAuthClient(
            config = config,
            logHttp = debug,
            verifierStore = verifierStore,
        )

        val refresher = AuthRefresher(
            oauthClient = oauthClient,
            session = session,
            onInvalidRefreshToken = onInvalidRefreshToken,
        )

        val appOkHttp = baseClient()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .addInterceptor(HeaderInterceptor(session))
            .addInterceptor(TokenInterceptor(session, refresher, onInvalidRefreshToken))
            .apply { if (debug) addLogging() }
            .build()

        val webOkHttp = baseClient()
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor(WebHeaderInterceptor(session::cookie))
            .apply { if (debug) addLogging() }
            .build()

        // Dispatcher 默认单主机 5 并发：瀑布流每卡含封面+头像且详情页主图与相关图同主机并发，
        // 放宽到 12 并发并配齐 16 连接复用池，避免封面与小头像互相排队或频繁重做 TLS 握手。
        val imageClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 12 })
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
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

data class PixivClientBundle(
    val appApi: AppApi,
    val webApi: PixivWebApi,
    val oauthClient: PixivOAuthClient,
    val refresher: AuthRefresher,
    val imageClient: OkHttpClient,
)
