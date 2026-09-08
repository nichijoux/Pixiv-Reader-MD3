package com.pixiv.reader.core.network.action

import com.pixiv.api.PixivConstants
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.PendingActionDao
import com.pixiv.reader.core.database.entity.PendingActionEntity
import com.pixiv.reader.core.network.R
import com.pixiv.reader.core.network.monitor.NetworkMonitor
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 离线操作队列：断网时收藏 / 关注 / 追更先落库暂存，联网后自动补发。
 *
 * - 写路径 [runOrQueue]：在线直连（失败按 [QueuePolicy] 分流——网络/服务端类转队列，
 *   明确拒绝传播失败）；离线直接入队。同目标重复操作经唯一索引收敛为终态。
 * - 补发泵 [start]：监听 [NetworkMonitor.isOnline]，上线沿 / 应用启动时串行补发
 *   （已登录才执行）；网络类失败中断本轮等待下次触发，明确拒绝标记 failed 保留待处理。
 * - 补发结果经 [events]（`UiMessage` 流）广播，由 app 层收集弹全局提示。
 */
@Singleton
class OfflineActionQueue @Inject constructor(
    private val dao: PendingActionDao,
    private val networkMonitor: NetworkMonitor,
    private val sessionRepository: SessionRepository,
    private val pixivRepository: PixivRepository,
) {

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

    /** 补发结果消息流（同步成功 N 条 / 失败提示；app 层收集弹全局通知）。 */
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    /** 补发互斥锁（上线沿与手动重试可能并发触发）。 */
    private val drainMutex = Mutex()

    /** 在途补发任务（防重复调度）。 */
    private var drainJob: Job? = null

    /**
     * 启动补发泵（应用创建时调用一次）：监听网络上线沿 + 启动时立即补发一次
     * （StateFlow 首值即触发，覆盖「启动时有网且有积压」场景）。
     *
     * @param scope 进程级作用域（Application 生命周期，不随页面销毁）
     * @return 无返回值；订阅持续到进程结束
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) launchDrain(scope)
            }
        }
    }

    /**
     * 在线直连 / 离线入队的统一写路径：网络类失败对调用方表现为成功（UI 乐观翻转），
     * 实际同步由补发泵兜底；服务端明确拒绝时传播失败（调用方弹错误提示）。
     *
     * @param family 操作家族（[PendingActionEntity.FAMILY_ILLUST_BOOKMARK] 等常量）
     * @param targetId 目标 id（插画 / 小说 / 用户 / 系列）
     * @param targetState 目标状态（true=加入，false=移除）
     * @param params 操作参数 JSON（收藏 restrict / tags 等；无参数传 null）
     * @param payload 展示快照 JSON（待同步列表展示；可空）
     * @param block 实际 API 调用（在线直连与补发共用同一份逻辑）
     * @return 成功（含入队）/ 失败（服务端明确拒绝）
     */
    suspend fun runOrQueue(
        family: String,
        targetId: Long,
        targetState: Boolean,
        params: String? = null,
        payload: String? = null,
        block: suspend () -> Unit,
    ): Result<Unit> {
        // 离线：直接入队（同目标旧项被唯一索引 REPLACE 收敛为终态）
        if (!networkMonitor.isOnline.value) {
            enqueue(family, targetId, targetState, params, payload)
            return Result.success(Unit)
        }
        return runCatching { block() }.fold(
            onSuccess = {
                // 直连成功：本操作即最新状态，丢弃同目标的过期队列项（防旧状态补发回滚）
                runCatching { dao.deleteByTarget(family, targetId) }
                Result.success(Unit)
            },
            onFailure = { e ->
                when (QueuePolicy.onDirectFailure(e)) {
                    QueuePolicy.Direct.ENQUEUE -> {
                        enqueue(family, targetId, targetState, params, payload)
                        Result.success(Unit)
                    }
                    QueuePolicy.Direct.FAIL -> Result.failure(e)
                }
            },
        )
    }

    /** 入队（写库失败静默——队列仅是优化，不阻塞调用方）。 */
    private suspend fun enqueue(family: String, targetId: Long, targetState: Boolean, params: String?, payload: String?) {
        runCatching {
            dao.upsert(
                PendingActionEntity(
                    family = family,
                    targetId = targetId,
                    targetState = targetState,
                    paramsJson = params,
                    payloadJson = payload,
                ),
            )
        }
    }

    /**
     * 管理页手动重试：复位该条状态为待补发并立即执行一轮补发
     * （并发触发由 [drainMutex] 串行化，重入安全）。
     *
     * @param id 队列条目 id
     * @return 无返回值
     */
    suspend fun retry(id: Long) {
        dao.updateStatus(id, attempts = 0, status = PendingActionEntity.STATUS_PENDING)
        drain()
    }

    /** 调度一轮补发（在途则跳过）。 */
    private fun launchDrain(scope: CoroutineScope) {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch { drain() }
    }

    /**
     * 补发泵本体：串行逐条执行队列中待补发操作。
     * 成功删行；网络/服务端类失败中断本轮（等下次上线沿）；明确拒绝标记 failed 继续。
     * 结束后按结果发全局提示（成功 N 条 / 失败提示）。
     *
     * @return 无返回值
     */
    private suspend fun drain(): Unit = drainMutex.withLock {
        // 未登录不补发（登出后队列保留，重新登录后随上线沿恢复）
        if (!sessionRepository.isLoggedIn.value) return
        if (!networkMonitor.isOnline.value) return
        val items = dao.getAll().filter { it.status != PendingActionEntity.STATUS_FAILED }
        var synced = 0
        var failed = 0
        for (item in items) {
            // 协程取消正常向上传播（作用域销毁时不写任何状态）
            val result = runCatching { execute(item) }
                .onFailure { if (it is CancellationException) throw it }
            if (result.isSuccess) {
                dao.delete(item)
                synced++
                continue
            }
            when (QueuePolicy.onDrainFailure(result.exceptionOrNull() ?: kotlin.run { failed++; continue })) {
                QueuePolicy.Drain.RETRY_LATER -> break
                QueuePolicy.Drain.MARK_FAILED -> {
                    dao.updateStatus(item.id, attempts = item.attempts + 1, status = PendingActionEntity.STATUS_FAILED)
                    failed++
                }
            }
        }
        if (synced > 0) {
            _events.tryEmit(UiMessage(R.string.queue_msg_synced, listOf(synced), MessageType.SUCCESS))
        }
        if (failed > 0) {
            _events.tryEmit(UiMessage(R.string.queue_msg_sync_failed, listOf(failed), MessageType.ERROR))
        }
    }

    /**
     * 按家族执行单条队列操作（与直连路径调用同一批 API）。
     * 参数（restrict / tags）从 paramsJson 还原，缺省回退公开语义。
     */
    private suspend fun execute(action: PendingActionEntity) {
        val params = action.paramsJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val restrict = params?.optString(KEY_RESTRICT)?.takeIf { it.isNotEmpty() }
            ?: PixivConstants.RESTRICT_PUBLIC
        val tags = params?.optJSONArray(KEY_TAGS)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        } ?: emptyList()
        val api = pixivRepository.api
        when (action.family) {
            PendingActionEntity.FAMILY_ILLUST_BOOKMARK ->
                if (action.targetState) api.bookmarkIllust(action.targetId, restrict, tags)
                else api.unbookmarkIllust(action.targetId)

            PendingActionEntity.FAMILY_NOVEL_BOOKMARK ->
                if (action.targetState) api.bookmarkNovel(action.targetId, restrict, tags)
                else api.unbookmarkNovel(action.targetId)

            PendingActionEntity.FAMILY_FOLLOW_USER ->
                if (action.targetState) api.followUser(action.targetId, restrict)
                else api.unfollowUser(action.targetId)

            PendingActionEntity.FAMILY_MANGA_WATCHLIST ->
                if (action.targetState) api.addWatchlistManga(action.targetId)
                else api.removeWatchlistManga(action.targetId)

            PendingActionEntity.FAMILY_NOVEL_WATCHLIST ->
                if (action.targetState) api.addWatchlistNovel(action.targetId)
                else api.removeWatchlistNovel(action.targetId)

            else -> error("unknown pending action family: ${action.family}")
        }
    }

    companion object {
        private const val KEY_RESTRICT = "restrict"
        private const val KEY_TAGS = "tags"

        /**
         * 构造收藏操作参数 JSON（restrict + 标签名列表）。
         *
         * @param restrict 收藏可见性（public / private）
         * @param tags 收藏标签名列表（可空）
         * @return 参数 JSON 字符串（无标签时仅含 restrict）
         */
        fun bookmarkParams(restrict: String, tags: List<String>): String {
            val json = JSONObject().put(KEY_RESTRICT, restrict)
            if (tags.isNotEmpty()) json.put(KEY_TAGS, JSONArray(tags))
            return json.toString()
        }
    }
}
