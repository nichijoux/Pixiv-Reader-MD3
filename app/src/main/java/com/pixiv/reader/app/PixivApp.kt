package com.pixiv.reader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。Hilt 装配网络层；同时作为 Coil 的默认 ImageLoader 工厂，
 * 注入 Pixiv 图片专用 OkHttpClient（自动带 Referer，否则 i.pximg.net 403）。
 */
@HiltAndroidApp
class PixivApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var pixivRepository: PixivRepository

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(pixivRepository.imageClient)
            .crossfade(true)
            .build()
}
