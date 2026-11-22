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

/**
 * 评论目标类型（novel / illust 两套 API 路径与响应结构）。
 *
 * @property apiValue app-api 评论接口的 type 路径参数值（全小写）
 */
enum class CommentTarget(val apiValue: String) {
    ILLUST("illust"),
    NOVEL("novel");

    companion object {
        /**
         * 从路由/存储的原始字符串解析。
         *
         * @param raw 原始值（"illust" / "novel"；null 或未知值回退 [NOVEL]）
         * @return 解析结果（永不失败）
         */
        fun from(raw: String?): CommentTarget =
            if (raw == ILLUST.apiValue) ILLUST else NOVEL
    }
}

/**
 * 顶层评论排序方向（客户端排序，子回复不参与）。
 *
 * @property NEWEST_FIRST 最新在前（与 pixiv 官方 API 返回顺序一致）
 * @property OLDEST_FIRST 最早在前
 */
enum class CommentSortOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * 按排序方向整理顶层评论列表（纯函数，客户端排序零网络请求）。
 *
 * [Comment.date] 为 ISO 8601 字符串，字典序即时间序；缺失日期用空串兜底
 * （降序排最后、升序排最前），与 FollowUserSorter 对 create_date 的处理一致。
 *
 * @param list 待排序的顶层评论列表（通常为分页累积结果）
 * @param order 目标排序方向
 * @return 排序后的新列表（不修改入参）
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
 * 通用评论列表 ViewModel（novel / illust 共用，core:network 下沉，
 * 供 core:comment 评论页与排行右栏评论区共用）。
 *
 * `type`/`targetId` 从 SavedStateHandle 读取（评论路由参数）；排行右栏等内嵌场景
 * 无此参数（=0），不预载，由调用方 [switchTo] 驱动加载。
 * 支持 pixiv 贴纸（stamp）：`getStamps` 拉目录、发贴纸评论带 stamp_id。
 */
