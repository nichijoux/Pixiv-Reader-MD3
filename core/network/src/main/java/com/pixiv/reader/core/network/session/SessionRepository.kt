package com.pixiv.reader.core.network.session

import android.net.Uri
import com.example.pixivapi.PixivApi
import com.example.pixivapi.auth.PixivAuthResult
import com.example.pixivapi.auth.SessionManager
import com.example.pixivapi.model.AccountResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话仓库：登录态可观察化 + OAuth 回调桥接。
 *
 * - [isLoggedIn]：UI 可观察的登录态
 * - [pendingOAuthUri]：MainActivity 收到 `pixiv://account/login` 回调后写入，
 *   Auth 页 ViewModel 消费并调 [processLogin] 完成 token 交换
 * - 登出/刷新失败（Invalid refresh token）统一收敛到这里
 */
@Singleton
class SessionRepository @Inject constructor(
    private val pixivApi: PixivApi,
) {

    private val _isLoggedIn = MutableStateFlow(pixivApi.session.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _pendingOAuthUri = MutableStateFlow<Uri?>(null)
    val pendingOAuthUri: StateFlow<Uri?> = _pendingOAuthUri.asStateFlow()

    val session: SessionManager get() = pixivApi.session

    val currentUser: AccountResponse? get() = pixivApi.session.currentUser

    /** 打开登录页 URL（库内部完成 PKCE，verifier 已持久化到 MMKV） */
    fun buildLoginUrl(): String = pixivApi.oauth.startLoginUrl()

    /** 临时账号注册 URL */
    fun buildProvisionalAccountUrl(): String = pixivApi.oauth.startProvisionalAccountUrl()

    /** 是否为 OAuth 回调 scheme（pixiv://account/login）。 */
    fun isOAuthCallback(uri: Uri): Boolean = pixivApi.oauth.isOAuthCallback(uri)

    /** MainActivity 收到深链回调时调用（onCreate / onNewIntent） */
    fun onOAuthCallback(uri: Uri) {
        _pendingOAuthUri.value = uri
    }

    /** 丢弃待处理回调（code 去重场景） */
    fun clearPendingCallback() {
        _pendingOAuthUri.value = null
    }

    /** 消费回调并换取 token；成功后更新登录态 */
    suspend fun processLogin(uri: Uri): PixivAuthResult {
        return when (val result = pixivApi.oauth.handleCallback(uri)) {
            is PixivAuthResult.Success -> {
                pixivApi.session.saveSession(result.account)
                _isLoggedIn.value = true
                _pendingOAuthUri.value = null
                result
            }
            is PixivAuthResult.Failure -> {
                _pendingOAuthUri.value = null
                result
            }
        }
    }

    /** 主动登出：清会话并同步登录态（MainShell 回调链触发）。 */
    fun logout() {
        pixivApi.session.logout()
        _isLoggedIn.value = false
    }

    /** 会话被服务端吊销（Invalid refresh token）时由回调触发 */
    fun forceLogout() = logout()
}
