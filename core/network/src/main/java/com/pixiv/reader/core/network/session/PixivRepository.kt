package com.pixiv.reader.core.network.session

import com.pixiv.api.PixivApi
import com.pixiv.api.network.AppApi
import com.pixiv.api.network.PixivWebApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Pixiv API 统一出口，feature 层通过构造注入本仓库访问：
 * - [api]：app-api（作品/用户/评论/收藏等常规接口）
 * - [webApi]：pixiv 网页接口（补每 P 真实宽高、isBlocking、拉黑等 app-api 没有的能力）
 * - [imageClient]：带 pixiv Referer 的图片加载 client（否则图片 403），
 *   Coil 在 PixivApp 注入该 client，PixivImage/AsyncImage 自动携带
 * - oauth / session：经 PixivApi 直取，也可走 SessionRepository
 */
@Singleton
class PixivRepository @Inject constructor(
    val pixivApi: PixivApi,
) {
    val api: AppApi get() = pixivApi.api
    val webApi: PixivWebApi get() = pixivApi.webApi
    val imageClient: OkHttpClient get() = pixivApi.imageClient
}
