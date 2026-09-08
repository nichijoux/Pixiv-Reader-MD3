package com.pixiv.reader.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pixiv.reader.app.actions.AppCardActions
import com.pixiv.reader.core.network.action.OfflineActionQueue
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /** 卡片本地动作（稍后再看 / 就地屏蔽）：进程单例，App 创建时启动数据流同步。 */
    @Inject
    lateinit var appCardActions: AppCardActions

    /** 离线操作队列（断网收藏/关注/追更暂存补发）：App 创建时启动上线沿监听。 */
    @Inject
    lateinit var offlineActionQueue: OfflineActionQueue

    override fun onCreate() {
        super.onCreate()
        // 一次性清理旧版「离线下载」缓存目录（离线功能已移除，老数据不再使用）
        runCatching { File(filesDir, "offline").deleteRecursively() }
        // 进程级作用域（Application 生命周期）：卡片动作数据流 + 离线队列补发泵共用
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appCardActions.start(appScope)
        offlineActionQueue.start(appScope)
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
