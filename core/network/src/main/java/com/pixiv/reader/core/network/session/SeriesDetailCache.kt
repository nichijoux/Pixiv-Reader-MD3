package com.pixiv.reader.core.network.session

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

data class SeriesDetailInfo(
    val coverUrl: String?,
    val caption: String?,
    val isConcluded: Boolean?,
    val totalChars: Int = 0,
    val updatedAt: String? = null,
)

/**
 * 系列详情进程级缓存：列表项不返回封面/简介/连载状态/总字数，逐个调 getNovelSeries 开销大。
 * in-flight 去重：同一 seriesId 并发只发一次网络调用；重启失效（封面可能变，会话级足够）。
 */
@Singleton
class SeriesDetailCache @Inject constructor() {

    private val infos = ConcurrentHashMap<Long, SeriesDetailInfo>()

    private val inFlight = ConcurrentHashMap<Long, Deferred<SeriesDetailInfo?>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(seriesId: Long): SeriesDetailInfo? = infos[seriesId]

    /**
     * 未命中时同一 seriesId 并发只发一次 [fetcher]；结果在缓存自身作用域写回内存层，
     * 与调用方生命周期无关。
     *
     * 以 [CoroutineStart.LAZY] 创建：先注册 in-flight 再 start，消除「检查后启动」窗口内的重复网络请求。
     */
    suspend fun getOrFetch(seriesId: Long, fetcher: suspend () -> SeriesDetailInfo?): SeriesDetailInfo? {
        infos[seriesId]?.let { return it }
        inFlight[seriesId]?.let { return it.await() }

        // 为让 finally 能引用任务自身，先声明再赋值；LAZY 保证协程体在赋值完成后才执行
        lateinit var deferred: Deferred<SeriesDetailInfo?>
        deferred = scope.async(start = CoroutineStart.LAZY) {
            val result = try {
                fetcher()
            } finally {
                // 条件移除：仅当表项仍指向自己时清除，避免误删并发胜者的注册
                inFlight.remove(seriesId, deferred)
            }
            if (result != null) {
                infos[seriesId] = result
            }
            result
        }
        val winner = inFlight.putIfAbsent(seriesId, deferred)
        if (winner != null) {
            // 并发窗口内已有任务在跑，直接复用它（本方 LAZY 任务从未启动，无需取消）
            return winner.await()
        }
        deferred.start()
        return deferred.await()
    }
}
