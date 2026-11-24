package com.pixiv.reader.core.network.download

import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 下载 Worker 依赖入口：当前构建 @HiltWorker 聚合为空，改用普通 Worker 手动通过 EntryPoint 取依赖。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun pixivRepository(): PixivRepository
    fun illustPageDownloader(): IllustPageDownloader
    fun downloadEntryDao(): DownloadEntryDao
    fun ugoiraExporter(): UgoiraExporter
}
