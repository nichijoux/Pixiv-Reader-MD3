package com.pixiv.reader.feature.viewer

import android.content.Context
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 图片保存助手：下载原图到 filesDir/Downloads（P6 迁移到 WorkManager 队列） */
@Singleton
class ImageSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    suspend fun save(url: String, name: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "Downloads").apply { mkdirs() }
            val file = File(dir, name)
            pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }
            file
        }
    }
}
