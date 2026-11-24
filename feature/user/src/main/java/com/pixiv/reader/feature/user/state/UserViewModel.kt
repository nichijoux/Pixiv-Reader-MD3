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
import com.pixiv.reader.core.common.ToggleUiState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "UserSeries"

enum class UserSection(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.user_section_illust),
    MANGA(R.string.user_section_manga),
    NOVEL(R.string.user_section_novel),
    SERIES(R.string.user_section_series),
}

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

    private val _followState = MutableStateFlow(ToggleUiState.OFF)
    val followState: StateFlow<ToggleUiState> = _followState.asStateFlow()

    private val _section = MutableStateFlow(UserSection.ILLUST)
    val section: StateFlow<UserSection> = _section.asStateFlow()

    private val _blockState = MutableStateFlow(ToggleUiState.OFF)
    val blockState: StateFlow<ToggleUiState> = _blockState.asStateFlow()

    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val mangaPaged = PagedState<Illust>(keySelector = { it.id })
    val novelPaged = PagedState<Novel>(keySelector = { it.id })
    val seriesPaged = PagedState<NovelSeriesItem>(keySelector = { it.id })

    val mangaSeriesPaged = PagedState<MangaSeriesItem>(keySelector = { it.id })

    /** 列表项无封面/简介等字段，经 SeriesDetailLoader 批量补齐。 */
    private val _seriesInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val seriesInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _seriesInfos.asStateFlow()

    init {
        // 跨页面关注覆盖流：列表卡片/详情页切换关注后，本页即时响应。
        viewModelScope.launch {
            favoriteActions.followedUsers
                .map { it[userId] }
                .distinctUntilChanged()
                .collect { followed ->
                    if (followed != null && !_followState.value.inFlight && _followState.value.isOn != followed) {
                        _followState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                    }
                }
        }
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
                    val followed = resp.user?.is_followed == true
                    _followState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                    favoriteActions.setUserFollowState(userId, followed)
                    recordHistory(resp.user)
                    loadSection(_section.value)
                    loadBlockState()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _error.value = UiMessage(R.string.user_load_failed, listOf(it.message ?: ""))
                }
            _isLoading.value = false
        }
    }

    /** replaceTop 事务原子写入，避免并发写入产生重复用户条目。 */
    private fun recordHistory(user: User?) {
        if (user == null) return
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.replaceTop(
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

    /** app API 不含拉黑态，只能走网页版详情的 isBlocking。 */
    private fun loadBlockState() {
        viewModelScope.launch {
            runCatching {
                pixivRepository.webApi.getWebUserDetail(userId).body?.isBlocking
            }.onSuccess { blocked ->
                if (blocked != null) {
                    _blockState.value = if (blocked) ToggleUiState.ON else ToggleUiState.OFF
                }
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

    private fun loadMangaSeries() {
        if (mangaSeriesPaged.items.value.isNotEmpty() || mangaSeriesPaged.isLoading.value) return
        viewModelScope.launch {
            mangaSeriesPaged.loadInitial(
                fetch = { pixivRepository.api.getUserIllustSeries(userId) },
                fetchNext = { pixivRepository.api.getNextMangaSeries(it) },
            )
        }
    }

    fun retryMangaSeries() {
        viewModelScope.launch {
            mangaSeriesPaged.reset()
            mangaSeriesPaged.loadInitial(
                fetch = { pixivRepository.api.getUserIllustSeries(userId) },
                fetchNext = { pixivRepository.api.getNextMangaSeries(it) },
            )
        }
    }

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
     * 并发限 6，避免首屏一批详情请求打满连接池。
     * 缓存命中项必须同步回填 [_seriesInfos]：VM 重建后本地流为空（否则二次进入封面/简介全空白），仅真缺失的走网络。
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
     * [ToggleUiState] 进行中保留旧文案、禁用防连点，失败回滚；私密关注成功时单独提示，让用户确认生效路径。
     */
    fun toggleFollow(restrict: String = PixivConstants.RESTRICT_PUBLIC) {
        runToggle(
            _followState,
            if (restrict == PixivConstants.RESTRICT_PRIVATE) {
                CoreR.string.core_msg_followed_private
            } else {
                CoreR.string.core_msg_followed
            },
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it, restrict) }
    }

    /**
     * 网页接口 saveBlock 需 CSRF token：不可用时直接发专用提示、不发网络请求。
     */
    fun toggleBlock() {
        runToggle(
            _blockState,
            R.string.user_blocked,
            R.string.user_unblocked_user,
            failedMessage = { e ->
                if (e is CsrfUnavailableException) {
                    UiMessage(R.string.user_csrf_unavailable)
                } else {
                    UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message ?: ""))
                }
            },
        ) { target ->
            val token = pixivRepository.csrfToken()
            if (token.isNullOrBlank()) {
                return@runToggle Result.failure(CsrfUnavailableException())
            }
            try {
                pixivRepository.webApi.saveBlock(
                    token,
                    BlockSaveRequest(
                        user_id = userId.toString(),
                        action = if (target) "block" else "unblock",
                    ),
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)

    private class CsrfUnavailableException : IllegalStateException("csrf token unavailable")
}
