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

    init {
        viewModelScope.launch { loadPost() }
        viewModelScope.launch { loadComments() }
    }

    /**
     * 加载帖子详情：优先无屏 WebView 取 post.info（含正文），失败退回 post.get（仅元数据）。
     * 成功后受限帖再触发赞助方案加载。
     */
    private suspend fun loadPost() {
        _post.value = FanboxPostState(isLoading = true)
        // 阶段 1：post.info（唯一带正文；CF 拦截 / 超时 / 解析失败返回 null）
        val info = fanboxRepository.fetchPostInfo(postId)
        if (info != null) {
            applyPost(info, bodyFallback = false)
            return
        }
        // 阶段 2：post.get 元数据兜底
        try {
            val meta = fanboxRepository.api.postGet(postId).body?.post
            if (meta != null) {
                applyPost(meta, bodyFallback = true)
            } else {
                _post.value = FanboxPostState(error = "empty response")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _post.value = FanboxPostState(error = e.message)
        }
    }

    /**
     * 应用帖子数据并按需触发方案加载。
     *
     * @param post 帖子对象（post.info 或 post.get 来源）
     * @param bodyFallback 是否为元数据兜底（无正文）
     */
    private fun applyPost(post: FanboxPost, bodyFallback: Boolean) {
        _post.value = FanboxPostState(
            post = post,
            sections = parseFanboxBody(post),
            isLoading = false,
            bodyFallback = bodyFallback,
        )
        // 受限帖才有付费墙方案段；post.get / post.info 都带 creatorId。
        // creatorId 声明在 lib:pixivapi（跨模块属性），先落局部变量再判空，
        // 否则 isNullOrBlank 后无法智能转换为非空
        val creatorId = post.creatorId
        if (post.isRestricted && !creatorId.isNullOrBlank()) {
            loadPlans(creatorId)
        }
    }

    /**
     * 加载创作者赞助方案（受限帖专用，失败仅置错误不塌页）。
     *
     * @param creatorId 创作者 id
     */
    private fun loadPlans(creatorId: String) {
        if (_plans.value.isLoading) return
        _plans.value = FanboxPlansState(isLoading = true)
        viewModelScope.launch {
            try {
                val list = fanboxRepository.api.planListCreator(creatorId).body?.plans.orEmpty()
                _plans.value = FanboxPlansState(items = list)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _plans.value = FanboxPlansState(error = e.message)
            }
        }
    }

    /**
     * 加载评论（单次一页，楼中楼服务端已内嵌）。赞助门槛（403 /
     * PLEDGE_INSUFFICIENT）按锁定态展示而非错误。
     */
    private suspend fun loadComments() {
        _comments.value = FanboxCommentsState(isLoading = true)
        try {
            val items = fanboxRepository.api.postGetComments(postId).body?.commentList?.items.orEmpty()
            _comments.value = FanboxCommentsState(items = items, isLoading = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _comments.value = FanboxCommentsState(
                isLoading = false,
                error = if (isPledgeRequired(e)) null else e.message,
                locked = isPledgeRequired(e),
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

    /** 重试整页（详情 + 评论；方案随详情链路触发）。 */
    fun retry() {
        viewModelScope.launch { loadPost() }
        viewModelScope.launch { loadComments() }
    }

    /**
     * pane 模式加载指定帖子（全屏路由经 SavedStateHandle 初始化，平板 pane 由首页驱动切换）。
     * 切换目标时重置三路状态并重新拉取。
     *
     * @param newPostId 目标帖子 id
     */
    fun loadPost(newPostId: String) {
        if (newPostId == postId && _post.value.post != null) return
        postId = newPostId
        viewModelScope.launch { loadPost() }
        viewModelScope.launch { loadComments() }
    }
}
