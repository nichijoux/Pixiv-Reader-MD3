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
 * 系列封面内存缓存（进程级 `@Singleton`）。
 *
 * pixiv 的 `/v1/user/novel-series` 列表项不带封面，只能逐个调 `getNovelSeries` 详情取第一册
 * `image_urls` —— 开销大。本缓存把 seriesId → 封面 URL 驻留进程内存，并做 **in-flight 去重**：
 * 同一 seriesId 的并发请求只真正发一次网络调用，其余 await 同一结果；命中后后续全部零请求。
 *
 * 生命周期：随进程（App 会话），重启失效（用户确认内存级即可；封面 URL 可能因作者换封面过期，
 * 会话级缓存可接受）。
 *
 * 用法（ViewModel 注入后）：
 * ```
 * val cover = seriesCoverCache.getOrFetch(seriesId) {
 *     pixivRepository.api.getNovelSeries(seriesId)
 *         .novel_series_first_novel?.image_urls?.medium
 * }
 * ```
 */
@Singleton
class SeriesCoverCache @Inject constructor() {

    private val covers = ConcurrentHashMap<Long, String>()

    /** 进行中的取封面任务：seriesId → Deferred（in-flight 去重）。 */
    private val inFlight = ConcurrentHashMap<Long, Deferred<String?>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同步查询已缓存封面；未缓存返回 null（不触发网络）。 */
    fun get(seriesId: Long): String? = covers[seriesId]

    /**
     * 取封面（带缓存）：已缓存直接返回；未缓存时同一 seriesId 并发只发一次 [fetcher]，
     * 成功后写入缓存供后续零请求复用。
     *
     * @param seriesId 系列 id
     * @param fetcher 未命中时执行的网络取封面函数（返回封面 URL，null 表示无可用图）
     */
    suspend fun getOrFetch(seriesId: Long, fetcher: suspend () -> String?): String? {
        covers[seriesId]?.let { return it }
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
        val url = deferred.await()
        if (url != null) {
            covers[seriesId] = url
        }
        return url
    }
}
