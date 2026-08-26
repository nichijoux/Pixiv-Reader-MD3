package com.pixiv.reader.core.network.comment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Comment
import com.pixiv.api.model.Stamp
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通用评论列表 ViewModel（novel / illust 共用，core:network 下沉，
 * 供 feature:comments 评论页与 feature:manga 排行右栏评论区共用）。
 *
 * `type`/`targetId` 从 SavedStateHandle 读取（评论路由参数）；排行右栏等内嵌场景
 * 无此参数（=0），不预载，由调用方 [switchTo] 驱动加载。
 * 支持 pixiv 贴纸（stamp）：`getStamps` 拉目录、发贴纸评论带 stamp_id。
 */
@HiltViewModel
class CommentListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : MessageViewModel() {

    private val type: String = savedStateHandle.get<String>("type") ?: "novel"
    private val targetId: Long = savedStateHandle.get<Long>("targetId") ?: 0L

    val isIllust: Boolean get() = type == "illust"

    /** 当前评论目标（可变：排行右栏随选中项切换；无路由参数时初始为 novel/0 待 [switchTo]）。 */
    private val _type = MutableStateFlow(type)
    private val _targetId = MutableStateFlow(targetId)

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
        // 评论路由必有 type+targetId；排行右栏（无路由参数）不预载，等 switchTo
        if (targetId > 0L) {
            loadComments()
            loadStamps()
        }
    }

    /**
     * 切换到另一作品的评论区（排行右栏选中项变化时调用）。
     * 清空旧评论状态后重新加载。
     */
    fun switchTo(newType: String, newTargetId: Long) {
        if (newTargetId == _targetId.value || newTargetId <= 0L) return
        _type.value = newType
        _targetId.value = newTargetId
        commentsPaged.reset()
        _replies.value = emptyMap()
        _repliesLoading.value = emptySet()
        _expandedReplies.value = emptySet()
        _commentDraft.value = ""
        _replyTarget.value = null
        _replyTargetTopId.value = null
        loadComments()
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
                    if (_type.value == "illust") {
                        pixivRepository.api.getIllustComments(_targetId.value)
                    } else {
                        pixivRepository.api.getNovelComments(_targetId.value)
                    }
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
            runCatching { pixivRepository.api.getCommentReplies(_type.value, parentId) }
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
                if (_type.value == "illust") {
                    pixivRepository.api.postIllustComment(
                        illustId = _targetId.value,
                        comment = commentText,
                        parentCommentId = parentId,
                        stampId = stampId,
                    )
                } else {
                    pixivRepository.api.postNovelComment(
                        novelId = _targetId.value,
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
                    sendMessage(UiMessage(CoreR.string.core_comment_published))
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
                        CoreR.string.core_comment_failed,
                        listOf(it.message ?: "")
                    ))
                }
        }
    }
}
