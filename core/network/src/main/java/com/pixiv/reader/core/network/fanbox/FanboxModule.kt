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

/** FANBOX 独立网络装配：走 Cookie 体系，不挂 pixiv OAuth/签名拦截器。 */
@Module
@InstallIn(SingletonComponent::class)
object FanboxModule {

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
