package com.pixiv.reader.core.network.session

import ceui.pixiv.login.VerifierStore
import com.tencent.mmkv.MMKV

/**
 * PKCE verifier 持久化（应对登录页跳转期间进程被杀）。
 */
class MmkvVerifierStore(private val mmkv: MMKV) : VerifierStore {

    override fun save(verifier: String) {
        mmkv.encode(KEY_VERIFIER, verifier)
    }

    override fun load(): String? = mmkv.decodeString(KEY_VERIFIER)

    override fun clear() {
        mmkv.removeValueForKey(KEY_VERIFIER)
    }

    companion object {
        private const val KEY_VERIFIER = "pkce_verifier"
    }
}
