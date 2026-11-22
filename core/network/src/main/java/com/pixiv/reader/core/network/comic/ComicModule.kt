package com.pixiv.reader.core.network.comic

import com.pixiv.api.network.ComicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 区分 COMIC 专用 HTTP 客户端与其他网络通道。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComicHttpClient

/**
 * pixiv COMIC 网络装配：独立 OkHttpClient（只挂 [ComicHeaderInterceptor]，不挂
 * pixiv 的 Bearer / 签名拦截器——COMIC 公开内容无需登录，与 FANBOX 的 cookie 体系
 * 同为独立通道；读超时放宽到 30s，因该 client 同时承担正文整页图片下载）+ Retrofit +
 * [ComicApi]。仓库与页面加载器共享此 client。
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
    @ComicHttpClient
    fun provideComicOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 逐跳根据实际目标添加站点头，避免自动重定向将 Cookie 转发到其他域名。
        .addNetworkInterceptor(ComicHeaderInterceptor())
        .build()

    /**
     * 提供 COMIC Retrofit 接口。
     *
     * @param client 上方提供的专用 client
     * @return 进程单例 [ComicApi]
     */
    @Provides
    @Singleton
    fun provideComicApi(@ComicHttpClient client: OkHttpClient): ComicApi = Retrofit.Builder()
        .baseUrl(ComicHeaderInterceptor.COMIC_API_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ComicApi::class.java)

    /**
     * 装配同时访问 COMIC API 与 viewer HTML 的仓库。
     *
     * @param comicApi COMIC Retrofit 接口
     * @param client 专用 HTTP 客户端
     * @return 进程单例仓库
     */
    @Provides
    @Singleton
    fun provideComicRepository(comicApi: ComicApi, @ComicHttpClient client: OkHttpClient): ComicRepository {
        // 显式绑定仓库，避免其既有构造参数要求全局的无限定客户端。
        return ComicRepository(comicApi, client)
    }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
