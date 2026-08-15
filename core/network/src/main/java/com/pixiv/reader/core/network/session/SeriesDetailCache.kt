package com.pixiv.reader.core.network.session

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 系列详情快照（列表页展示用，由一次 `getNovelSeries` 请求派生）。
 *
 * @param coverUrl 第一册封面 URL（`novel_series_first_novel.image_urls.medium`）
 * @param caption 系列简介（`novel_series_detail.caption`）
 * @param isConcluded 是否已完结（`novel_series_detail.is_concluded`；null = 未知）
 * @param totalChars 总字数（`novel_series_detail.total_character_count`；封面信息条用）
 * @param updatedAt 最近更新时间（`novel_series_latest_novel.create_date`；作者行日期用）
 */
data class SeriesDetailInfo(
    val coverUrl: String?,
    val caption: String?,
    val isConcluded: Boolean?,
    val totalChars: Int = 0,
    val updatedAt: String? = null,
)

/**
 * 系列详情内存缓存（进程级 `@Singleton`）。
 *
 * pixiv 的 `/v1/user/novel-series`、`/v1/watchlist/novel` 列表项均不带封面/简介/连载状态/总字数，
 * 只能逐个调 `getNovelSeries` 详情取——开销大。本缓存把 seriesId → [SeriesDetailInfo]
 * 驻留进程内存，并做 **in-flight 去重**：同一 seriesId 的并发请求只真正发一次网络调用，
 * 其余 await 同一结果；命中后后续全部零请求。封面/简介/连载状态/字数/更新时间同源自一次请求。
 *
 * 生命周期：随进程（App 会话），重启失效（用户确认内存级即可；封面 URL 可能因作者换封面过期，
 * 会话级缓存可接受）。
 *
 * 用法（ViewModel 注入后）：
 * ```
 * val info = seriesDetailCache.getOrFetch(seriesId) {
 *     pixivRepository.api.getNovelSeries(seriesId).let { resp ->
 *         SeriesDetailInfo(
 *             coverUrl = resp.novel_series_first_novel?.image_urls?.medium,
 *             caption = resp.novel_series_detail?.caption,
 *             isConcluded = resp.novel_series_detail?.is_concluded,
 *         )
 *     }
 * }
 * ```
 */
@Singleton
class SeriesDetailCache @Inject constructor() {

    private val infos = ConcurrentHashMap<Long, SeriesDetailInfo>()

    /** 进行中的取详情任务：seriesId → Deferred（in-flight 去重）。 */
    private val inFlight = ConcurrentHashMap<Long, Deferred<SeriesDetailInfo?>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同步查询已缓存详情；未缓存返回 null（不触发网络）。 */
    fun get(seriesId: Long): SeriesDetailInfo? = infos[seriesId]

    /**
     * 取详情（带缓存）：已缓存直接返回；未缓存时同一 seriesId 并发只发一次 [fetcher]，
     * 成功后写入缓存供后续零请求复用。
     *
     * @param seriesId 系列 id
     * @param fetcher 未命中时执行的网络取详情函数（返回 [SeriesDetailInfo]，null 表示无可用数据）
     */
    suspend fun getOrFetch(seriesId: Long, fetcher: suspend () -> SeriesDetailInfo?): SeriesDetailInfo? {
        infos[seriesId]?.let { return it }
        inFlight[seriesId]?.let { return it.await() }

        val deferred = scope.async {
            try {
                fetcher()
            } finally {
                inFlight.remove(seriesId)
            }
        }
        val winner = inFlight.putIfAbsent(seriesId, deferred)
        if (winner != null) {
            // 并发窗口内已有任务在跑，复用其结果并取消本次冗余请求
            deferred.cancel()
            return winner.await()
        }
        val info = deferred.await()
        if (info != null) {
            infos[seriesId] = info
        }
        return info
    }
}
