package com.pixiv.api.network

import com.pixiv.api.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * Pixiv App API（app-api.pixiv.net）。所有接口需带 `Authorization: Bearer <access_token>`；
 * 列表接口响应带 `next_url`，翻页即请求该 URL（见末尾 getNext* 系列）。
 */
interface AppApi {

    // ── 作品详情 ─────────────────────────────────────────────────────────────

    @GET("v1/illust/detail")
    suspend fun getIllust(@Query("illust_id") illustId: Long): SingleIllustResponse

    @GET("v2/illust/related")
    suspend fun getRelatedIllusts(@Query("illust_id") illustId: Long): IllustResponse

    /** [type] = illust / manga / ugoira。 */
    @GET("v1/user/illusts")
    suspend fun getUserIllusts(
        @Query("user_id") userId: Long,
        @Query("type") type: String,
        @Query("offset") offset: Int? = null,
    ): IllustResponse

    /** [contentType] = illust / manga。 */
    @GET("v1/illust/new")
    suspend fun getNewIllusts(@Query("content_type") contentType: String): IllustResponse

    @GET("v1/user/bookmarks/illust")
    suspend fun getUserBookmarkedIllusts(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
        @Query("tag") tag: String? = null,
    ): IllustResponse

    @GET("v1/ugoira/metadata")
    suspend fun getUgoiraMetadata(@Query("illust_id") illustId: Long): GifResponse

    // ── 排行榜 ───────────────────────────────────────────────────────────────

    @GET("v1/illust/ranking")
    suspend fun getRanking(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
    ): IllustResponse

    @GET("v1/novel/ranking")
    suspend fun getRankingNovels(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
    ): NovelResponse

    // ── 推荐 ─────────────────────────────────────────────────────────────────

    @GET("v1/illust/recommended")
    suspend fun getRecommendedIllusts(
        @Query("include_ranking_illusts") includeRanking: Boolean,
    ): HomeIllustResponse

    @GET("v1/manga/recommended")
    suspend fun getRecommendedManga(): HomeIllustResponse

    @GET("v1/novel/recommended")
    suspend fun getRecommendedNovels(): NovelRecommendResponse

    @GET("v1/walkthrough/illusts")
    suspend fun getWalkthrough(): IllustResponse

    @GET("v1/user/recommended")
    suspend fun getRecommendedUsers(): UserPreviewResponse

    @GET("v1/user/related")
    suspend fun getRelatedUsers(@Query("seed_user_id") userId: Long): UserPreviewResponse

    @GET("v1/trending-tags/{type}")
    suspend fun getTrendingTags(@Path("type") type: String): TrendingTagsResponse

    @GET("v1/spotlight/articles")
    suspend fun getArticles(@Query("category") category: String): ArticlesResponse

    // ── 搜索 ─────────────────────────────────────────────────────────────────

    @GET("v1/search/illust")
    suspend fun searchIllusts(
        @Query("word") word: String,
        @Query("sort") sort: String? = null,
        @Query("search_target") searchTarget: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("bookmark_num_min") bookmarkNumMin: Int? = null,
        @Query("tool") tool: String? = null,
        @Query("lang") lang: String? = null,
        @Query("search_ai_type") searchAiType: Int? = null,
        @Query("ratio_pattern") ratioPattern: String? = null,
        @Query("content_type") contentType: String? = null,
        @Query("width_min") widthMin: Int? = null,
        @Query("width_max") widthMax: Int? = null,
        @Query("height_min") heightMin: Int? = null,
        @Query("height_max") heightMax: Int? = null,
    ): IllustResponse

    @GET("v1/search/novel")
    suspend fun searchNovels(
        @Query("word") word: String,
        @Query("sort") sort: String? = null,
        @Query("search_target") searchTarget: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("bookmark_num_min") bookmarkNumMin: Int? = null,
        @Query("genre") genre: Int? = null,
        @Query("lang") lang: String? = null,
        @Query("search_ai_type") searchAiType: Int? = null,
        @Query("is_original_only") isOriginalOnly: Boolean? = null,
        @Query("is_replaceable_only") isReplaceableOnly: Boolean? = null,
        @Query("text_length_min") textLengthMin: Int? = null,
        @Query("text_length_max") textLengthMax: Int? = null,
        @Query("word_count_min") wordCountMin: Int? = null,
        @Query("word_count_max") wordCountMax: Int? = null,
        @Query("reading_time_min") readingTimeMin: Int? = null,
        @Query("reading_time_max") readingTimeMax: Int? = null,
    ): NovelResponse

