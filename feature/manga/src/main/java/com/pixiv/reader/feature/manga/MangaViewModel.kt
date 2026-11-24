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

enum class MangaContentType { MANGA, ILLUST, UGOIRA }

/**
 * 漫画 Tab ViewModel：漫画 / 插画 / 动图三流，各自独立 [PagedState]（切类型懒加载、数据驻留 VM）。
 * 动图流只能走搜索过滤（content_type=ugoira）：Pixiv 无动图推荐/排行接口。
 */
@HiltViewModel
class MangaViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    val ugoiraLoader: UgoiraLoader,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>(keySelector = { it.id })
    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val ugoiraPaged = PagedState<Illust>(keySelector = { it.id })

    // 默认选中插画；菜单展示顺序由 MangaRoute 的 MENU_ORDER 决定，与枚举声明顺序解耦
    private val _tab = MutableStateFlow(MangaContentType.ILLUST)
    val tab: StateFlow<MangaContentType> = _tab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadIllust()
    }

    fun selectTab(tab: MangaContentType) {
        _tab.value = tab
        when (tab) {
            MangaContentType.MANGA -> if (recommendPaged.items.value.isEmpty()) loadManga()
            MangaContentType.ILLUST -> if (illustPaged.items.value.isEmpty()) loadIllust()
            MangaContentType.UGOIRA -> if (ugoiraPaged.items.value.isEmpty()) loadUgoira()
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (_tab.value) {
                MangaContentType.MANGA -> recommendPaged.loadMore()
                MangaContentType.ILLUST -> illustPaged.loadMore()
                MangaContentType.UGOIRA -> ugoiraPaged.loadMore()
            }
        }
    }

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

    /** nowFavorite 是目标状态（组件回调传入），无需先读本地缓存。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    companion object {
        /** 无专门动图推荐接口，只能靠高频 tag 过滤出纯动图（避免空词搜索）。 */
        private const val UGOIRA_TAG = "動画"
        private const val UGOIRA_CONTENT_TYPE = "ugoira"
    }
}