@HiltViewModel
class CommentListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val sessionRepository: SessionRepository,
) : MessageViewModel() {

    private val target: CommentTarget = CommentTarget.from(savedStateHandle.get<String>("type"))
    private val targetId: Long = savedStateHandle.get<Long>("targetId") ?: 0L

    /** 当前评论目标类型（pane 场景经 [switchTo] 切换）。 */
    val commentTarget: CommentTarget get() = _type.value

    /** 当前登录用户 id（未登录为 0），用于判定「自己的评论」以暴露删除入口。 */
    val ownUid: Long get() = sessionRepository.session.loggedInUid

    /** 当前评论目标（可变：排行右栏随选中项切换；无路由参数时初始为 novel/0 待 [switchTo]）。 */
    private val _type = MutableStateFlow(target)
    private val _targetId = MutableStateFlow(targetId)
    private var targetGeneration = 0
    private val posting = AtomicBoolean(false)

    /** 分页评论列表（数据驻留 VM，触底加载更多）。 */
    val commentsPaged = PagedState<Comment>(keySelector = { it.id.takeIf { id -> id > 0L } })

    /** 顶层评论排序方向（默认最新在前，与 pixiv 官方列表一致）。 */
    private val _sortOrder = MutableStateFlow(CommentSortOrder.NEWEST_FIRST)

    /**
     * 当前顶层评论排序方向（VM 会话态，不持久化；切换只重排已加载数据，不发网络请求）。
     */
    val sortOrder: StateFlow<CommentSortOrder> = _sortOrder.asStateFlow()

    /** 按 [sortOrder] 整理后的顶层评论列表（分页追加或切换方向后自动重排）。 */
    val sortedComments: StateFlow<List<Comment>> =
        combine(commentsPaged.items, _sortOrder) { list, order -> sortCommentsForDisplay(list, order) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 子回复缓存：父评论 id → 子回复列表（v3 列表只给 has_replies 标志，子回复按需拉取）。 */
    private val _replies = MutableStateFlow<Map<Long, List<Comment>>>(emptyMap())
    val replies: StateFlow<Map<Long, List<Comment>>> = _replies.asStateFlow()

    /** 正在加载子回复的父评论 id 集合。 */
    private val _repliesLoading = MutableStateFlow<Set<Long>>(emptySet())
    val repliesLoading: StateFlow<Set<Long>> = _repliesLoading.asStateFlow()

    /** 已展开子回复（超过 3 条后点「查看全部」）的父评论 id 集合。 */
    private val _expandedReplies = MutableStateFlow<Set<Long>>(emptySet())
    val expandedReplies: StateFlow<Set<Long>> = _expandedReplies.asStateFlow()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    /** 当前回复目标评论（非 null 时输入框预填 @昵称，渲染为胶囊，退格删除即取消）。 */
    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    /** 回复目标所属顶层评论 id（顶层评论回复时 = 自身 id；子评论回复时 = 外层顶层 id）。 */
    private val _replyTargetTopId = MutableStateFlow<Long?>(null)

    /** pixiv 贴纸目录（表情面板展示；加载失败则面板只显示文本表情）。 */
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
     * 切换到另一作品的评论区（排行右栏选中项变化时调用）。
     * 清空旧评论状态后重新加载；排序方向属用户偏好，不在切换时重置。
     *
     * @param newTarget 新评论目标类型（插画 / 小说）
     * @param newTargetId 新作品 id（非正数或类型和 id 均未改变时忽略）
     * @return 无返回值
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
     * 翻转顶层评论排序方向（最新在前 ↔ 最早在前）。
     *
     * 仅重排已加载数据，不触发网络请求；[switchTo] 切换作品时不重置排序，
     * 以便在右栏连续浏览多个作品时保持用户选择。
     *
     * @return 无返回值
     */
    fun toggleSortOrder() {
        _sortOrder.value = when (_sortOrder.value) {
            CommentSortOrder.NEWEST_FIRST -> CommentSortOrder.OLDEST_FIRST
            CommentSortOrder.OLDEST_FIRST -> CommentSortOrder.NEWEST_FIRST
        }
    }

    /**
     * 为全屏评论页和 pane 加载同一份贴纸目录，每个 ViewModel 只请求一次。
     *
     * @return 无返回值，请求结果通过 [stamps] 暴露
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

    /** 首次加载 / 重新加载评论（按 type 分流）。 */
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

    /** 触底加载更多评论。 */
    fun loadMoreComments() {
        viewModelScope.launch {
            Log.d(
                TAG,
                "加载更多评论 type=${_type.value} targetId=${_targetId.value} 当前条数=${commentsPaged.items.value.size}",
            )
            commentsPaged.loadMore()
        }
    }

    /**
     * 按需加载父评论下的子回复，仅向发起请求时的评论区回填结果。
     *
     * @param parentId 父评论 id
     * @return 无返回值，请求结果和加载状态通过状态流暴露
     */
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

    /** 展开 / 收起某父评论的子回复（超过 3 条时）。 */
    fun toggleRepliesExpanded(parentId: Long) {
        val current = _expandedReplies.value
        _expandedReplies.value = if (parentId in current) current - parentId else current + parentId
    }

    fun onCommentDraftChange(value: String) {
        _commentDraft.value = value
    }

    /** 设置 / 取消回复目标（顶层或子评论均可）；设置时输入框预填 `@昵称 `（渲染为回复胶囊）。 */
    fun setReplyTarget(comment: Comment?, topLevelId: Long?) {
        _replyTarget.value = comment
        _replyTargetTopId.value = topLevelId
        // 昵称为空时不预填（否则出现孤立 "@"，且无法构成胶囊）
        val name = comment?.user?.name.orEmpty()
        _commentDraft.value = if (comment != null && name.isNotBlank()) "@$name " else _commentDraft.value
    }

    /**
     * 发布评论 / 贴纸（按 type 分流，成功后清空草稿/回复目标并刷新列表）。
     *
     * @param stampId 贴纸 id，非空时按 Pixiv API 规则发送纯贴纸，否则发送文本
     * @return 无返回值，操作结果通过消息通道提示
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
     * 删除自己的评论（长按评论确认后调用；按 type 分流走 v1/{type}/comment/delete）。
     * 成功后清空子回复缓存并刷新评论列表；切换作品代次变化时丢弃旧操作回写。
     *
     * @param commentId 待删除的评论 id（顶层评论或子回复均可）
     * @return 无返回值；结果经消息通道提示
     */
    fun deleteComment(commentId: Long) {
        val generation = targetGeneration
        val apiType = _type.value.apiValue
        viewModelScope.launch {
            runCatching { pixivRepository.api.deleteComment(apiType, commentId) }
                .onSuccess {
                    if (generation != targetGeneration) return@onSuccess
                    sendMessage(UiMessage(CoreR.string.core_comment_deleted))
                    // 清空子回复缓存，避免删除子回复后展开区仍保留旧缓存。
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
