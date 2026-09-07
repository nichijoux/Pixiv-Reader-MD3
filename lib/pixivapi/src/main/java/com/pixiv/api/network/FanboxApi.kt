package com.pixiv.api.network

import com.pixiv.api.model.FanboxCreatorListResponse
import com.pixiv.api.model.FanboxPostDetailResponse
import com.pixiv.api.model.FanboxPostListResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * FANBOX API（api.fanbox.cc，Cookie + Origin + CSRF 鉴权，鉴权头由
 * [FanboxHeaderInterceptor] 统一注入）。端点与响应结构对齐 Pixiv-Shaft
 * 的 FANBOX 实现；如线上字段有变，以抓包结果为准调整模型。
 */
interface FanboxApi {

    /** 关注创作者的新帖流（[parentId] 传上一页最后帖子的 id 翻页，空 = 最新一页） */
    @FormUrlEncoded
    @POST("post.listFollowing")
    suspend fun postListFollowing(
        @Field("parentId") parentId: String = "",
        @Field("limit") limit: Int = 10,
    ): FanboxPostListResponse

    /** 某创作者的帖子流（[creatorId] 形如 "12345"，[parentId] 传末帖 id 翻页） */
    @FormUrlEncoded
    @POST("post.listCreator")
    suspend fun postListCreator(
        @Field("creatorId") creatorId: String,
        @Field("parentId") parentId: String = "",
        @Field("limit") limit: Int = 10,
    ): FanboxPostListResponse

    /** 帖子详情（含正文 body） */
    @FormUrlEncoded
    @POST("post.info")
    suspend fun postInfo(
        @Field("postId") postId: String,
    ): FanboxPostDetailResponse

    /** 关注的创作者列表 */
    @FormUrlEncoded
    @POST("user.listFollowing")
    suspend fun userFollowing(
        @Field("limit") limit: Int = 30,
    ): FanboxCreatorListResponse
}
