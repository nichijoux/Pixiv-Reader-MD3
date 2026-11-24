package com.pixiv.reader.core.network.session

import com.pixiv.api.PixivApi
import com.pixiv.api.network.AppApi
import com.pixiv.api.network.PixivWebApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * API 统一出口：app-api（作品/用户/评论/收藏等）/ webApi（网页接口，补 app-api 没有的能力）/
 * imageClient（带 pixiv Referer 的图片 client，否则 403；Coil 于 PixivApp 注入）。
 */
@Singleton
class PixivRepository @Inject constructor(
    val pixivApi: PixivApi,
) {
    val api: AppApi get() = pixivApi.api
    val webApi: PixivWebApi get() = pixivApi.webApi
    val imageClient: OkHttpClient get() = pixivApi.imageClient

    /** pixiv 网页写操作要求 x-csrf-token 头；无会话或 Cookie 中无该键返回 null。 */
    fun csrfToken(): String? =
        pixivApi.session.cookie()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("csrf_token=") }
            ?.substringAfter('=')
}
