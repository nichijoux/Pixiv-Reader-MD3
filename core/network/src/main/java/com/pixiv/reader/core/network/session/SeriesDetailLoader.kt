package com.pixiv.reader.core.network.session

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 系列详情批量补齐器：先同步回填缓存命中的条目，再限并发补齐缺失项
 * （缓存与 in-flight 去重仍在 [SeriesDetailCache]，本类只负责编排）。
 */
class SeriesDetailLoader @Inject constructor(private val cache: SeriesDetailCache) {

    /** 详情本体原样写入的便捷入口（大多数列表页用这个）。 */
    suspend fun backfillInfos(
        target: MutableStateFlow<Map<Long, SeriesDetailInfo>>,
        ids: List<Long>,
        concurrency: Int = 4,
        onError: (id: Long, error: Throwable) -> Unit = { _, _ -> },
        fetch: suspend (id: Long) -> SeriesDetailInfo?,
    ) = backfillInto(target, ids, concurrency, select = { it }, onError = onError, fetch = fetch)

    /**
     * 批量补齐系列详情的派生值到 [target]。
     *
     * 已缓存条目也会同步回填（VM 重建后本地流为空，命中 ≠ 无需回填）；单项失败跳过不写入，
     * 取消异常向上重抛。
     */
    suspend fun <T> backfillInto(
        target: MutableStateFlow<Map<Long, T>>,
        ids: List<Long>,
        concurrency: Int = 4,
        select: (SeriesDetailInfo) -> T?,
        onError: (id: Long, error: Throwable) -> Unit = { _, _ -> },
        fetch: suspend (id: Long) -> SeriesDetailInfo?,
    ) {
        // 已在进程缓存的详情先同步回填 VM 状态（零网络、无需起协程）
        val cached = ids.mapNotNull { id ->
            cache.get(id)?.let { info -> select(info)?.let { id to it } }
        }
        if (cached.isNotEmpty()) target.update { it + cached }
        // 仅真正缺失的走网络（以回填后的流为准，避免并发重复请求）
        val missing = ids.filter { it !in target.value }
        if (missing.isEmpty()) return
        val sem = Semaphore(concurrency)
        coroutineScope {
            missing.forEach { id ->
                launch {
                    sem.withPermit {
                        val info = try {
                            cache.getOrFetch(id) { fetch(id) }
                        } catch (e: CancellationException) {
                            // 取消是调用方生命周期信号，向上重抛（冗余请求可被真正取消）
                            throw e
                        } catch (e: Exception) {
                            onError(id, e)
                            null
                        }
                        val value = info?.let(select) ?: return@withPermit
                        target.update { it + (id to value) }
                    }
                }
            }
        }
    }
}
