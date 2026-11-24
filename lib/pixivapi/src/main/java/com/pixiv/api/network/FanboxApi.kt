package com.pixiv.api.network

import com.pixiv.api.model.FanboxCommentResponse
import com.pixiv.api.model.FanboxCreatorListResponse
import com.pixiv.api.model.FanboxPlanListResponse
import com.pixiv.api.model.FanboxPostDetailResponse
import com.pixiv.api.model.FanboxPostListResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * FANBOX 只读 API（baseUrl `https://api.fanbox.cc/`）。认证走 cookie 而非 OAuth——pixiv app-api
 * 的 Bearer 在这里无效；请求头由 core:network 的 FanboxHeaderInterceptor 注入。
 *
 * 正文接口 `post.info` 不在本接口：该端点被 Cloudflare 单独封锁非浏览器客户端（OkHttp 一律 403），
 * 只能经 core:network 的 FanboxWebBridge 在无屏 WebView 页内 fetch。
 */
interface FanboxApi {

    /** 首页「投稿」流（需登录，未登录 / cookie 过期返回 401）。 */
    @GET("post.listHome")
    suspend fun postListHome(@Query("limit") limit: Int = 10): FanboxPostListResponse

    @GET
    suspend fun postListHomeByUrl(@Url url: String): FanboxPostListResponse

    /** 未登录也能拿到（内容退化为通用推荐）；响应无翻页游标，单页。 */
    @GET("creator.listRecommended")
    suspend fun creatorListRecommended(@Query("limit") limit: Int = 10): FanboxCreatorListResponse

    /**
     * 帖子元数据：响应 `body.post` 里没有正文 body 字段，是 post.info 取不到时的兜底。
     */
    @GET("post.get")
    suspend fun postGet(@Query("postId") postId: String): FanboxPostDetailResponse

    /** 帖子评论（楼中楼 replies 服务端已内嵌，单次取一页）。 */
    @GET("post.getComments")
    suspend fun postGetComments(
        @Query("postId") postId: String,
        @Query("limit") limit: Int = 20,
    ): FanboxCommentResponse

    @GET("plan.listCreator")
    suspend fun planListCreator(@Query("creatorId") creatorId: String): FanboxPlanListResponse
}
