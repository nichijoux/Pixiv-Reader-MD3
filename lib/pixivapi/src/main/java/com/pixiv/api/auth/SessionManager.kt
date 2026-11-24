package com.pixiv.api.auth

import com.pixiv.api.model.AccountResponse

interface SessionManager {

    val isLoggedIn: Boolean

    val loggedInUid: Long

    val currentUser: AccountResponse?

    val isPremium: Boolean

    /** 不含 "Bearer " 前缀；未登录抛异常。 */
    fun accessToken(): String

    /** 未登录返回空串：拦截器依赖它而不是抛异常。 */
    fun bearerTokenOrEmpty(): String

    fun refreshToken(): String?

    /** 网页 Cookie（PHPSESSID 等，供 Web API / FANBOX 使用） */
    fun cookie(): String

    fun saveSession(account: AccountResponse)

    /** 刷新后调用：只换 token，保留 user 元数据。 */
    fun applyTokenRefresh(accessToken: String, refreshToken: String, expiresIn: Int)

    fun logout()
}

/**
 * 默认内存实现。会话会被 OkHttp 拦截器线程（刷新 token）与主线程/协程并发读写，
 * 读写必须同时持 [lock] 且 [account] 加 `@Volatile`，否则会出现「刷新结果晚到、
 * 把刚吐销的账号覆写回来」的复活竞态。
 */
open class SimpleSessionManager(
    private val store: SessionStore,
) : SessionManager {

    private val lock = Any()

    @Volatile
    private var account: AccountResponse? = null

    override val isLoggedIn: Boolean get() = account?.access_token != null

    override val loggedInUid: Long get() = account?.user?.id ?: 0L

    override val currentUser: AccountResponse? get() = account

    override val isPremium: Boolean get() = account?.user?.is_premium == true

    override fun accessToken(): String =
        account?.access_token ?: throw IllegalStateException("not logged in")

    override fun bearerTokenOrEmpty(): String =
        runCatching { "Bearer " + accessToken() }.getOrDefault("")

    override fun refreshToken(): String? = account?.refresh_token

    override fun cookie(): String = store.getCookie()

    override fun saveSession(account: AccountResponse) {
        synchronized(lock) {
            this.account = account
            store.saveAccount(account)
        }
    }

    override fun applyTokenRefresh(accessToken: String, refreshToken: String, expiresIn: Int) {
        synchronized(lock) {
            // 已登出（并发 logout）时丢弃刷新结果：否则会用空 user 重建会话，
            // 「复活」刚被吐销的账号并让 UI 卡在已登录
            val current = account ?: return
            val updated = current.copy(
                access_token = accessToken,
                refresh_token = refreshToken,
                expires_in = expiresIn,
            )
            account = updated
            store.saveAccount(updated)
        }
    }

    override fun logout() {
        synchronized(lock) {
            account = null
            store.clear()
        }
    }

    fun restore() {
        synchronized(lock) {
            account = store.loadAccount()
        }
    }
}

/** 持久化存储抽象 */
interface SessionStore {
    fun loadAccount(): AccountResponse?
    fun saveAccount(account: AccountResponse)
    fun getCookie(): String
    fun clear()
}
