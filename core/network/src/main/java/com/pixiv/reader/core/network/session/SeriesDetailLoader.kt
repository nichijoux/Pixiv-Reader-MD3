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
 * 系列详情批量补齐器（core 共享）：收敛四处列表页同构的
 * 「缓存同步回填 → 过滤缺失 → 限并发逐个 [SeriesDetailCache.getOrFetch] → 逐条原子并入
 * 目标状态流」管线（用户页系列列表 / 小说 Tab 追更页签 / 追更页漫画封面 / 追更页小说详情）。
 *
 * 与 [SeriesDetailCache] 配套使用：缓存与 in-flight 去重仍在 cache 层，本类只负责批量编排。
 *
 * @param cache 系列详情进程缓存（构造注入，与调用方共享同一单例）
 */
class SeriesDetailLoader @Inject constructor(private val cache: SeriesDetailCache) {

    /**
     * 批量补齐系列详情本体到 [target]（[backfillInto] 的恒等提取便捷入口：
     * 详情就绪后原样写入，大多数列表页用这个）。
     *
     * @param target 目标状态流（seriesId → [SeriesDetailInfo]）；已缓存的条目即使命中也会同步回填
     * @param ids 待补齐的系列 id 全集
     * @param concurrency 并发上限（避免首屏一批详情请求打满连接池）
     * @param onError 单项请求失败回调（仅记录用；取消异常向上重抛不进此回调）；默认忽略
     * @param fetch 单个系列的详情请求（未命中缓存时调用；抛异常视为该项失败，跳过不写入）
     * @return 无返回值；结果经 [target] 流驱动 UI 刷新
     */
    suspend fun backfillInfos(
        target: MutableStateFlow<Map<Long, SeriesDetailInfo>>,
        ids: List<Long>,
        concurrency: Int = 4,
        onError: (id: Long, error: Throwable) -> Unit = { _, _ -> },
        fetch: suspend (id: Long) -> SeriesDetailInfo?,
    ) = backfillInto(target, ids, concurrency, select = { it }, onError = onError, fetch = fetch)

    /**
     * 批量补齐系列详情的派生值到 [target]（挂起至全部缺失项补齐或失败跳过）。
     *
     * @param T 写入值的类型（详情的派生值，如封面 URL）
     * @param target 目标状态流（seriesId → T）；已缓存的条目即使命中也会同步回填
     *   （VM 重建后本地流为空，命中 ≠ 无需回填）
     * @param ids 待补齐的系列 id 全集
     * @param concurrency 并发上限（避免首屏一批详情请求打满连接池）
     * @param select 从详情提取写入值；返回 null = 该项跳过不写入（如封面为空时）
     * @param onError 单项请求失败回调（仅记录用；取消异常向上重抛不进此回调）；默认忽略
     * @param fetch 单个系列的详情请求（未命中缓存时调用；抛异常视为该项失败，跳过不写入）
     * @return 无返回值；结果经 [target] 流驱动 UI 刷新
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
                        // 并发完成时原子并入，保持集合完整
                        target.update { it + (id to value) }
                    }
                }
            }
        }
    }
}
