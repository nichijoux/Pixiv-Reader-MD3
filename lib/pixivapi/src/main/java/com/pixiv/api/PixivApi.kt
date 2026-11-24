package com.pixiv.api

import ceui.pixiv.login.PixivOAuthClient
import ceui.pixiv.login.PixivOAuthConfig
import ceui.pixiv.login.VerifierStore
import com.pixiv.api.network.AppApi
import com.pixiv.api.network.PixivWebApi
import com.pixiv.api.auth.AuthRefresher
import com.pixiv.api.auth.PixivOAuth
import com.pixiv.api.auth.SessionManager
import com.pixiv.api.network.PixivClient
import okhttp3.OkHttpClient

/**
 * API 顶层门面：一次性构建所有网络组件。OAuth 密钥（clientId / clientSecret / 签名密钥）
 * 默认取自 pixiv-login 库的 `PixivOAuthConfig.PIXIV_ANDROID`，无需手动填写。
 */
class PixivApi(
    val session: SessionManager,
    val api: AppApi,
    val webApi: PixivWebApi,
    val oauth: PixivOAuth,
    /** pixiv-login 库的原始 OAuth 客户端，通常不需要。 */
    val oauthClient: PixivOAuthClient,
    val refresher: AuthRefresher,
    val imageClient: OkHttpClient,
) {
    companion object {

        /**
         * [verifierStore] 必须持久化（MMKV / SharedPreferences）：进程被杀后 OAuth 回调仍要能校验 PKCE。
         */
        fun create(
            session: SessionManager,
            verifierStore: VerifierStore,
            config: PixivOAuthConfig = PixivOAuthConfig.PIXIV_ANDROID,
            debug: Boolean = false,
            onInvalidRefreshToken: () -> Unit = {},
        ): PixivApi {
            val bundle = PixivClient.build(
                session = session,
                config = config,
                verifierStore = verifierStore,
                debug = debug,
                onInvalidRefreshToken = onInvalidRefreshToken,
            )
            return PixivApi(
                session = session,
                api = bundle.appApi,
                webApi = bundle.webApi,
                oauth = PixivOAuth(bundle.oauthClient),
                oauthClient = bundle.oauthClient,
                refresher = bundle.refresher,
                imageClient = bundle.imageClient,
            )
        }
    }
}
