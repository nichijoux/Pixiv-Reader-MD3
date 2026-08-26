package com.pixiv.reader.feature.user.state

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 历史类型筛选（插画 / 小说 / 用户）。 */
enum class HistoryFilter(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.history_filter_illust),
    NOVEL(R.string.history_filter_novel),
    USER(R.string.history_filter_user),
}

/**
 * 阅读历史 ViewModel：观察本地浏览历史（Room），按类型筛选；支持历史卡片收藏。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val browseHistoryDao: BrowseHistoryDao,
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ILLUST)

    val history: StateFlow<List<BrowseHistoryEntity>> =
        filter.flatMapLatest { f ->
            when (f) {
                HistoryFilter.ILLUST -> browseHistoryDao.observeByType("illust", 200)
                HistoryFilter.NOVEL -> browseHistoryDao.observeByType("novel", 200)
                HistoryFilter.USER -> browseHistoryDao.observeByType("user", 200)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filterFlow: StateFlow<HistoryFilter> = filter

    fun selectFilter(f: HistoryFilter) {
        if (filter.value != f) filter.value = f
    }

    fun delete(entity: BrowseHistoryEntity) {
        viewModelScope.launch { browseHistoryDao.delete(entity) }
    }

    fun clearAll() {
        viewModelScope.launch { browseHistoryDao.clearAll() }
    }

    /** 收藏 / 取消收藏插画（历史卡与首页一致）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    /** 收藏 / 取消收藏小说（历史卡与详情一致）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)
}
