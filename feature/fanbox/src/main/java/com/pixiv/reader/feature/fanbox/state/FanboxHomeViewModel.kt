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

/** 推荐创作者单页状态（无游标，独立于 [PagedState] 的轻量三态）。 */
data class FanboxCreatorsState(
    val items: List<FanboxCreator> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * FANBOX 首页 ViewModel：投稿流（PagedState 游标分页）+ 推荐创作者（单页）。
 *
 * 会话过期处理（PRD FR-2）：`FANBOXSESSID` 存在但失效时 post.listHome 返回 401——
 * 在 fetch lambda 内识别 401 并置 [sessionExpired]，UI 展示「重新登录」引导；
 * 用户在可见 WebView 重登后返回，点重试重新拉取。
 */
@HiltViewModel
class FanboxHomeViewModel @Inject constructor(
    private val fanboxRepository: FanboxRepository,
) : MessageViewModel() {

    /** 投稿流（首页 + nextUrl 触底翻页）。 */
    val postsPaged = PagedState<FanboxPost>()

    private val _creators = MutableStateFlow(FanboxCreatorsState())
    val creators: StateFlow<FanboxCreatorsState> = _creators.asStateFlow()

    /** 会话已过期（401）——true 时 UI 以「重新登录」引导替代普通错误态。 */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        loadPosts()
        loadCreators()
    }

    /**
     * 首次 / 重试加载投稿流第一页。
     */
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

    /** 触底加载下一页投稿。 */
    fun loadMorePosts() {
        viewModelScope.launch { postsPaged.loadMore() }
    }

    /**
     * 加载推荐创作者（单页，失败不塌投稿流）。
     */
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

    /**
     * 整页重试（投稿 + 创作者；会话过期态一并复位，重登返回后由此恢复）。
     */
    fun retry() {
        _sessionExpired.value = false
        postsPaged.reset()
        loadPosts()
        if (_creators.value.error != null) loadCreators()
    }

    /**
     * 包一层 fetch 鉴别 401：过期时置 [sessionExpired] 并发通知，异常照常上抛
     * （PagedState 捕获后置 error，但 UI 优先按过期态展示）。
     *
     * @param block 实际请求
     * @return 请求结果
     * @throws HttpException 请求失败时原样上抛（含 401）
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
