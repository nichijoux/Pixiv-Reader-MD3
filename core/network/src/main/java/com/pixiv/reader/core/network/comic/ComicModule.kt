package com.pixiv.reader.core.network.comic

import com.pixiv.api.network.ComicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * pixiv COMIC 网络装配：独立 OkHttpClient（只挂 [ComicHeaderInterceptor]，不挂
 * pixiv 的 Bearer / 签名拦截器——COMIC 公开内容无需登录，与 FANBOX 的 cookie 体系
 * 同为独立通道；读超时放宽到 30s，因该 client 同时承担正文整页图片下载）+ Retrofit +
 * [ComicApi]。仓库 / 页面加载器经 `@Inject` 构造注入，共享此 client。
 */
@Module
@InstallIn(SingletonComponent::class)
object ComicModule {

    /**
     * 提供 COMIC 专用 OkHttpClient（viewer HTML / JSON API / 正文图片共用）。
     *
     * @return 进程单例 client
     */
    @Provides
    @Singleton
    fun provideComicOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(ComicHeaderInterceptor())
        .build()

    /**
     * 提供 COMIC Retrofit 接口。
     *
     * @param client 上方提供的专用 client
     * @return 进程单例 [ComicApi]
     */
    @Provides
    @Singleton
    fun provideComicApi(client: OkHttpClient): ComicApi = Retrofit.Builder()
        .baseUrl(ComicHeaderInterceptor.COMIC_API_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ComicApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
