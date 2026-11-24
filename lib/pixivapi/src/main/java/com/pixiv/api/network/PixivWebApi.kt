package com.pixiv.api.network

import com.pixiv.api.model.*
import retrofit2.http.*

/**
 * Pixiv 网页 API（www.pixiv.net ajax）。鉴权靠 Cookie（PHPSESSID / cf_clearance 等），
 * 写操作（block/save、street/main）需要 `x-csrf-token` 头。
 */
interface PixivWebApi {

    @GET("ajax/illust/{illust_id}")
    suspend fun getWebIllust(@Path("illust_id") illustId: Long): WebResponse<WebIllust>

    @GET("ajax/novel/{novel_id}")
    suspend fun getNovelWeb(@Path("novel_id") novelId: Long): WebResponse<WebNovel>

    /** 每 P 真实原图宽高（app-api 不提供，用于多 P 详情预置展示高度）。 */
    @GET("ajax/illust/{illust_id}/pages")
    suspend fun getIllustPages(@Path("illust_id") illustId: Long): WebResponse<List<WebIllustPage>>

    @GET("ajax/user/{user_id}")
    suspend fun getWebUserDetail(
        @Path("user_id") userId: Long,
        @Query("full") full: Int = 1,
        @Query("lang") lang: String = "zh",
    ): WebResponse<WebUserDetail>

    /** [type] = illust / manga / novel。 */
    @GET("ajax/top/{type}")
    suspend fun getSquareContents(
        @Path("type") type: String,
        @Query("mode") mode: String = "all",
        @Query("lang") lang: String = "zh",
    ): SquareResponse

    @GET("touch/ajax/user/bookmarks")
    suspend fun getBookmarked(
        @Query("id") userId: Long,
        @Query("type") type: String,
        @Query("rest") rest: String,
    ): SquareResponse

    @GET("touch/ajax/user/related")
    suspend fun getRelatedUsers(
        @Query("id") userId: Long,
        @Query("type") type: String,
        @Query("rest") rest: String,
    ): SquareResponse

    @GET("touch/ajax/search/illusts")
    suspend fun searchIllusts(
        @Query("word") word: String,
        @Query("include_meta") includeMeta: Int = 1,
        @Query("type") type: String = "all",
        @Query("csw") csw: Int = 0,
        @Query("s_mode") sMode: String = "s_tag_full",
        @Query("lang") lang: String = "zh",
    ): CircleResponse

    @POST("ajax/street/v2/main")
    suspend fun getStreetMain(
        @Header("x-csrf-token") csrfToken: String,
        @Body request: StreetRequest,
    ): StreetResponse

    @GET("ajax/tags/frequent/illust")
    suspend fun getFrequentTags(
        @Query("ids[]") ids: List<Long>,
        @Query("lang") lang: String = "zh",
    ): WebResponse<List<FrequentTag>>

    /** 拉黑列表：`target_id` 必含本人一条 isTarget=true，读 isBlocked 得拉黑态。 */
    @GET("ajax/block/list")
    suspend fun getBlockList(
        @Query("target_id") targetId: Long,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 24,
        @Query("lang") lang: String = "zh",
    ): WebResponse<BlockListBody>

    @POST("ajax/block/save")
    suspend fun saveBlock(
        @Header("x-csrf-token") csrfToken: String,
        @Body request: BlockSaveRequest,
    ): WebResponse<Any>

    @GET("ajax/user/{userId}/illusts/tag")
    suspend fun getUserIllustsByTag(
        @Path("userId") userId: Long,
        @Query("tag") tag: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = 48,
        @Query("sensitiveFilterMode") sensitiveFilterMode: String = "userSetting",
        @Query("lang") lang: String = "zh",
    ): WebResponse<UserTagIllustBody>
}

data class FrequentTag(
    val tag: String? = null,
    val tag_translation: String? = null,
) : java.io.Serializable
