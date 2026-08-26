package com.pixiv.reader.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 漫画 Tab 内容类型：漫画 / 插画 / 动图。 */
enum class MangaContentType { MANGA, ILLUST, UGOIRA }

/**
 * 漫画 Tab ViewModel：漫画 / 插画 / 动图三流，各自独立 `PagedState` 分页（切类型懒加载、数据驻留 VM）。
 * - 漫画：`GET /v1/manga/recommended` 游标分页
 * - 插画：`GET /v1/illust/recommended`（与首页推荐同款）
 * - 动图：`GET /v1/search/illust?content_type=ugoira` —— Pixiv 无动图推荐/排行接口，
 *   唯一纯动图流是搜索过滤（`content_type=ugoira` 已在发现页筛选验证可用）；
 *   默认搜索词「動画」为 Pixiv 动图作品高频 tag，避免空词搜索。
 * 收藏/取消收藏由瀑布流卡片回调。
 */
@HiltViewModel
class MangaViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    /** 动图加载器：供动图 Tab 瀑布流卡片播放动画（核心层 @Singleton）。 */
    val ugoiraLoader: UgoiraLoader,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>()
    val illustPaged = PagedState<Illust>()
    val ugoiraPaged = PagedState<Illust>()

    private val _tab = MutableStateFlow(MangaContentType.MANGA)
    val tab: StateFlow<MangaContentType> = _tab.asStateFlow()

    /** 下拉刷新指示（PullToRefreshBox 用，按当前内容类型生效）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        loadManga()
    }

    /** 切换内容类型：对应列表为空时懒加载（避免每次切换都重新请求）。 */
    fun selectTab(tab: MangaContentType) {
        _tab.value = tab
        when (tab) {
            MangaContentType.MANGA -> if (recommendPaged.items.value.isEmpty()) loadManga()
            MangaContentType.ILLUST -> if (illustPaged.items.value.isEmpty()) loadIllust()
            MangaContentType.UGOIRA -> if (ugoiraPaged.items.value.isEmpty()) loadUgoira()
        }
    }

    /** 加载更多：按当前内容类型拉取对应列表下一页。 */
    fun loadMore() {
        viewModelScope.launch {
            when (_tab.value) {
                MangaContentType.MANGA -> recommendPaged.loadMore()
                MangaContentType.ILLUST -> illustPaged.loadMore()
                MangaContentType.UGOIRA -> ugoiraPaged.loadMore()
            }
        }
    }

    /** 重试：按当前内容类型重新加载。 */
    fun retry() {
        when (_tab.value) {
            MangaContentType.MANGA -> loadManga()
            MangaContentType.ILLUST -> loadIllust()
            MangaContentType.UGOIRA -> loadUgoira()
        }
    }

    private fun loadManga() {
        viewModelScope.launch {
            recommendPaged.loadInitial(
                fetch = { pixivRepository.api.getRecommendedManga() },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    private fun loadIllust() {
        viewModelScope.launch {
            illustPaged.loadInitial(
                fetch = { pixivRepository.api.getRecommendedIllusts(includeRanking = true) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    private fun loadUgoira() {
        viewModelScope.launch {
            ugoiraPaged.loadInitial(
                fetch = {
                    pixivRepository.api.searchIllusts(
                        word = UGOIRA_TAG,
                        // 显式最新排序（与发现页一致）：不传时服务端默认排序不确定，刷新可能返回稳定旧序
                        sort = "date_desc",
                        contentType = UGOIRA_CONTENT_TYPE,
                    )
                },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    /** 下拉刷新：重拉当前内容类型第一页（清空旧列表），结束后复位指示（防重入）。 */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                when (_tab.value) {
                    MangaContentType.MANGA -> {
                        recommendPaged.reset()
                        recommendPaged.loadInitial(
                            fetch = { pixivRepository.api.getRecommendedManga() },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }

                    MangaContentType.ILLUST -> {
                        illustPaged.reset()
                        illustPaged.loadInitial(
                            fetch = { pixivRepository.api.getRecommendedIllusts(includeRanking = true) },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }

                    MangaContentType.UGOIRA -> {
                        ugoiraPaged.reset()
                        ugoiraPaged.loadInitial(
                            fetch = {
                                pixivRepository.api.searchIllusts(
                                    word = UGOIRA_TAG,
                                    // 显式最新排序（与发现页一致）：不传时服务端默认排序不确定，刷新可能返回稳定旧序
                                    sort = "date_desc",
                                    contentType = UGOIRA_CONTENT_TYPE,
                                )
                            },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    companion object {
        /** 动图流默认搜索词：Pixiv 动图高频 tag（无专门动图推荐接口，仅搜索可过滤纯动图）。 */
        private const val UGOIRA_TAG = "動画"
        private const val UGOIRA_CONTENT_TYPE = "ugoira"
    }
}
