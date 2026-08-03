package com.pixiv.reader.core.network.di

import android.content.Context
import ceui.pixiv.login.PixivOAuthConfig
import ceui.pixiv.login.VerifierStore
import com.example.pixivapi.PixivApi
import com.example.pixivapi.auth.SessionManager
import com.example.pixivapi.auth.SessionStore
import com.example.pixivapi.auth.SimpleSessionManager
import com.pixiv.reader.core.network.session.MmkvSessionStore
import com.pixiv.reader.core.network.session.MmkvVerifierStore
import com.tencent.mmkv.MMKV
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 网络层依赖装配：MMKV → 会话存储 → PixivApi（OAuth 密钥来自 pixiv-login 库）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMmkv(@ApplicationContext context: Context): MMKV {
        MMKV.initialize(context)
        return MMKV.defaultMMKV()
    }

    @Provides
    @Singleton
    fun provideSessionStore(mmkv: MMKV): SessionStore = MmkvSessionStore(mmkv)

    @Provides
    @Singleton
    fun provideSessionManager(store: SessionStore): SessionManager =
        SimpleSessionManager(store).also { it.restore() }

    @Provides
    @Singleton
    fun provideVerifierStore(mmkv: MMKV): VerifierStore = MmkvVerifierStore(mmkv)

    @Provides
    @Singleton
    fun providePixivApi(
        session: SessionManager,
        verifierStore: VerifierStore,
    ): PixivApi = PixivApi.create(
        session = session,
        verifierStore = verifierStore,
        config = PixivOAuthConfig.PIXIV_ANDROID,
        debug = false,
    )
}
