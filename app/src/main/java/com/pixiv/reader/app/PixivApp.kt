package com.pixiv.reader.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pixiv.reader.app.actions.AppCardActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class PixivApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var pixivRepository: PixivRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appCardActions: AppCardActions

    @Inject
    lateinit var downloadCompletionNotifier: DownloadCompletionNotifier

    override fun onCreate() {
        super.onCreate()
        // 清理旧版离线功能残留目录
        runCatching { File(filesDir, "offline").deleteRecursively() }
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appCardActions.start(appScope)
        downloadCompletionNotifier.start(appScope)
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