    @GET("v1/search/popular-preview/illust")
    suspend fun popularPreview(
        @Query("word") word: String,
        @Query("sort") sort: String? = null,
        @Query("search_target") searchTarget: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("bookmark_num_min") bookmarkNumMin: Int? = null,
        @Query("tool") tool: String? = null,
        @Query("lang") lang: String? = null,
        @Query("search_ai_type") searchAiType: Int? = null,
        @Query("ratio_pattern") ratioPattern: String? = null,
    ): IllustResponse

    @GET("v1/search/popular-preview/novel")
    suspend fun popularNovelPreview(
        @Query("word") word: String,
        @Query("sort") sort: String? = null,
        @Query("search_target") searchTarget: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): NovelResponse

    @GET("v1/search/user")
    suspend fun searchUsers(@Query("word") word: String): UserPreviewResponse

    @GET("v2/search/autocomplete")
    suspend fun searchAutocomplete(@Query("word") word: String): AutocompleteResponse

    // ── 用户 ─────────────────────────────────────────────────────────────────

    /** 需 filter=for_ios 才能拿到 is_accept_request 等字段。 */
    @GET("v2/user/detail")
    suspend fun getUserDetail(@Query("user_id") userId: Long): UserResponse

    @GET("v1/user/me/state")
    suspend fun getSelfState(): SelfProfile

    @GET("v1/user/following")
    suspend fun getFollowingUsers(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
        @Query("offset") offset: Int? = null,
    ): UserPreviewResponse

    @GET("v1/user/follower")
    suspend fun getFollowers(@Query("user_id") userId: Long): UserPreviewResponse

    @GET("v1/user/mypixiv")
    suspend fun getMypixivUsers(@Query("user_id") userId: Long): UserPreviewResponse

    @GET("v1/user/follow/detail")
    suspend fun getFollowDetail(@Query("user_id") userId: Long): UserFollowDetail

    @Multipart
    @POST("v1/user/profile/edit")
    suspend fun updateUserProfile(@Part parts: List<MultipartBody.Part>): NullResponse

    @FormUrlEncoded
    @POST("v1/user/workspace/edit")
    suspend fun editWorkspace(@FieldMap fields: Map<String, String>): NullResponse

    @GET("v1/user/bookmark-tags/illust")
    suspend fun getIllustBookmarkTags(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
    ): BookmarkTagResponse

    @GET("v1/user/bookmark-tags/novel")
    suspend fun getNovelBookmarkTags(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
    ): BookmarkTagResponse

    @GET("v2/illust/bookmark/detail")
    suspend fun getIllustBookmarkDetail(@Query("illust_id") illustId: Long): ListBookmarkTag

    @GET("v2/novel/bookmark/detail")
    suspend fun getNovelBookmarkDetail(@Query("novel_id") novelId: Long): ListBookmarkTag

    @GET("v1/mute/list")
    suspend fun getMutedHistory(): MutedHistory

    @GET("v1/illust/bookmark/users")
    suspend fun getIllustBookmarkUsers(@Query("illust_id") illustId: Long): UserPreviewResponse

    /** 画师开启接受约稿时才有；单页返回、无 next_url。 */
    @GET("v1/user/request-plans")
    suspend fun getUserRequestPlans(@Query("user_id") userId: Long): UserRequestPlansResponse

    /** 筛选选项按当前账号动态下发，不可硬编码。 */
    @GET("v1/search/options")
    suspend fun searchOptions(
        @Query("word") word: String,
        @Query("search_target") searchTarget: String = "partial_match_for_tags",
        @Query("merge_plain_keyword_results") mergePlainKeywordResults: Boolean = true,
        @Query("include_translated_tag_results") includeTranslatedTagResults: Boolean = true,
        @Query("search_ai_type") searchAiType: Int = 0,
    ): SearchOptionsResponse

    @GET("idp-urls")
    suspend fun getIdpUrls(): IdpUrlsResponse

    @GET("v1/user/profile/presets")
    suspend fun getPresets(): Preset

    // ── 收藏 / 关注操作 ─────────────────────────────────────────────────────

    @FormUrlEncoded
    @POST("v2/illust/bookmark/add")
    suspend fun bookmarkIllust(
        @Field("illust_id") illustId: Long,
        @Field("restrict") restrict: String,
        @Field("tags[]") tags: List<String> = emptyList(),
    ): NullResponse

