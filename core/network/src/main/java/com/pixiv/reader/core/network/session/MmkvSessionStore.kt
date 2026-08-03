package com.pixiv.reader.core.network.session

import com.example.pixivapi.auth.SessionStore
import com.example.pixivapi.model.AccountResponse
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

/**
 * 基于 MMKV 的会话持久化实现。
 * 存 access_token / refresh_token / user（Gson 序列化），以及网页 Cookie。
 */
class MmkvSessionStore(private val mmkv: MMKV) : SessionStore {

    private val gson = Gson()

    override fun loadAccount(): AccountResponse? {
        val json = mmkv.decodeString(KEY_ACCOUNT) ?: return null
        return runCatching { gson.fromJson(json, AccountResponse::class.java) }.getOrNull()
    }

    override fun saveAccount(account: AccountResponse) {
        mmkv.encode(KEY_ACCOUNT, gson.toJson(account))
    }

    override fun getCookie(): String = mmkv.decodeString(KEY_COOKIE).orEmpty()

    override fun clear() {
        mmkv.removeValueForKey(KEY_ACCOUNT)
        mmkv.removeValueForKey(KEY_COOKIE)
    }

    companion object {
        private const val KEY_ACCOUNT = "session_account"
        private const val KEY_COOKIE = "web_cookie"
    }
}
