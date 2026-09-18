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
 * pixiv COMIC 只读 API（baseUrl `https://comic.pixiv.net/`，公开内容无需登录；
 * 请求头由 core:network 的 ComicHeaderInterceptor 统一注入浏览器 UA /
 * `Referer` / `X-Requested-With: pixivcomic`——`/api/app/` 各端点有 WAF，缺浏览器
 * UA 一律 403）。
 *
 * 阅读两步流中的第①步（拉取 viewer 页 HTML 解析随机 salt）**不在本接口**：
 * 响应是 HTML 而非 JSON，由 ComicRepository 用 OkHttp 直取并解析，便于日后
 * 单点替换为无屏 WebView 兜底。
 */
interface ComicApi {

    /**
     * 周榜分类标签（総合 / 男性向け / … 共 12 个，请求 [weeklyRanking] 时原样回传）。
     *
     * @return 标签列表外壳
     */
    @GET("api/app/rankings/weekly/labels")
    suspend fun weeklyRankingLabels(): ComicLabelsResponse

    /**
     * 人气榜分类标签（与周榜同一组标签，两个榜单各查一次以对齐服务端变化）。
     *
     * @return 标签列表外壳
     */
    @GET("api/app/rankings/popularity/labels")
    suspend fun popularityRankingLabels(): ComicLabelsResponse

    /**
     * 周榜（整榜下发，无翻页）。
     *
     * @param label 分类标签（日文原文，Retrofit 自动 URL 编码）
     * @return 榜单外壳
     */
    @GET("api/app/rankings/weekly/v3")
    suspend fun weeklyRanking(@Query("label") label: String): ComicRankingResponse

    /**
     * 人气榜（整榜下发，无翻页）。
     *
     * @param label 分类标签（日文原文）
     * @return 榜单外壳
     */
    @GET("api/app/rankings/popularity")
    suspend fun popularityRanking(@Query("label") label: String): ComicRankingResponse

    /**
     * COMIC 首页（banner 位 + 最近更新作品）。
     *
     * @return 首页数据外壳
     */
    @GET("api/app/top/v8")
    suspend fun top(): ComicTopResponse

    /**
     * 作品详情。
     *
     * @param workId 作品 id
     * @return 作品数据外壳（data.officialWork）
     */
    @GET("api/app/works/v5/{workId}")
    suspend fun work(@Path("workId") workId: Long): ComicWorkResponse

    /**
     * 作品章节列表。
     *
     * @param workId 作品 id
     * @param order 排序（`asc` 第 1 话在前 / `desc` 最新话在前）
     * @return 章节列表外壳
     */
    @GET("api/app/works/{workId}/episodes/v2")
    suspend fun episodes(
        @Path("workId") workId: Long,
        @Query("order") order: String = "asc",
    ): ComicEpisodesResponse

    /**
     * 关键词搜索（每页 30 条，页号翻页，无 next_url 游标）。
     *
     * @param keyword 搜索词
     * @param page 页号（从 1 开始）
     * @return 搜索结果外壳
     */
    @GET("api/app/works/search/v2/{keyword}")
    suspend fun searchWorks(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1,
    ): ComicSearchResponse

    /**
     * 阅读页数据（阅读两步流的第②步；签名所需的 salt 来自第①步 viewer 页 HTML）。
     *
     * @param episodeId 章节 id
     * @param time `X-Client-Time`：RFC3339 秒级精度的当前时间
     * @param hash `X-Client-Hash`：hex(SHA256(time + salt))
     * @return 阅读数据外壳（data.readingEpisode，含打乱的页面图片列表）
     */
    @GET("api/app/episodes/{episodeId}/read_v4")
    suspend fun readEpisode(
        @Path("episodeId") episodeId: Long,
        @Header("X-Client-Time") time: String,
        @Header("X-Client-Hash") hash: String,
    ): ComicReadResponse
}
