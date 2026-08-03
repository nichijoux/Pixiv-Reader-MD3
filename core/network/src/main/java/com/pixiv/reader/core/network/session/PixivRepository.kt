package com.pixiv.reader.core.network.session

import com.example.pixivapi.PixivApi
import com.example.pixivapi.api.AppApi
import com.example.pixivapi.api.PixivWebApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Pixiv API 统一出口。
 * feature 层通过构造注入本仓库访问 api / webApi / imageClient / oauth。
 */
@Singleton
class PixivRepository @Inject constructor(
    val pixivApi: PixivApi,
) {
    val api: AppApi get() = pixivApi.api
    val webApi: PixivWebApi get() = pixivApi.webApi
    val imageClient: OkHttpClient get() = pixivApi.imageClient
}
