package com.pixiv.reader.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pixiv.reader.app.actions.AppCardActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PixivApp : Application(), Configuration.Provider, ImageLoaderFactory {

    // 懒注入网络仓库，避免冷启动 Application.onCreate 阶段立即构造多组 OkHttpClient 与 Retrofit
    @Inject
    lateinit var pixivRepository: Lazy<PixivRepository>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appCardActions: AppCardActions

    @Inject
    lateinit var downloadCompletionNotifier: DownloadCompletionNotifier

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // 旧版离线残留目录移到后台 IO 线程清理，避免阻塞冷启动主线程
        appScope.launch(Dispatchers.IO) {
            runCatching { File(filesDir, "offline").deleteRecursively() }
        }
        appCardActions.start(appScope)
        downloadCompletionNotifier.start(appScope)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { pixivRepository.get().imageClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            // 无透明通道缩略图优先 RGB_565 解码（内存占用减半）；pximg.net 静态资源 URL 不可变，忽略缓存头提高磁盘复用率；
            // 关闭全局 crossfade 避免瀑布流高速滑动时全屏多图同时 alpha 混合加重 GPU 负担。
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
