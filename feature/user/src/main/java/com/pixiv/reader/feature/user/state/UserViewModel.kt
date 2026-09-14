package com.pixiv.reader.feature.user.state

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.BlockSaveRequest
import com.pixiv.api.model.Illust
import com.pixiv.api.model.MangaSeriesItem
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.api.model.Profile
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailLoader
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "UserSeries"

/** 用户主页作品分区。 */
enum class UserSection(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.user_section_illust),
    MANGA(R.string.user_section_manga),
    NOVEL(R.string.user_section_novel),
    SERIES(R.string.user_section_series),
}

/**
 * 用户主页 ViewModel：用户详情（统计 / 关注态）+ 分区作品列表（插画 / 漫画 / 小说）+ 关注 / 取关。
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val seriesDetailLoader: SeriesDetailLoader,
    private val browseHistoryDao: BrowseHistoryDao,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    private val userId: Long = savedStateHandle.get<Long>("userId") ?: 0L

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _section = MutableStateFlow(UserSection.ILLUST)
    val section: StateFlow<UserSection> = _section.asStateFlow()

    /** 是否已拉黑该用户（通过网页版用户详情 isBlocking 初始化） */
    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isBlocking = MutableStateFlow(false)
    val isBlocking: StateFlow<Boolean> = _isBlocking.asStateFlow()

    val illustPaged = PagedState<Illust>()
    val mangaPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()
    val seriesPaged = PagedState<NovelSeriesItem>()

    /** 漫画系列列表（系列分区漫画段用；随系列分区首载一起拉取，单页 + 翻页）。 */
    val mangaSeriesPaged = PagedState<MangaSeriesItem>()

    /** 系列详情（seriesId → 封面/简介/连载状态/字数/更新时间）；列表项无这些字段，经 SeriesDetailLoader 批量补齐。 */
    private val _seriesInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val seriesInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _seriesInfos.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    _user.value = resp.user
                    _profile.value = resp.profile
                    _isFollowed.value = resp.user?.is_followed == true
                    recordHistory(resp.user)
                    loadSection(_section.value)
                    loadBlockState()
                }
                .onFailure {
                    _error.value = UiMessage(R.string.user_load_failed, listOf(it.message ?: ""))
                }
            _isLoading.value = false
        }
    }

    /** 打开用户主页时写入浏览历史（先删旧记录避免重复）。 */
    private fun recordHistory(user: User?) {
        if (user == null) return
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.deleteByTarget("user", user.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "user",
                        targetId = user.id,
                        title = user.name,
                        coverUrl = user.profile_image_urls?.best(),
                    ),
                )
            }
        }
    }

    /** 网页版用户详情含 isBlocking（我是否拉黑了对方），初始化拉黑态。 */
    private fun loadBlockState() {
        viewModelScope.launch {
            runCatching {
                pixivRepository.webApi.getWebUserDetail(userId).body?.isBlocking
            }.onSuccess { blocked ->
                if (blocked != null) _isBlocked.value = blocked
            }
        }
    }

    fun selectSection(section: UserSection) {
        if (_section.value == section) return
        _section.value = section
        if (!hasLoaded(section)) loadSection(section)
    }

    private fun hasLoaded(section: UserSection): Boolean = when (section) {
        UserSection.ILLUST -> illustPaged.items.value.isNotEmpty() || illustPaged.isLoading.value
        UserSection.MANGA -> mangaPaged.items.value.isNotEmpty() || mangaPaged.isLoading.value
        UserSection.NOVEL -> novelPaged.items.value.isNotEmpty() || novelPaged.isLoading.value
        UserSection.SERIES -> seriesPaged.items.value.isNotEmpty() || seriesPaged.isLoading.value
    }

    private fun loadSection(section: UserSection) {
        viewModelScope.launch {
            when (section) {
                UserSection.ILLUST -> illustPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserIllusts(userId, "illust") },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                UserSection.MANGA -> mangaPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserIllusts(userId, "manga") },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                UserSection.NOVEL -> novelPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserNovels(userId) },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
                UserSection.SERIES -> {
                    seriesPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserNovelSeries(userId) },
                        fetchNext = { pixivRepository.api.getNextNovelSeries(it) },
                    )
                    loadSeriesInfos(seriesPaged.items.value.map { it.id })
                    // 漫画系列随系列分区首载一起拉取（漫画段切换零等待）
                    loadMangaSeries()
                }
            }
        }
    }

    /** 漫画系列首载（幂等：已加载/加载中跳过；单页 + next_url 翻页）。 */
    private fun loadMangaSeries() {
        if (mangaSeriesPaged.items.value.isNotEmpty() || mangaSeriesPaged.isLoading.value) return
        viewModelScope.launch {
            mangaSeriesPaged.loadInitial(
                fetch = { pixivRepository.api.getUserIllustSeries(userId) },
                fetchNext = { pixivRepository.api.getNextMangaSeries(it) },
            )
        }
    }

    /** 漫画系列失败重试（重置分页状态后重拉）。 */
    fun retryMangaSeries() {
        viewModelScope.launch {
            mangaSeriesPaged.reset()
            mangaSeriesPaged.loadInitial(
                fetch = { pixivRepository.api.getUserIllustSeries(userId) },
                fetchNext = { pixivRepository.api.getNextMangaSeries(it) },
            )
        }
    }

    /** 漫画系列触底加载更多。 */
    fun loadMoreMangaSeries() {
        viewModelScope.launch { mangaSeriesPaged.loadMore() }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (_section.value) {
                UserSection.ILLUST -> illustPaged.loadMore()
                UserSection.MANGA -> mangaPaged.loadMore()
                UserSection.NOVEL -> novelPaged.loadMore()
                UserSection.SERIES -> {
                    seriesPaged.loadMore()
                    loadSeriesInfos(seriesPaged.items.value.map { it.id })
                }
            }
        }
    }

    /**
     * 为系列列表批量取详情（[SeriesDetailLoader] 批量管线：SeriesDetailCache 内存缓存 +
     * in-flight 去重，封面/简介/连载状态/字数/更新时间同源自一次 getNovelSeries）。
     * 列表项无这些字段；并发限 6，避免首屏一批详情请求打满连接池。
     * 已缓存条目先同步回填 [_seriesInfos]——VM 随页面销毁重建后其本地流为空，缓存命中
     * 路径也必须把数据送进 UI 流（否则返回后二次进入封面/简介全空白），仅真正缺失的走网络。
     */
    private fun loadSeriesInfos(seriesIds: List<Long>) {
        viewModelScope.launch {
            seriesDetailLoader.backfillInfos(
                target = _seriesInfos,
                ids = seriesIds,
                concurrency = 6,
                onError = { id, e ->
                    Log.e(TAG, "loadSeriesInfos: series=$id 详情获取失败: ${e.message}")
                },
            ) { id ->
                pixivRepository.api.getNovelSeries(id).let { resp ->
                    SeriesDetailInfo(
                        coverUrl = resp.novel_series_first_novel?.image_urls?.medium,
                        caption = resp.novel_series_detail?.caption,
                        isConcluded = resp.novel_series_detail?.is_concluded,
                        totalChars = resp.novel_series_detail?.total_character_count ?: 0,
                        updatedAt = resp.novel_series_latest_novel?.create_date,
                    )
                }
            }
        }
    }

    /**
     * 关注 / 取关（即时反馈）。
     *
     * @param restrict 关注可见性：public（默认公开）/ private（私密关注，仅自己可见）
     */
    fun toggleFollow(restrict: String = PixivConstants.RESTRICT_PUBLIC) {
        if (_isFollowing.value) return
        viewModelScope.launch {
            _isFollowing.value = true
            val current = _isFollowed.value
            favoriteActions.toggleFollowUser(userId, !current, restrict).onSuccess {
                _isFollowed.value = !current
                sendMessage(
                    if (!current) {
                        // 私密关注单独提示，让用户确认生效路径
                        if (restrict == PixivConstants.RESTRICT_PRIVATE) {
                            UiMessage(CoreR.string.core_msg_followed_private)
                        } else {
                            UiMessage(CoreR.string.core_msg_followed)
                        }
                    } else {
                        UiMessage(CoreR.string.core_msg_unfollowed)
                    }
                )
            }.onFailure {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
            }
            _isFollowing.value = false
        }
    }

    /** 拉黑 / 取消拉黑（网页接口 saveBlock，需要 CSRF token）。 */
    fun toggleBlock() {
        if (_isBlocking.value) return
        viewModelScope.launch {
            _isBlocking.value = true
            val token = pixivRepository.csrfToken()
            if (token.isNullOrBlank()) {
                sendMessage(UiMessage(R.string.user_csrf_unavailable))
                _isBlocking.value = false
                return@launch
            }
            val current = _isBlocked.value
            runCatching {
                pixivRepository.webApi.saveBlock(
                    token,
                    BlockSaveRequest(
                        user_id = userId.toString(),
                        action = if (current) "unblock" else "block",
                    ),
                )
            }.onSuccess {
                _isBlocked.value = !current
                sendMessage(UiMessage(if (!current) R.string.user_blocked else R.string.user_unblocked_user))
            }.onFailure {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
            }
            _isBlocking.value = false
        }
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)
}
