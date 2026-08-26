package com.pixiv.reader.feature.comments.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Comment
import com.pixiv.api.model.Stamp
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.comments.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通用评论列表 ViewModel（novel / illust 共用）。
 *
 * `targetId` 为对应内容 id。支持 pixiv 贴纸（stamp）：`getStamps` 拉目录、发贴纸评论带 stamp_id。
 */
@HiltViewModel
class CommentListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : MessageViewModel() {

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

    /** 当前回复目标评论（非 null 时输入框预填 @昵称，渲染为胶囊，退格删除即取消）。 */
    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    /** 回复目标所属顶层评论 id（顶层评论回复时 = 自身 id；子评论回复时 = 外层顶层 id）。 */
    private val _replyTargetTopId = MutableStateFlow<Long?>(null)

    /** pixiv 贴纸目录（表情面板展示；加载失败则面板只显示文本表情）。 */
    private val _stamps = MutableStateFlow<List<Stamp>>(emptyList())
    val stamps: StateFlow<List<Stamp>> = _stamps.asStateFlow()

    init {
        loadComments()
        loadStamps()
    }

    /** 加载 pixiv 贴纸目录（供表情面板选贴纸发评论）。 */
    private fun loadStamps() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getStamps() }
                .onSuccess { _stamps.value = it.stamps }
        }
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
            _repliesLoading.value += parentId
            runCatching { pixivRepository.api.getCommentReplies(type, parentId) }
                .onSuccess { resp ->
                    _replies.value += (parentId to resp.comments)
                }
            _repliesLoading.value -= parentId
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
     * [stampId] 非空时发贴纸（comment 留空，pixiv API 规则）；否则发文本评论。
     */
    fun postComment(stampId: Long? = null) {
        val text = _commentDraft.value.trim()
        // 纯贴纸：文本可空（stampId 非空），否则文本必须非空
        if (text.isEmpty() && stampId == null) return
        viewModelScope.launch {
            runCatching {
                val commentText = if (stampId != null) "" else text
                val parentId = _replyTarget.value?.id
                if (isIllust) {
                    pixivRepository.api.postIllustComment(
                        illustId = targetId,
                        comment = commentText,
                        parentCommentId = parentId,
                        stampId = stampId,
                    )
                } else {
                    pixivRepository.api.postNovelComment(
                        novelId = targetId,
                        comment = commentText,
                        parentCommentId = parentId,
                        stampId = stampId,
                    )
                }
            }
                .onSuccess {
                    // 刷新目标：回复目标所属顶层评论（顶层评论回复 = 自身；子评论回复 = 外层顶层）
                    val repliedTopId = _replyTargetTopId.value ?: _replyTarget.value?.id
                    _commentDraft.value = ""
                    _replyTarget.value = null
                    _replyTargetTopId.value = null
                    sendMessage(UiMessage(R.string.comment_msg_published))
                    loadComments()
                    // 刷新并自动展开该顶层评论，保证新子回复即时可见
                    if (repliedTopId != null) {
                        _expandedReplies.value += repliedTopId
                        _replies.value -= repliedTopId
                        loadReplies(repliedTopId)
                    }
                }
                .onFailure {
                    sendMessage(UiMessage(
                        R.string.comment_msg_failed,
                        listOf(it.message ?: "")
                    ))
                }
        }
    }
}
