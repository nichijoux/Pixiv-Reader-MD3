package com.pixiv.api.auth

import android.net.Uri
import ceui.pixiv.login.PixivOAuthClient
import ceui.pixiv.login.PixivOAuthResult
import com.pixiv.api.model.AccountResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * OAuth 刷新器：负责 token 刷新与请求重放
 *
 * 完全委托给 pixiv-login 库（com.github.SoxiaLiSA:pixiv-login）的
 * [PixivOAuthClient.refreshTokenSuspend] —— 密钥、签名、PKCE 均由库处理。
 * 对齐 Shaft `TokenInterceptor.getNewToken` 的同步锁逻辑：
 * - 若其它线程已刷新过（缓存 token ≠ 本请求头 token），直接复用
 * - 否则用 refresh_token 换新 token
 */
class AuthRefresher(
    private val oauthClient: PixivOAuthClient,
    private val session: SessionManager,
    private val onInvalidRefreshToken: () -> Unit = {},
) {

    private val mutex = Mutex()

    /** 返回 "Bearer xxx"，失败返回 null */
    suspend fun refreshAndGetBearer(tokenForThisRequest: String?): String? = mutex.withLock {
        val current = session.bearerTokenOrEmpty()
        // 已被别的线程刷新过
        if (current.isNotEmpty() && current != tokenForThisRequest) {
            return current
        }
        val refreshToken = session.refreshToken() ?: return null
        when (val result = oauthClient.refreshTokenSuspend(refreshToken)) {
            is PixivOAuthResult.Success -> {
                val r = result.response
                session.applyTokenRefresh(
                    accessToken = r.accessToken,
                    refreshToken = r.refreshToken,
                    expiresIn = r.expiresIn,
                )
                "Bearer ${r.accessToken}"
            }
            is PixivOAuthResult.Failure -> {
                if (result is PixivOAuthResult.Failure.ServerRejected &&
                    result.httpCode == 400 &&
                    result.message.contains("Invalid refresh token")
                ) {
                    session.logout()
                    onInvalidRefreshToken()
                }
                null
            }
        }
    }

    /**
     * 阻塞版刷新（供 OkHttp 拦截器使用）。
     * 拦截器的 intercept() 是阻塞方法，不能直接调用 suspend 函数。
     */
    fun refreshAndGetBearerBlocking(tokenForThisRequest: String?): String? =
        runBlocking { refreshAndGetBearer(tokenForThisRequest) }
}

/**
 * Pixiv OAuth PKCE 登录门面
 *
 * 完全委托给 pixiv-login 库（com.github.SoxiaLiSA:pixiv-login）的 [PixivOAuthClient]：
 * clientId / clientSecret / 登录 URL / 回调 scheme 全部取自库的
 * `PixivOAuthConfig.PIXIV_ANDROID`，无需也不应再硬编码。
 *
 * 流程：
 * 1. [startLoginUrl] → 打开 Chrome Custom Tab
 * 2. 回调（pixiv://account/login?code=…）→ [isOAuthCallback] 判断 → [handleCallback] 换 token
 */
class PixivOAuth(
    private val oauthClient: PixivOAuthClient,
) {

    /** 生成登录 URL（库内部完成 PKCE） */
    fun startLoginUrl(): String = oauthClient.startLogin()

    /** 生成临时账号创建 URL（未注册浏览） */
    fun startProvisionalAccountUrl(): String = oauthClient.startProvisionalAccount()

    /** 是否为 OAuth 回调 URI（scheme 匹配 callbackScheme，如 "pixiv"） */
    fun isOAuthCallback(uri: Uri): Boolean = oauthClient.isOAuthCallback(uri)

    /**
     * 处理 OAuth 回调并换取 token。
     * 库返回的 [PixivOAuthResult.Success.rawBody] 用 Gson 反序列化为
     * 本库的 [AccountResponse]（含完整 user 信息），可直接交给 SessionManager。
     */
    suspend fun handleCallback(uri: Uri): PixivAuthResult = withContext(Dispatchers.IO) {
        when (val result = oauthClient.handleCallback(uri)) {
            is PixivOAuthResult.Success -> {
                val account = runCatching {
                    Gson().fromJson(result.rawBody, AccountResponse::class.java)
                }.getOrNull()
                if (account?.access_token != null) {
                    PixivAuthResult.Success(account)
                } else {
                    PixivAuthResult.Failure.ServerRejected(400, "parse rawBody failed", null)
                }
            }
            is PixivOAuthResult.Failure -> when (result) {
                is PixivOAuthResult.Failure.MissingCode ->
                    PixivAuthResult.Failure.MissingCode
                is PixivOAuthResult.Failure.MissingVerifier ->
                    PixivAuthResult.Failure.MissingVerifier
                is PixivOAuthResult.Failure.ServerRejected ->
                    PixivAuthResult.Failure.ServerRejected(result.httpCode, result.message, result.cause)
                is PixivOAuthResult.Failure.NetworkError ->
                    PixivAuthResult.Failure.NetworkError(result.cause ?: RuntimeException(result.message))
            }
        }
    }
}

/** 登录结果（门面类型；内部映射自库的 [PixivOAuthResult]） */
sealed interface PixivAuthResult {
    data class Success(val account: AccountResponse) : PixivAuthResult

    sealed interface Failure : PixivAuthResult {
        data object MissingCode : Failure
        data object MissingVerifier : Failure
        data class ServerRejected(
            val httpCode: Int,
            val message: String,
            val cause: Throwable?,
        ) : Failure
        data class NetworkError(val cause: Throwable) : Failure
    }
}
