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
 * Token 刷新器：密钥/签名/PKCE 全由 pixiv-login 库处理，这里只加锁避免并发 401 重复刷新。
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

    /** 供 OkHttp 拦截器使用：intercept() 是阻塞方法，不能直接调 suspend。 */
    fun refreshAndGetBearerBlocking(tokenForThisRequest: String?): String? =
        runBlocking { refreshAndGetBearer(tokenForThisRequest) }
}

/**
 * PKCE 登录门面：clientId / clientSecret / 回调 scheme 全取自 pixiv-login 库的
 * `PixivOAuthConfig.PIXIV_ANDROID`，不要硬编码。
 */
class PixivOAuth(
    private val oauthClient: PixivOAuthClient,
) {

    fun startLoginUrl(): String = oauthClient.startLogin()

    fun startProvisionalAccountUrl(): String = oauthClient.startProvisionalAccount()

    fun isOAuthCallback(uri: Uri): Boolean = oauthClient.isOAuthCallback(uri)

    /** 库只给 rawBody，需反序列化成本库 [AccountResponse] 才能交给 SessionManager。 */
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