    @FormUrlEncoded
    @POST("v1/illust/bookmark/delete")
    suspend fun unbookmarkIllust(@Field("illust_id") illustId: Long): NullResponse

    @FormUrlEncoded
    @POST("v2/novel/bookmark/add")
    suspend fun bookmarkNovel(
        @Field("novel_id") novelId: Long,
        @Field("restrict") restrict: String,
        @Field("tags[]") tags: List<String> = emptyList(),
    ): NullResponse

    @FormUrlEncoded
    @POST("v1/novel/bookmark/delete")
    suspend fun unbookmarkNovel(@Field("novel_id") novelId: Long): NullResponse

    @FormUrlEncoded
    @POST("v1/user/follow/add")
    suspend fun followUser(
        @Field("user_id") userId: Long,
        @Field("restrict") restrict: String,
    ): NullResponse

    @FormUrlEncoded
    @POST("v1/user/follow/delete")
    suspend fun unfollowUser(@Field("user_id") userId: Long): NullResponse

    @FormUrlEncoded
    @POST("v1/novel/marker/add")
    suspend fun addNovelMarker(
        @Field("novel_id") novelId: Long,
        @Field("page") page: Int,
    ): NullResponse

    @FormUrlEncoded
    @POST("v1/novel/marker/delete")
    suspend fun removeNovelMarker(@Field("novel_id") novelId: Long): NullResponse

    @GET("v2/novel/markers")
    suspend fun getNovelMarkers(): NovelMarkerResponse

    // ── 关注动态流 ──────────────────────────────────────────────────────────

    @GET("v2/illust/follow")
    suspend fun getFollowingIllusts(@Query("restrict") restrict: String): IllustResponse

    @GET("v1/novel/follow")
    suspend fun getFollowingNovels(@Query("restrict") restrict: String): NovelResponse

    @GET("v2/illust/mypixiv")
    suspend fun getMypixivIllusts(): IllustResponse

    @GET("v1/novel/mypixiv")
    suspend fun getMypixivNovels(): NovelResponse

    // ── 评论 ────────────────────────────────────────────────────────────────

    @GET("v3/illust/comments")
    suspend fun getIllustComments(@Query("illust_id") illustId: Long): CommentResponse

    @GET("v3/novel/comments")
    suspend fun getNovelComments(@Query("novel_id") novelId: Long): CommentResponse

    @GET("v2/{type}/comment/replies")
    suspend fun getCommentReplies(
        @Path("type") type: String,
        @Query("comment_id") commentId: Long,
    ): CommentResponse

    /** 发贴纸时传 [stampId] 且 comment 留空。 */
    @FormUrlEncoded
    @POST("v1/illust/comment/add")
    suspend fun postIllustComment(
        @Field("illust_id") illustId: Long,
        @Field("comment") comment: String,
        @Field("parent_comment_id") parentCommentId: Long? = null,
        @Field("stamp_id") stampId: Long? = null,
    ): PostCommentResponse

    @FormUrlEncoded
    @POST("v1/novel/comment/add")
    suspend fun postNovelComment(
        @Field("novel_id") novelId: Long,
        @Field("comment") comment: String,
        @Field("parent_comment_id") parentCommentId: Long? = null,
        @Field("stamp_id") stampId: Long? = null,
    ): PostCommentResponse

    @FormUrlEncoded
    @POST("v1/{type}/comment/delete")
    suspend fun deleteComment(
        @Path("type") type: String,
        @Field("comment_id") commentId: Long,
    ): NullResponse

    @GET("v1/stamps")
    suspend fun getStamps(): StampsResponse

    // ── 小说 ────────────────────────────────────────────────────────────────

    @GET("v2/novel/detail")
    suspend fun getNovel(@Query("novel_id") novelId: Long): SingleNovelResponse

    @GET("webview/v2/novel")
    suspend fun getNovelHtml(@Query("id") id: Long): ResponseBody

    @GET("v1/user/novels")
    suspend fun getUserNovels(@Query("user_id") userId: Long): NovelResponse

    @GET("v1/user/bookmarks/novel")
    suspend fun getUserBookmarkedNovels(
        @Query("user_id") userId: Long,
        @Query("restrict") restrict: String,
        @Query("tag") tag: String? = null,
    ): NovelResponse

    @GET("v1/novel/new")
    suspend fun getNewNovels(): NovelResponse

    // ── 系列 ────────────────────────────────────────────────────────────────

