package com.pixiv.reader.feature.user.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.ReadLaterDao
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 加入 / 移出的唯一入口在卡片长按菜单（全局动作宿主），本页只提供类型筛选与清空。 */
@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterDao: ReadLaterDao,
) : ViewModel() {

    private val _filter = MutableStateFlow("illust")

    val items: StateFlow<List<ReadLaterEntity>> =
        _filter.flatMapLatest { type -> readLaterDao.observeByType(type) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(type: String) {
        _filter.value = type
    }

    fun clearAll() {
        viewModelScope.launch { readLaterDao.clearAll() }
    }
}
