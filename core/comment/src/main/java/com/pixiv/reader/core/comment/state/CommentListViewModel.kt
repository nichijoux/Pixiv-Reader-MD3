package com.pixiv.reader.core.comment.state

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Comment
import com.pixiv.api.model.Stamp
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "CommentList"

/** novel / illust 两套评论 API 路径与响应结构；[apiValue] 即接口 type 路径参数（全小写）。 */
enum class CommentTarget(val apiValue: String) {
    ILLUST("illust"),
    NOVEL("novel");

    companion object {
        /** null 或未知值回退 [NOVEL]。 */
        fun from(raw: String?): CommentTarget =
            if (raw == ILLUST.apiValue) ILLUST else NOVEL
    }
}

/** 客户端排序，子回复不参与；NEWEST_FIRST 与 pixiv 官方 API 返回顺序一致。 */
enum class CommentSortOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * [Comment.date] 为 ISO 8601 字符串，字典序即时间序；缺失日期用空串兜底（降序排最后、升序排最前），
 * 与 FollowUserSorter 对 create_date 的处理一致。
 */
internal fun sortCommentsForDisplay(
    list: List<Comment>,
    order: CommentSortOrder,
): List<Comment> =
    when (order) {
        CommentSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.date.orEmpty() }
        CommentSortOrder.OLDEST_FIRST -> list.sortedBy { it.date.orEmpty() }
    }

/**
 * novel / illust 共用（评论页与排行右栏）。`type`/`targetId` 取自 SavedStateHandle；内嵌 pane 无路由参数
 * （targetId=0）时不预载，由 [switchTo] 驱动。
 */