    @GET("v2/novel/series")
    suspend fun getNovelSeries(
        @Query("series_id") seriesId: Long,
        @Query("last_order") lastOrder: Int? = null,
    ): NovelSeriesResp

    @GET("v1/illust/series")
    suspend fun getIllustSeries(
        @Query("illust_series_id") seriesId: Long,
        @Query("last_order") lastOrder: Int? = null,
    ): IllustSeriesResp

    @GET("v1/user/novel-series")
    suspend fun getUserNovelSeries(@Query("user_id") userId: Long): NovelSeriesListResponse

    @GET("v1/user/illust-series")
    suspend fun getUserIllustSeries(@Query("user_id") userId: Long): MangaSeriesListResponse

    // ── 追更 ────────────────────────────────────────────────────────────────

    @GET("v1/watchlist/manga")
    suspend fun getWatchlistManga(): WatchlistResponse

    @GET("v1/watchlist/novel")
    suspend fun getWatchlistNovel(): WatchlistResponse

    @FormUrlEncoded
    @POST("v1/watchlist/manga/add")
    suspend fun addWatchlistManga(@Field("series_id") seriesId: Long): NullResponse

    @FormUrlEncoded
    @POST("v1/watchlist/manga/delete")
    suspend fun removeWatchlistManga(@Field("series_id") seriesId: Long): NullResponse

    @FormUrlEncoded
    @POST("v1/watchlist/novel/add")
    suspend fun addWatchlistNovel(@Field("series_id") seriesId: Long): NullResponse

    @FormUrlEncoded
    @POST("v1/watchlist/novel/delete")
    suspend fun removeWatchlistNovel(@Field("series_id") seriesId: Long): NullResponse

    // ── 通知 / 信息 ─────────────────────────────────────────────────────────

    @GET("v1/notification/list")
    suspend fun getNotifications(): NotificationListResponse

    @GET("v1/notification/view-more")
    suspend fun getNotificationMore(@Query("notification_id") notificationId: Long): NotificationListResponse

    @GET("v1/info/latest")
    suspend fun getLatestInfo(): InfoLatestResponse

    @GET("v1/info/list")
    suspend fun getInfoList(@Query("cid") cid: Int): InfoListResponse

    // ── 举报 ────────────────────────────────────────────────────────────────

    @GET("v1/illust/report/topic-list")
    suspend fun getReportTopicList(): IllustReportTopicListResponse

    @FormUrlEncoded
    @POST("v2/illust/report")
    suspend fun reportIllust(
        @Field("illust_id") illustId: Long,
        @Field("topic_id") topicId: Int,
        @Field("description") description: String,
    ): NullResponse

    // ── 通用翻页 ────────────────────────────────────────────────────────────

    /**
     * 任意 next_url 翻页。Retrofit 不支持泛型返回类型，故按响应类型拆分
     * （getNextIllusts / getNextNovels / ...）。
     */
    @GET
    suspend fun getNextIllusts(@Url nextUrl: String): IllustResponse

    @GET
    suspend fun getNextNovels(@Url nextUrl: String): NovelResponse

    @GET
    suspend fun getNextUsers(@Url nextUrl: String): UserPreviewResponse

    @GET
    suspend fun getNextComments(@Url nextUrl: String): CommentResponse

    @GET
    suspend fun getNextTrendingTags(@Url nextUrl: String): TrendingTagsResponse

    @GET
    suspend fun getNextArticles(@Url nextUrl: String): ArticlesResponse

    @GET
    suspend fun getNextBookmarkTags(@Url nextUrl: String): BookmarkTagResponse

    @GET
    suspend fun getNextNovelMarkers(@Url nextUrl: String): NovelMarkerResponse

    @GET
    suspend fun getNextNovelSeries(@Url nextUrl: String): NovelSeriesListResponse

    @GET
    suspend fun getNextMangaSeries(@Url nextUrl: String): MangaSeriesListResponse

    @GET
    suspend fun getNextWatchlist(@Url nextUrl: String): WatchlistResponse

    @GET
    suspend fun getNextNotifications(@Url nextUrl: String): NotificationListResponse

    @GET
    suspend fun getNextNovelSeriesDetail(@Url nextUrl: String): NovelSeriesResp

    @GET
    suspend fun getNextIllustSeries(@Url nextUrl: String): IllustSeriesResp

    @GET
    suspend fun getNextSimpleUsers(@Url nextUrl: String): SimpleUserResponse
}
