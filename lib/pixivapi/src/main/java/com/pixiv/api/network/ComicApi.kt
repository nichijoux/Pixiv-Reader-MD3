package com.pixiv.api.network

import com.pixiv.api.model.ComicEpisodesResponse
import com.pixiv.api.model.ComicLabelsResponse
import com.pixiv.api.model.ComicRankingResponse
import com.pixiv.api.model.ComicReadResponse
import com.pixiv.api.model.ComicSearchResponse
import com.pixiv.api.model.ComicTopResponse
import com.pixiv.api.model.ComicWorkResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * pixiv COMIC 只读 API（baseUrl `https://comic.pixiv.net/`）。请求头由 core:network 的
 * ComicHeaderInterceptor 统一注入浏览器 UA / `Referer` / `X-Requested-With: pixivcomic`——
 * `/api/app/` 各端点有 WAF，缺浏览器 UA 一律 403。
 *
 * 阅读两步流的第①步（拉 viewer 页 HTML 解析随机 salt）不在本接口：响应是 HTML 而非 JSON，
 * 由 ComicRepository 用 OkHttp 直取。
 */
interface ComicApi {

    /** 周榜分类标签（総合 / 男性向け / … 共 12 个，请求 [weeklyRanking] 时原样回传）。 */
    @GET("api/app/rankings/weekly/labels")
    suspend fun weeklyRankingLabels(): ComicLabelsResponse

    /** 人气榜分类标签：与周榜同一组标签，但仍各查一次，以对齐服务端变化。 */
    @GET("api/app/rankings/popularity/labels")
    suspend fun popularityRankingLabels(): ComicLabelsResponse

    /** 周榜（整榜下发，无翻页）。 */
    @GET("api/app/rankings/weekly/v3")
    suspend fun weeklyRanking(@Query("label") label: String): ComicRankingResponse

    /** 人气榜（整榜下发，无翻页）。 */
    @GET("api/app/rankings/popularity")
    suspend fun popularityRanking(@Query("label") label: String): ComicRankingResponse

    @GET("api/app/top/v8")
    suspend fun top(): ComicTopResponse

    @GET("api/app/works/v5/{workId}")
    suspend fun work(@Path("workId") workId: Long): ComicWorkResponse

    /** [order]：`asc` 第 1 话在前 / `desc` 最新话在前。 */
    @GET("api/app/works/{workId}/episodes/v2")
    suspend fun episodes(
        @Path("workId") workId: Long,
        @Query("order") order: String = "asc",
    ): ComicEpisodesResponse

    /** 关键词搜索：每页 30 条，页号翻页、无 next_url 游标（调用方需自合成占位游标）。 */
    @GET("api/app/works/search/v2/{keyword}")
    suspend fun searchWorks(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1,
    ): ComicSearchResponse

    /**
     * 阅读页数据（两步流第②步）：`time` 为 RFC3339 秒级精度，
     * `hash` = hex(SHA256(time + 第①步 viewer 页 HTML 里的随机 salt))。
     */
    @GET("api/app/episodes/{episodeId}/read_v4")
    suspend fun readEpisode(
        @Path("episodeId") episodeId: Long,
        @Header("X-Client-Time") time: String,
        @Header("X-Client-Hash") hash: String,
    ): ComicReadResponse
}