@HiltViewModel
class CommentListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val sessionRepository: SessionRepository,
) : MessageViewModel() {

    private val target: CommentTarget = CommentTarget.from(savedStateHandle.get<String>("type"))
    private val targetId: Long = savedStateHandle.get<Long>("targetId") ?: 0L

    /** pane 场景经 [switchTo] 切换。 */
    val commentTarget: CommentTarget get() = _type.value

    /** 当前登录用户 id（未登录为 0），用于判定「自己的评论」以暴露删除入口。 */
    val ownUid: Long get() = sessionRepository.session.loggedInUid

    private val _type = MutableStateFlow(target)
    private val _targetId = MutableStateFlow(targetId)
    private var targetGeneration = 0
    private val posting = AtomicBoolean(false)

    val commentsPaged = PagedState<Comment>(keySelector = { it.id.takeIf { id -> id > 0L } })

    private val _sortOrder = MutableStateFlow(CommentSortOrder.NEWEST_FIRST)

    /** VM 会话态不持久化；切换只重排已加载数据，不发网络请求。 */
    val sortOrder: StateFlow<CommentSortOrder> = _sortOrder.asStateFlow()

    val sortedComments: StateFlow<List<Comment>> =
        combine(commentsPaged.items, _sortOrder) { list, order -> sortCommentsForDisplay(list, order) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** v3 列表只给 has_replies 标志，子回复按需拉取。 */
    private val _replies = MutableStateFlow<Map<Long, List<Comment>>>(emptyMap())
    val replies: StateFlow<Map<Long, List<Comment>>> = _replies.asStateFlow()

    private val _repliesLoading = MutableStateFlow<Set<Long>>(emptySet())
    val repliesLoading: StateFlow<Set<Long>> = _repliesLoading.asStateFlow()

    private val _expandedReplies = MutableStateFlow<Set<Long>>(emptySet())
    val expandedReplies: StateFlow<Set<Long>> = _expandedReplies.asStateFlow()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    /** 顶层评论回复时 = 自身 id；子评论回复时 = 外层顶层 id。 */
    private val _replyTargetTopId = MutableStateFlow<Long?>(null)

    /** 加载失败则面板只显示文本表情。 */
    private val _stamps = MutableStateFlow<List<Stamp>>(emptyList())
    val stamps: StateFlow<List<Stamp>> = _stamps.asStateFlow()
    private var stampsRequested = false

    init {
        // 评论路由必有 type+targetId；排行右栏（无路由参数）不预载，等 switchTo
        if (targetId > 0L) {
            Log.d(TAG, "打开评论区 type=$target targetId=$targetId")
            loadComments()
            loadStamps()
        }
    }

    /**
     * 排行右栏选中项变化时调用：清空旧评论状态后重新加载；排序方向属用户偏好，不在切换时重置。
     */
    fun switchTo(newTarget: CommentTarget, newTargetId: Long) {
        if (newTargetId <= 0L || (newTarget == _type.value && newTargetId == _targetId.value)) return
        // 作废旧作品的异步结果，再重置分页、草稿和回复树。
        targetGeneration++
        _type.value = newTarget
        _targetId.value = newTargetId
        commentsPaged.reset()
        _replies.value = emptyMap()
        _repliesLoading.value = emptySet()
        _expandedReplies.value = emptySet()
        _commentDraft.value = ""
        _replyTarget.value = null
        _replyTargetTopId.value = null
        loadComments()
        loadStamps()
    }

    /**
     * 仅重排已加载数据，不触发网络请求；[switchTo] 切换作品时不重置排序，以便右栏连续浏览多个作品时保持用户选择。
     */
    fun toggleSortOrder() {
        _sortOrder.value = when (_sortOrder.value) {
            CommentSortOrder.NEWEST_FIRST -> CommentSortOrder.OLDEST_FIRST
            CommentSortOrder.OLDEST_FIRST -> CommentSortOrder.NEWEST_FIRST
        }
    }

    /**
     * 全屏评论页与 pane 各持一个 VM，贴纸目录每 VM 只请求一次。
     */
    private fun loadStamps() {
        // 贴纸目录与作品无关，切换作品时沿用成功结果；失败时允许下次重试。
        if (stampsRequested) return
        stampsRequested = true
        viewModelScope.launch {
            runCatching { pixivRepository.api.getStamps() }
                .onSuccess { _stamps.value = it.stamps }
                .onFailure {
                    stampsRequested = false
                    if (it is CancellationException) throw it
                }
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            Log.d(TAG, "加载评论 type=${_type.value} targetId=${_targetId.value}")
            commentsPaged.loadInitial(
                fetch = {
                    if (_type.value == CommentTarget.ILLUST) {
                        pixivRepository.api.getIllustComments(_targetId.value)
                    } else {
                        pixivRepository.api.getNovelComments(_targetId.value)
                    }
                },
                fetchNext = { pixivRepository.api.getNextComments(it) },
            )
            Log.d(
                TAG,
                "评论加载完成 type=${_type.value} targetId=${_targetId.value} 条数=${commentsPaged.items.value.size} " +
                    "hasMore=${commentsPaged.hasMore.value} error=${commentsPaged.error.value}",
            )
        }
    }

    fun loadMoreComments() {
        viewModelScope.launch {
            Log.d(
                TAG,
                "加载更多评论 type=${_type.value} targetId=${_targetId.value} 当前条数=${commentsPaged.items.value.size}",
            )
            commentsPaged.loadMore()
        }
    }

    /** 结果仅回填到发起请求时的评论区（代次不符则丢弃）。 */
    fun loadReplies(parentId: Long) {
        if (_replies.value.containsKey(parentId) || _repliesLoading.value.contains(parentId)) return
        val generation = targetGeneration
        val apiType = _type.value.apiValue
        _repliesLoading.value += parentId
        viewModelScope.launch {
            try {
                val resp = pixivRepository.api.getCommentReplies(apiType, parentId)
                // 切换作品后，旧回复不得回填新作品的缓存。
                if (generation == targetGeneration) {
                    _replies.value += (parentId to resp.comments)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                // 只结束本次加载，避免清除新作品中恰好同 id 的加载状态。
                if (generation == targetGeneration) _repliesLoading.value -= parentId
            }
        }
    }

    fun toggleRepliesExpanded(parentId: Long) {
        val current = _expandedReplies.value
        _expandedReplies.value = if (parentId in current) current - parentId else current + parentId
    }

    fun onCommentDraftChange(value: String) {
        _commentDraft.value = value
    }

    /** 设置时输入框预填 `@昵称 `（渲染为回复胶囊）。 */
    fun setReplyTarget(comment: Comment?, topLevelId: Long?) {
        _replyTarget.value = comment
        _replyTargetTopId.value = topLevelId
        // 昵称为空时不预填（否则出现孤立 "@"，且无法构成胶囊）
        val name = comment?.user?.name.orEmpty()
        _commentDraft.value = if (comment != null && name.isNotBlank()) "@$name " else _commentDraft.value
    }

    /**
     * 按 type 分流，成功后清空草稿/回复目标并刷新列表；stampId 非空时按 Pixiv API 规则发纯贴纸。
     */
    fun postComment(stampId: Long? = null) {
        val draft = _commentDraft.value
        val text = draft.trim()
        if (text.isEmpty() && stampId == null) return
        // 启动协程前占用发送标记，阻止文本与贴纸入口同时提交。
        if (!posting.compareAndSet(false, true)) return
        val generation = targetGeneration
        val commentType = _type.value
        val workId = _targetId.value
        val parentId = _replyTarget.value?.id
        val repliedTopId = _replyTargetTopId.value ?: parentId
        viewModelScope.launch {
            runCatching {
                val commentText = if (stampId != null) "" else text
                if (commentType == CommentTarget.ILLUST) {
                    pixivRepository.api.postIllustComment(
                        illustId = workId,
                        comment = commentText,
                        parentCommentId = parentId,
                        stampId = stampId,
                    )
                } else {
                    pixivRepository.api.postNovelComment(
                        novelId = workId,
                        comment = commentText,
                        parentCommentId = parentId,
                        stampId = stampId,
                    )
                }
            }
                .onSuccess {
                    // 只更新原评论区，并保留发送期间新编辑的草稿或回复对象。
                    if (generation != targetGeneration) return@onSuccess
                    if (draft == _commentDraft.value && parentId == _replyTarget.value?.id) {
                        _commentDraft.value = ""
                        _replyTarget.value = null
                        _replyTargetTopId.value = null
                    }
                    loadComments()
                    // 刷新并自动展开该顶层评论，保证新子回复即时可见
                    if (repliedTopId != null) {
                        _expandedReplies.value += repliedTopId
                        _replies.value -= repliedTopId
                        loadReplies(repliedTopId)
                    }
                    trySendMessage(UiMessage(CoreR.string.core_comment_published))
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (generation == targetGeneration) {
                        trySendMessage(UiMessage(
                            CoreR.string.core_comment_failed,
                            listOf(it.message ?: "")
                        ))
                    }
                }
        }.invokeOnCompletion {
            // 成功、失败或在启动前取消均释放发送标记。
            posting.set(false)
        }
    }

    /**
     * 长按确认后调用，走 `v1/{type}/comment/delete`；成功后清空子回复缓存并刷新列表，代次变化时丢弃旧回写。
     */
    fun deleteComment(commentId: Long) {
        val generation = targetGeneration
        val apiType = _type.value.apiValue
        viewModelScope.launch {
            runCatching { pixivRepository.api.deleteComment(apiType, commentId) }
                .onSuccess {
                    if (generation != targetGeneration) return@onSuccess
                    sendMessage(UiMessage(CoreR.string.core_comment_deleted))
                    // 删除子回复后展开区不得再用旧缓存
                    _replies.value = emptyMap()
                    loadComments()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (generation == targetGeneration) {
                        sendMessage(UiMessage(
                            CoreR.string.core_comment_delete_failed,
                            listOf(it.message ?: "")
                        ))
                    }
                }
        }
    }
}
