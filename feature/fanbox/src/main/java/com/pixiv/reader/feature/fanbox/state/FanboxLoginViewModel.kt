package com.pixiv.reader.feature.fanbox.state

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.pixiv.api.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * FANBOX 内嵌登录 ViewModel：监控 WebView 侧 CookieManager 中的 FANBOX 登录态，
 * 登录成功（cookie 含 FANBOX_SESSIONID）后把 FANBOX 域 cookie 持久化到会话存储，
 * 供 FanboxHeaderInterceptor 鉴权使用。
 */
@HiltViewModel
class FanboxLoginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    /**
     * 检查 FANBOX 域 cookie 是否已登录；已登录则抓取并持久化。
     *
     * @return true = 登录成功并已保存 cookie
     */
    fun checkAndSave(): Boolean {
        val cookie = CookieManager.getInstance().getCookie("https://fanbox.cc").orEmpty()
        if (!cookie.contains("FANBOX_SESSIONID")) return false
        CookieManager.getInstance().flush()
        sessionManager.setFanboxCookie(cookie)
        return true
    }

    /** 是否已存在可用登录态（跳过登录页直接进入）。 */
    fun hasSession(): Boolean = sessionManager.fanboxCookie().contains("FANBOX_SESSIONID")

    /** 轮询间隔（ms）。 */
    suspend fun waitNextPoll() = delay(1500)
}
