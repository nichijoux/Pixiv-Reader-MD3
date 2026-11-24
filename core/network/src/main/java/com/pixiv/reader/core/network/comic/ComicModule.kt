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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComicHttpClient

/** COMIC 独立网络装配：不挂 pixiv OAuth/签名拦截器，读超时放宽至 30s 以兼顾正文整页图片下载。 */
@Module
@InstallIn(SingletonComponent::class)
object ComicModule {

    @Provides
    @Singleton
    @ComicHttpClient
    fun provideComicOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // 逐跳按实际目标注入站点头，避免自动重定向将 Cookie 转发到其他域名
        .addNetworkInterceptor(ComicHeaderInterceptor())
        .build()

    @Provides
    @Singleton
    fun provideComicApi(@ComicHttpClient client: OkHttpClient): ComicApi = Retrofit.Builder()
        .baseUrl(ComicHeaderInterceptor.COMIC_API_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ComicApi::class.java)

    @Provides
    @Singleton
    fun provideComicRepository(comicApi: ComicApi, @ComicHttpClient client: OkHttpClient): ComicRepository {
        // 显式绑定以使用 @ComicHttpClient，避免构造注入要求全局无限定客户端
        return ComicRepository(comicApi, client)
    }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
