package com.pixiv.reader.feature.fanbox.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.FanboxCreator
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostList
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.fanbox.FanboxRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.feature.fanbox.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** 推荐创作者无游标，故用独立三态而不用 [PagedState]。 */
data class FanboxCreatorsState(
    val items: List<FanboxCreator> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * FANBOX 认证走 cookie：`FANBOXSESSID` 存在但失效时 post.listHome 返回 401，
 * 在 fetch 内识别并置 [sessionExpired]，由 UI 换「重新登录」引导替代普通错误态。
 */
@HiltViewModel
class FanboxHomeViewModel @Inject constructor(
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    val postsPaged = PagedState<FanboxPost>(keySelector = { it.id })

    private val _creators = MutableStateFlow(FanboxCreatorsState())
    val creators: StateFlow<FanboxCreatorsState> = _creators.asStateFlow()

    /** 会话已过期（401）——true 时 UI 以「重新登录」引导替代普通错误态。 */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        loadPosts()
        loadCreators()
    }

    fun loadPosts() {
        viewModelScope.launch {
            postsPaged.loadInitial(
                fetch = { guardSession { fanboxRepository.api.postListHome().body ?: FanboxPostList() } },
                fetchNext = { url ->
                    guardSession { fanboxRepository.api.postListHomeByUrl(url).body ?: FanboxPostList() }
                },
            )
        }
    }

    fun loadMorePosts() {
        viewModelScope.launch { postsPaged.loadMore() }
    }

    fun loadCreators() {
        if (_creators.value.isLoading) return
        _creators.value = FanboxCreatorsState(isLoading = true)
        viewModelScope.launch {
            try {
                val list = fanboxRepository.api.creatorListRecommended().body?.creators.orEmpty()
                _creators.value = FanboxCreatorsState(items = list)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _creators.value = FanboxCreatorsState(error = e.message)
            }
        }
    }

    /** 重登返回后由此恢复：过期态一并复位。 */
    fun retry() {
        _sessionExpired.value = false
        postsPaged.reset()
        loadPosts()
        if (_creators.value.error != null) loadCreators()
    }

    /**
     * 过期时置 [sessionExpired] 并发通知，异常照常上抛（PagedState 置 error，UI 优先按过期态展示）。
     */
    private suspend fun <T> guardSession(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        if (e.code() == 401) {
            _sessionExpired.value = true
            trySendMessage(UiMessage(R.string.fanbox_session_expired))
        }
        throw e
    }
}
