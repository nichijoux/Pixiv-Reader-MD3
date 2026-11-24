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

/** 正文取不到时退回元数据（[FanboxPostState.bodyFallback]），页面不塌。 */
data class FanboxPostState(
    val post: FanboxPost? = null,
    val sections: List<FanboxSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val bodyFallback: Boolean = false,
)

/** 仅受限帖加载。 */
data class FanboxPlansState(
    val items: List<FanboxPlan> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** [locked] = 赞助门槛拦截（PLEDGE_INSUFFICIENT），按锁定展示而非报错。 */
data class FanboxCommentsState(
    val items: List<FanboxComment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val locked: Boolean = false,
)

@HiltViewModel
class FanboxPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    /** 全屏路由经 SavedStateHandle 初始化；平板 pane 经 [loadPost] 动态切换。 */
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

    /** 优先无屏 WebView 取 post.info（唯一带正文），失败退回 post.get（仅元数据）。 */
    private suspend fun loadPostInternal(targetId: String) {
        // post.info：CF 拦截 / 超时 / 解析失败均返回 null
        val info = fanboxRepository.fetchPostInfo(targetId)
        if (targetId != postId) return
        if (info != null) {
            applyPost(targetId, info, bodyFallback = false)
            return
        }
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

    /** 单次一页，楼中楼服务端已内嵌；403 / PLEDGE_INSUFFICIENT 按锁定态而非错误展示。 */
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

    private fun isPledgeRequired(e: Exception): Boolean {
        if (e !is HttpException) return false
        if (e.code() == 403) return true
        // 部分场景以 200/4xx + 错误体返回，错误体内含 PLEDGE_INSUFFICIENT
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        return body?.contains("PLEDGE", ignoreCase = true) == true
    }

    fun retry() {
        if (postId.isBlank()) return
        startLoadJobs(postId)
    }

    /** 全屏路由经 SavedStateHandle 初始化，平板 pane 由首页驱动切换。 */
    fun loadPost(newPostId: String) {
        if (newPostId.isBlank() || (newPostId == postId && _post.value.post != null)) return
        postId = newPostId
        startLoadJobs(newPostId)
    }
}
