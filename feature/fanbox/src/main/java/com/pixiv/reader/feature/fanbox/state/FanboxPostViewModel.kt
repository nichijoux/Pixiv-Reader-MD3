package com.pixiv.reader.feature.fanbox.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.FanboxComment
import com.pixiv.api.model.FanboxPlan
import com.pixiv.api.model.FanboxPost
import com.pixiv.reader.core.network.fanbox.FanboxRepository
import com.pixiv.reader.core.network.fanbox.FanboxSection
import com.pixiv.reader.core.network.fanbox.parseFanboxBody
import com.pixiv.reader.core.network.message.MessageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** 帖子详情状态：正文取不到时退回元数据（bodyFallback），页面不塌。 */
data class FanboxPostState(
    val post: FanboxPost? = null,
    val sections: List<FanboxSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** post.info 失败、已退回 post.get（仅元数据无正文）时为 true。 */
    val bodyFallback: Boolean = false,
)

/** 赞助方案段状态（仅受限帖加载）。 */
data class FanboxPlansState(
    val items: List<FanboxPlan> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 评论段状态：locked 为赞助门槛拦截（PLEDGE_INSUFFICIENT），按锁定展示而非报错。 */
data class FanboxCommentsState(
    val items: List<FanboxComment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val locked: Boolean = false,
)

/**
 * FANBOX 帖子详情 ViewModel：正文（post.info → post.get 兜底）、赞助方案（受限帖）、
 * 评论三路独立加载，各自失败不塌整页。
 */
@HiltViewModel
class FanboxPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    /** 路由参数 postId（全屏路由初始化；平板 pane 经 [loadPost] 动态切换）。 */
    var postId: String = savedStateHandle["postId"] ?: ""
        private set

    private val _post = MutableStateFlow(FanboxPostState())
    val post: StateFlow<FanboxPostState> = _post.asStateFlow()

    private val _plans = MutableStateFlow(FanboxPlansState())
    val plans: StateFlow<FanboxPlansState> = _plans.asStateFlow()

    private val _comments = MutableStateFlow(FanboxCommentsState())
    val comments: StateFlow<FanboxCommentsState> = _comments.asStateFlow()

    private var postJob: Job? = null
    private var plansJob: Job? = null
    private var commentsJob: Job? = null

    init {
        if (postId.isNotBlank()) {
            startLoadJobs(postId)
        }
    }

    /**
     * 取消前一帖子的在途请求，重置状态并发起当前 [targetId] 的详情与评论加载。
     *
     * @param targetId 目标帖子 ID
     * @return 无返回值
     */
    private fun startLoadJobs(targetId: String) {
        postJob?.cancel()
        plansJob?.cancel()
        commentsJob?.cancel()
        _post.value = FanboxPostState(isLoading = true)
        _plans.value = FanboxPlansState()
        _comments.value = FanboxCommentsState(isLoading = true)
        postJob = viewModelScope.launch { loadPostInternal(targetId) }
        commentsJob = viewModelScope.launch { loadCommentsInternal(targetId) }
    }

    /**
     * 加载帖子详情：优先无屏 WebView 取 post.info（含正文），失败退回 post.get（仅元数据）。
     * 成功后受限帖再触发赞助方案加载；请求期间若切换帖子则丢弃陈旧响应。
     *
     * @param targetId 请求发起时的帖子 ID
     * @return 无返回值
     */
    private suspend fun loadPostInternal(targetId: String) {
        // 阶段 1：post.info（唯一带正文；CF 拦截 / 超时 / 解析失败返回 null）
        val info = fanboxRepository.fetchPostInfo(targetId)
        if (targetId != postId) return
        if (info != null) {
            applyPost(targetId, info, bodyFallback = false)
            return
        }
        // 阶段 2：post.get 元数据兜底
        try {
            val meta = fanboxRepository.api.postGet(targetId).body?.post
            if (targetId != postId) return
            if (meta != null) {
                applyPost(targetId, meta, bodyFallback = true)
            } else {
                _post.value = FanboxPostState(isLoading = false, error = "empty response")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (targetId != postId) return
            _post.value = FanboxPostState(isLoading = false, error = e.message)
        }
    }

    /**
     * 应用帖子数据并按需触发方案加载。
     *
     * @param targetId 当前响应归属的帖子 ID
     * @param post 帖子对象（post.info 或 post.get 来源）
     * @param bodyFallback 是否为元数据兜底（无正文）
     * @return 无返回值
     */
    private fun applyPost(targetId: String, post: FanboxPost, bodyFallback: Boolean) {
        if (targetId != postId) return
        _post.value = FanboxPostState(
            post = post,
            sections = parseFanboxBody(post),
            isLoading = false,
            bodyFallback = bodyFallback,
        )
        // 受限帖才有付费墙方案段；post.get / post.info 都带 creatorId。
        val creatorId = post.creatorId
        if (post.isRestricted && !creatorId.isNullOrBlank()) {
            loadPlans(targetId, creatorId)
        }
    }

    /**
     * 加载创作者赞助方案（受限帖专用，失败仅置错误不塌页）。
     *
     * @param targetId 当前帖子 ID（切换帖子后忽略陈旧结果）
     * @param creatorId 创作者 id
     * @return 无返回值
     */
    private fun loadPlans(targetId: String, creatorId: String) {
        plansJob?.cancel()
        _plans.value = FanboxPlansState(isLoading = true)
        plansJob = viewModelScope.launch {
            try {
                val list = fanboxRepository.api.planListCreator(creatorId).body?.plans.orEmpty()
                if (targetId == postId) {
                    _plans.value = FanboxPlansState(items = list)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (targetId == postId) {
                    _plans.value = FanboxPlansState(error = e.message)
                }
            }
        }
    }

    /**
     * 加载评论（单次一页，楼中楼服务端已内嵌）。赞助门槛（403 /
     * PLEDGE_INSUFFICIENT）按锁定态展示而非错误。
     *
     * @param targetId 请求发起时的帖子 ID
     * @return 无返回值，请求结果通过 [comments] 暴露
     */
    private suspend fun loadCommentsInternal(targetId: String) {
        try {
            val items = fanboxRepository.api.postGetComments(targetId).body?.commentList?.items.orEmpty()
            if (targetId == postId) {
                _comments.value = FanboxCommentsState(items = items, isLoading = false)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (targetId != postId) return
            // 错误体只可读取一次，两处状态共用同一次赞助门槛判定。
            val locked = isPledgeRequired(e)
            _comments.value = FanboxCommentsState(
                isLoading = false,
                error = if (locked) null else e.message,
                locked = locked,
            )
        }
    }

    /**
     * 判定异常是否为赞助门槛拦截。
     *
     * @param e 评论请求异常
     * @return 需赞助解锁为 true
     */
    private fun isPledgeRequired(e: Exception): Boolean {
        if (e !is HttpException) return false
        if (e.code() == 403) return true
        // 部分场景以 200/4xx + 错误体返回，错误体内含 PLEDGE_INSUFFICIENT
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return body?.contains("PLEDGE", ignoreCase = true) == true
    }

    /**
     * 重试整页（取消在途任务并重载详情、方案与评论）。
     *
     * @return 无返回值
     */
    fun retry() {
        if (postId.isBlank()) return
        startLoadJobs(postId)
    }

    /**
     * pane 模式加载指定帖子（全屏路由经 SavedStateHandle 初始化，平板 pane 由首页驱动切换）。
     * 切换目标时取消旧任务、重置三路状态并重新拉取。
     *
     * @param newPostId 目标帖子 id
     * @return 无返回值
     */
    fun loadPost(newPostId: String) {
        if (newPostId.isBlank() || (newPostId == postId && _post.value.post != null)) return
        postId = newPostId
        startLoadJobs(newPostId)
    }
}
