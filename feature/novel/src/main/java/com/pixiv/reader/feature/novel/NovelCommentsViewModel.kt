package com.pixiv.reader.feature.novel

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
 * 小说评论独立页 ViewModel（第六十四轮起从详情页拆出）。
 *
 * 职责：分页加载评论（`getNovelComments` + `getNextComments`，PagedState 游标分页）、
 * 发评论 / 回复（`parent_comment_id`）、回复目标与输入草稿状态、消息通知。
 */
@HiltViewModel
class NovelCommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    /** 分页评论列表（数据驻留 VM，触底加载更多）。 */
    val commentsPaged = PagedState<Comment>()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    /** 当前回复目标评论（非 null 时输入框预填 @昵称，底部显示回复条）。 */
    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        loadComments()
    }

    /** 首次加载 / 重新加载评论。 */
    fun loadComments() {
        viewModelScope.launch {
            commentsPaged.loadInitial(
                fetch = { pixivRepository.api.getNovelComments(novelId) },
                fetchNext = { pixivRepository.api.getNextComments(it) },
            )
        }
    }

    /** 触底加载更多评论。 */
    fun loadMoreComments() {
        viewModelScope.launch { commentsPaged.loadMore() }
    }

    fun onCommentDraftChange(value: String) {
        _commentDraft.value = value
    }

    /** 设置 / 取消回复目标；设置时输入框预填 `@昵称 `。 */
    fun setReplyTarget(comment: Comment?) {
        _replyTarget.value = comment
        _commentDraft.value = if (comment != null) {
            "@${comment.user?.name.orEmpty()} "
        } else {
            _commentDraft.value
        }
    }

    /** 发布评论 / 回复（成功后清空草稿与回复目标并刷新列表）。 */
    fun postComment() {
        val text = _commentDraft.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                pixivRepository.api.postNovelComment(
                    novelId = novelId,
                    comment = text,
                    parentCommentId = _replyTarget.value?.id,
                )
            }
                .onSuccess {
                    _commentDraft.value = ""
                    _replyTarget.value = null
                    _message.send(UiMessage(R.string.novel_msg_comment_published))
                    loadComments()
                }
                .onFailure { _message.send(UiMessage(R.string.novel_msg_comment_failed, listOf(it.message ?: ""))) }
        }
    }
}
