package com.pixiv.reader.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

/**
 * 应用入口。Hilt 装配网络层 + WorkManager Worker 工厂；
 * 同时作为 Coil 的默认 ImageLoader 工厂（注入 Pixiv 图片专用 OkHttpClient，带 Referer）。
 */
@HiltAndroidApp
class PixivApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var pixivRepository: PixivRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // 一次性清理旧版「离线下载」缓存目录（离线功能已移除，老数据不再使用）
        runCatching { File(filesDir, "offline").deleteRecursively() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(pixivRepository.imageClient)
            .crossfade(true)
            .build()
}
