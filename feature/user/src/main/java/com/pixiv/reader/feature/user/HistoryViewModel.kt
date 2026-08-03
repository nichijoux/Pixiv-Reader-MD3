package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 阅读历史 ViewModel：观察本地浏览历史（Room）。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val browseHistoryDao: BrowseHistoryDao,
) : ViewModel() {

    val history: StateFlow<List<BrowseHistoryEntity>> =
        browseHistoryDao.observeRecent(100)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entity: BrowseHistoryEntity) {
        viewModelScope.launch { browseHistoryDao.delete(entity) }
    }

    fun clearAll() {
        viewModelScope.launch { browseHistoryDao.clearAll() }
    }
}
