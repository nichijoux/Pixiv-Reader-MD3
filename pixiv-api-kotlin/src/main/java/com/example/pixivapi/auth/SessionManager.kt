package com.example.pixivapi.auth

import com.example.pixivapi.model.AccountResponse

/**
 * 会话管理抽象接口
 *
 * 新应用可基于 SharedPreferences / DataStore / MMKV 实现。
 * 会话持有 access_token / refresh_token / user 信息。
 */
interface SessionManager {

    /** 是否已登录 */
    val isLoggedIn: Boolean

    /** 当前登录用户 UID（未登录返回 0） */
    val loggedInUid: Long

    /** 当前用户 */
    val currentUser: AccountResponse?

    /** 是否 Premium 会员 */
    val isPremium: Boolean

    /** access_token（不含 "Bearer " 前缀），未登录抛异常 */
    fun accessToken(): String

    /** 返回 "Bearer xxx"，未登录返回空串（避免拦截器抛异常） */
    fun bearerTokenOrEmpty(): String

    /** refresh_token */
    fun refreshToken(): String?

    /** 网页 Cookie（PHPSESSID 等，用于 Web API） */
    fun cookie(): String

    /** 保存登录结果 */
    fun saveSession(account: AccountResponse)

    /** 更新 tokens（刷新后调用，保留 user 元数据） */
    fun applyTokenRefresh(accessToken: String, refreshToken: String, expiresIn: Int)

    /** 登出 */
    fun logout()
}

/**
 * 默认内存实现（配合外部持久化回调）
 */
open class SimpleSessionManager(
    private val store: SessionStore,
) : SessionManager {

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
        this.account = account
        store.saveAccount(account)
    }

    override fun applyTokenRefresh(accessToken: String, refreshToken: String, expiresIn: Int) {
        val updated = (account ?: AccountResponse()).copy(
            access_token = accessToken,
            refresh_token = refreshToken,
            expires_in = expiresIn,
        )
        this.account = updated
        store.saveAccount(updated)
    }

    override fun logout() {
        account = null
        store.clear()
    }

    /** 启动时恢复会话 */
    fun restore() {
        account = store.loadAccount()
    }
}

/** 持久化存储抽象 */
interface SessionStore {
    fun loadAccount(): AccountResponse?
    fun saveAccount(account: AccountResponse)
    fun getCookie(): String
    fun clear()
}
