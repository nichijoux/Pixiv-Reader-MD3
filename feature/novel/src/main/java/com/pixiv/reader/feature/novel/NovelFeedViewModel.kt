package com.pixiv.reader.feature.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.NovelDefaultTab
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 小说 Tab 推荐流（P4）+ 关注流 + 默认页偏好（第五十三/五十四轮）。
 * 推荐接口：v1/novel/recommended（带 next_url 游标分页）；关注接口：v1/novel/follow（公开关注）。
 */
@HiltViewModel
class NovelFeedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    /** 推荐 Tab：v1/novel/recommended 游标分页。 */
    val feed = PagedState<Novel>()

    /** 关注 Tab：v1/novel/follow?restrict=public 游标分页（数据驻留 VM，切回不重复请求）。 */
    val follow = PagedState<Novel>()

    /** 关注流是否已触发首次加载（防切回重复请求）。 */
    private var followInitialized = false

    /** 推荐流下拉刷新指示。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 关注流下拉刷新指示。 */
    private val _isFollowRefreshing = MutableStateFlow(false)
    val isFollowRefreshing: StateFlow<Boolean> = _isFollowRefreshing.asStateFlow()

    /** 小说 Tab 默认页偏好（我的页-浏览设置）：推荐 / 关注。 */
    val novelDefaultTab: StateFlow<NovelDefaultTab> = userPreferences.novelDefaultTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, NovelDefaultTab.RECOMMEND)

    /** 操作通知（收藏等）：UI 侧 collect 显示 NotificationHost。 */
    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            feed.reset()
            feed.loadInitial(
                fetch = { pixivRepository.api.getRecommendedNovels() },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { feed.loadMore() }
    }

    /** 推荐流下拉刷新：重拉第一页，结束后复位指示（防重入）。 */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                feed.reset()
                feed.loadInitial(
                    fetch = { pixivRepository.api.getRecommendedNovels() },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 关注 Tab 首次进入时加载（幂等）；失败可重试。 */
    fun ensureFollowLoaded() {
        if (followInitialized) return
        followInitialized = true
        viewModelScope.launch {
            follow.loadInitial(
                fetch = { pixivRepository.api.getFollowingNovels("public") },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun refreshFollow() {
        followInitialized = true
        viewModelScope.launch {
            follow.reset()
            follow.loadInitial(
                fetch = { pixivRepository.api.getFollowingNovels("public") },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun loadMoreFollow() {
        viewModelScope.launch { follow.loadMore() }
    }

    /** 关注流下拉刷新：重拉第一页，结束后复位指示（防重入）。 */
    fun pullRefreshFollow() {
        if (_isFollowRefreshing.value) return
        viewModelScope.launch {
            _isFollowRefreshing.value = true
            try {
                follow.reset()
                follow.loadInitial(
                    fetch = { pixivRepository.api.getFollowingNovels("public") },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            } finally {
                _isFollowRefreshing.value = false
            }
        }
    }

    /** 读取小说 Tab 默认页（首帧定位用，取真实落盘值而非 stateIn 占位）。 */
    suspend fun loadDefaultTab(): NovelDefaultTab = userPreferences.novelDefaultTab.first()

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }.onSuccess {
                _message.send(UiMessage(if (nowFavorite) R.string.novel_msg_bookmarked else R.string.novel_msg_unbookmarked))
            }.onFailure {
                _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
            }
        }
    }
}
