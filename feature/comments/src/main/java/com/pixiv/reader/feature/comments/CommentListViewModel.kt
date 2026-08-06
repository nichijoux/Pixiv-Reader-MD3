package com.pixiv.reader.feature.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Comment
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 通用评论列表 ViewModel（novel / illust 共用）。
 *
 * 路由参数 `type`（novel/illust）决定调用哪个评论 API（分页加载 + 发布/回复），
 * `targetId` 为对应内容 id。stamp 贴纸暂不启用（API 已支持，后续可扩展）。
 */
@HiltViewModel
class CommentListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: "novel"
    private val targetId: Long = savedStateHandle.get<Long>("targetId") ?: 0L

    val isIllust: Boolean get() = type == "illust"

    /** 分页评论列表（数据驻留 VM，触底加载更多）。 */
    val commentsPaged = PagedState<Comment>()

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

    /** 当前回复目标评论（非 null 时输入框预填 @昵称，底部显示回复条）。 */
    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    /** 回复目标所属顶层评论 id（顶层评论回复时 = 自身 id；子评论回复时 = 外层顶层 id）。 */
    private val _replyTargetTopId = MutableStateFlow<Long?>(null)
    val replyTargetTopId: StateFlow<Long?> = _replyTargetTopId.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        loadComments()
    }

    /** 首次加载 / 重新加载评论（按 type 分流）。 */
    fun loadComments() {
        viewModelScope.launch {
            commentsPaged.loadInitial(
                fetch = {
                    if (isIllust) pixivRepository.api.getIllustComments(targetId)
                    else pixivRepository.api.getNovelComments(targetId)
                },
                fetchNext = { pixivRepository.api.getNextComments(it) },
            )
        }
    }

    /** 触底加载更多评论。 */
    fun loadMoreComments() {
        viewModelScope.launch { commentsPaged.loadMore() }
    }

    /** 按需加载某父评论的子回复（防重；成功后写缓存并保持展开态）。 */
    fun loadReplies(parentId: Long) {
        if (_replies.value.containsKey(parentId) || _repliesLoading.value.contains(parentId)) return
        viewModelScope.launch {
            _repliesLoading.value = _repliesLoading.value + parentId
            runCatching { pixivRepository.api.getCommentReplies(type, parentId) }
                .onSuccess { resp ->
                    _replies.value = _replies.value + (parentId to resp.comments)
                }
            _repliesLoading.value = _repliesLoading.value - parentId
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

    /** 设置 / 取消回复目标（顶层或子评论均可）；设置时输入框预填 `@昵称 `。 */
    fun setReplyTarget(comment: Comment?, topLevelId: Long?) {
        _replyTarget.value = comment
        _replyTargetTopId.value = topLevelId
        _commentDraft.value = if (comment != null) {
            "@${comment.user?.name.orEmpty()} "
        } else {
            _commentDraft.value
        }
    }

    /** 发布评论 / 回复（按 type 分流，成功后清空草稿与回复目标并刷新列表）。 */
    fun postComment() {
        val text = _commentDraft.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                if (isIllust) {
                    pixivRepository.api.postIllustComment(
                        illustId = targetId,
                        comment = text,
                        parentCommentId = _replyTarget.value?.id,
                    )
                } else {
                    pixivRepository.api.postNovelComment(
                        novelId = targetId,
                        comment = text,
                        parentCommentId = _replyTarget.value?.id,
                    )
                }
            }
                .onSuccess {
                    // 刷新目标：回复目标所属顶层评论（顶层评论回复 = 自身；子评论回复 = 外层顶层）
                    val repliedTopId = _replyTargetTopId.value ?: _replyTarget.value?.id
                    _commentDraft.value = ""
                    _replyTarget.value = null
                    _replyTargetTopId.value = null
                    _message.send(UiMessage(R.string.comment_msg_published))
                    loadComments()
                    // 刷新并自动展开该顶层评论，保证新子回复即时可见
                    if (repliedTopId != null) {
                        _expandedReplies.value = _expandedReplies.value + repliedTopId
                        _replies.value = _replies.value - repliedTopId
                        loadReplies(repliedTopId)
                    }
                }
                .onFailure { _message.send(UiMessage(R.string.comment_msg_failed, listOf(it.message ?: ""))) }
        }
    }
}
