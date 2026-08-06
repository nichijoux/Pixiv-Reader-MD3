package com.pixiv.reader.core.network.download

import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 下载 worker 的依赖入口（@HiltWorker 聚合在当前构建为空，改用普通 Worker + 手动取依赖）。
 * Worker 内 `EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java)`
 * 获取这些依赖。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun pixivRepository(): PixivRepository
    fun progressDownloader(): ProgressDownloader
    fun downloadEntryDao(): DownloadEntryDao
}
