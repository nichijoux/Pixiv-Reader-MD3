package com.pixiv.reader.core.network.fanbox

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.pixiv.api.network.FanboxApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * FANBOX 网络装配：独立 OkHttpClient（只挂 [FanboxHeaderInterceptor]，不挂 pixiv 的
 * Bearer / 签名拦截器——FANBOX 是 cookie 体系）+ Retrofit + [FanboxApi]。
 * 无屏 WebView 桥与仓库经 `@Inject` 构造注入，无需在此 @Provides。
 */
@Module
@InstallIn(SingletonComponent::class)
object FanboxModule {

    /**
     * 提供 FANBOX Retrofit 接口。
     *
     * @return 进程单例 [FanboxApi]
     */
    @Provides
    @Singleton
    fun provideFanboxApi(): FanboxApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(FanboxHeaderInterceptor())
            .build()
        return Retrofit.Builder()
            .baseUrl(FanboxRepository.FANBOX_API_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FanboxApi::class.java)
    }

    private const val TIMEOUT_SECONDS = 10L
}
