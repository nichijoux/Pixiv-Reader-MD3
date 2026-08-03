package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 历史类型筛选。 */
enum class HistoryFilter { ALL, ILLUST, NOVEL, USER }

/**
 * 阅读历史 ViewModel：观察本地浏览历史（Room），支持按类型筛选。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val browseHistoryDao: BrowseHistoryDao,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val history: StateFlow<List<BrowseHistoryEntity>> =
        filter.flatMapLatest { f ->
            when (f) {
                HistoryFilter.ALL -> browseHistoryDao.observeRecent(200)
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
}
