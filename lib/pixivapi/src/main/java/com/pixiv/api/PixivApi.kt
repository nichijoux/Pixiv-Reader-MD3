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
 * Pixiv API 顶层入口（门面）
 *
 * 一次性构建所有网络组件。OAuth 密钥（clientId / clientSecret / 签名密钥）
 * 默认取自 pixiv-login 库（com.github.SoxiaLiSA:pixiv-login）的
 * `PixivOAuthConfig.PIXIV_ANDROID`，**无需手动填写**：
 * ```
 * val pixiv = PixivApi.create(
 *     session = sessionManager,       // 你的 SessionManager 实现
 *     verifierStore = yourStore,      // pixiv-login 库的 VerifierStore
 *     debug = BuildConfig.DEBUG,
 * )
 *
 * // 登录
 * val url = pixiv.oauth.startLoginUrl()
 * // 回调
 * when (val r = pixiv.oauth.handleCallback(uri)) {
 *     is PixivAuthResult.Success -> pixiv.session.saveSession(r.account)
 *     else -> ...
 * }
 *
 * // 请求
 * val illust = pixiv.api.getIllust(illustId)
 * val ranking = pixiv.api.getRanking(PixivConstants.RANK_DAY)
 * ```
 */
class PixivApi(
    val session: SessionManager,
    val api: AppApi,
    val webApi: PixivWebApi,
    val oauth: PixivOAuth,
    /** pixiv-login 库的原始 OAuth 客户端（高级用，通常不需要） */
    val oauthClient: PixivOAuthClient,
    val refresher: AuthRefresher,
    val imageClient: OkHttpClient,
) {
    companion object {

        /**
         * 创建 Pixiv API 实例
         *
         * 构建顺序：PixivOAuthClient → AuthRefresher → AppApi/WebApi（依赖 refresher）→ PixivApi。
         *
         * @param session       会话管理（建议启动时调用 restore() 恢复登录态）
         * @param verifierStore PKCE verifier 存储（pixiv-login 库的 VerifierStore，
         *                      建议用 MMKV/SharedPreferences 持久化以应对进程被杀）
         * @param config        OAuth 客户端配置，默认 pixiv-login 库的
         *                      [PixivOAuthConfig.PIXIV_ANDROID]（clientId / clientSecret
         *                      / 签名密钥均来自该库）
         * @param debug         是否打印请求日志
         * @param onInvalidRefreshToken refresh_token 被吊销回调（可弹窗提示重新登录）
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
