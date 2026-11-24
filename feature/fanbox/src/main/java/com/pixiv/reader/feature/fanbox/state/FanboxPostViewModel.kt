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

/** 正文取不到时退回元数据（[Loaded.bodyFallback]），页面不塌。 */
sealed interface FanboxPostState {
    data object Loading : FanboxPostState

    data class Loaded(
        val post: FanboxPost,
        val sections: List<FanboxSection>,
        val bodyFallback: Boolean = false,
    ) : FanboxPostState

    data class Error(val message: String? = null) : FanboxPostState

    val postOrNull: FanboxPost?
        get() = (this as? Loaded)?.post
}

/** 仅受限帖加载。 */
sealed interface FanboxPlansState {
    data object Idle : FanboxPlansState

    data object Loading : FanboxPlansState

    data class Loaded(val items: List<FanboxPlan>) : FanboxPlansState

    data class Error(val message: String? = null) : FanboxPlansState
}

/** [Locked] = 赞助门槛拦截（PLEDGE_INSUFFICIENT），按锁定展示而非报错。 */
sealed interface FanboxCommentsState {
    data object Loading : FanboxCommentsState

    data class Loaded(val items: List<FanboxComment>) : FanboxCommentsState

    data object Locked : FanboxCommentsState

    data class Error(val message: String? = null) : FanboxCommentsState
}

@HiltViewModel
class FanboxPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    /** 全屏路由经 SavedStateHandle 初始化；平板 pane 经 [loadPost] 动态切换。 */
    var postId: String = savedStateHandle["postId"] ?: ""
        private set

    private val _post = MutableStateFlow<FanboxPostState>(FanboxPostState.Loading)
    val post: StateFlow<FanboxPostState> = _post.asStateFlow()

    private val _plans = MutableStateFlow<FanboxPlansState>(FanboxPlansState.Idle)
    val plans: StateFlow<FanboxPlansState> = _plans.asStateFlow()

    private val _comments = MutableStateFlow<FanboxCommentsState>(FanboxCommentsState.Loading)
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
        _post.value = FanboxPostState.Loading
        _plans.value = FanboxPlansState.Idle
        _comments.value = FanboxCommentsState.Loading
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
                _post.value = FanboxPostState.Error("empty response")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (targetId != postId) return
            _post.value = FanboxPostState.Error(e.message)
        }
    }

    private fun applyPost(targetId: String, post: FanboxPost, bodyFallback: Boolean) {
        if (targetId != postId) return
        _post.value = FanboxPostState.Loaded(
            post = post,
            sections = parseFanboxBody(post),
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
        _plans.value = FanboxPlansState.Loading
        plansJob = viewModelScope.launch {
            try {
                val list = fanboxRepository.api.planListCreator(creatorId).body?.plans.orEmpty()
                if (targetId == postId) {
                    _plans.value = FanboxPlansState.Loaded(list)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (targetId == postId) {
                    _plans.value = FanboxPlansState.Error(e.message)
                }
            }
        }
    }

    /** 单次一页，楼中楼服务端已内嵌；403 / PLEDGE_INSUFFICIENT 按锁定态而非错误展示。 */
    private suspend fun loadCommentsInternal(targetId: String) {
        try {
            val items = fanboxRepository.api.postGetComments(targetId).body?.commentList?.items.orEmpty()
            if (targetId == postId) {
                _comments.value = FanboxCommentsState.Loaded(items)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (targetId != postId) return
            _comments.value = if (isPledgeRequired(e)) {
                FanboxCommentsState.Locked
            } else {
                FanboxCommentsState.Error(e.message)
            }
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
        if (newPostId.isBlank() || (newPostId == postId && _post.value is FanboxPostState.Loaded)) return
        postId = newPostId
        startLoadJobs(newPostId)
    }
}
