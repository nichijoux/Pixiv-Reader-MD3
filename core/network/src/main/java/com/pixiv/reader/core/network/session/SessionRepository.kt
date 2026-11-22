package com.pixiv.reader.core.network.session

import android.net.Uri
import android.webkit.CookieManager
import com.pixiv.api.PixivApi
import com.pixiv.api.auth.PixivAuthResult
import com.pixiv.api.auth.SessionManager
import com.pixiv.api.model.AccountResponse
import com.pixiv.reader.core.network.fanbox.FanboxWebBridge
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.feed.FeedSnapshotStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val sessionEvents: SessionEvents,
    private val favoriteActions: FavoriteActions,
    private val fanboxWebBridge: FanboxWebBridge,
    private val feedSnapshotStore: FeedSnapshotStore,
) {

    private val _isLoggedIn = MutableStateFlow(pixivApi.session.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /** 会话失效事件的收集作用域：与单例同生命周期（进程级），不随页面销毁取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            sessionEvents.invalidations.collect {
                // 拦截器已清空底层会话，同步清理登录态与会话派生缓存，避免跨账号残留。
                val loggedIn = pixivApi.session.isLoggedIn
                _isLoggedIn.value = loggedIn
                if (!loggedIn) {
                    clearSessionState()
                }
            }
        }
    }

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
        // 回调到达 = 浏览器侧 pixiv 登录态已确立（生态入口 SSO 完成）
        markWebSsoCompleted()
    }

    /** 丢弃待处理回调（code 去重场景） */
    fun clearPendingCallback() {
        _pendingOAuthUri.value = null
    }

    /**
     * 浏览器 SSO 完成标记（会话级）：生态入口（FANBOX/COMIC/私信）首次点击时先在
     * 系统浏览器打开 OAuth 授权页完成 pixiv 网页登录，回调唤起本 app 时置位——
     * 此后同一浏览器的 pixiv 网页访问均为登录态，入口点击可直接打开目标网页。
     * 单例跨线程读写，用 @Volatile 保证可见性。
     */
    @Volatile
    var webSsoCompleted: Boolean = false
        private set

    /** pixiv 网页登录授权 URL（系统浏览器打开；登录/SSO 后 302 回 pixiv:// 唤起本 app） */
    fun webLoginUrl(): String = pixivApi.oauth.startLoginUrl()

    /** 消费 OAuth 回调（无论是否完成 token 交换），浏览器侧 pixiv 登录态已确立 */
    fun markWebSsoCompleted() {
        webSsoCompleted = true
    }

    /** 消费回调并换取 token；成功后更新登录态并清理旧会话覆盖状态 */
    suspend fun processLogin(uri: Uri): PixivAuthResult {
        return when (val result = pixivApi.oauth.handleCallback(uri)) {
            is PixivAuthResult.Success -> {
                favoriteActions.clearSessionOverrides()
                feedSnapshotStore.clearAll()
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

    /** 主动登出：清会话、WebView Cookie 与会话缓存，并同步登录态（MainShell 回调链触发）。 */
    fun logout() {
        pixivApi.session.logout()
        _isLoggedIn.value = false
        clearSessionState()
        // FANBOX / COMIC 网页侧用的是 WebView Cookie 体系，与 OAuth token 独立：
        // 不清 Cookie 会导致切换账号后仍沿用上一个账号的网页身份
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }

    /**
     * 清理依赖当前账号会话的内存覆盖状态、无屏 WebView 壳页及首页快照。
     *
     * @return 无返回值
     */
    private fun clearSessionState() {
        webSsoCompleted = false
        favoriteActions.clearSessionOverrides()
        fanboxWebBridge.release()
        scope.launch { feedSnapshotStore.clearAll() }
    }
}
