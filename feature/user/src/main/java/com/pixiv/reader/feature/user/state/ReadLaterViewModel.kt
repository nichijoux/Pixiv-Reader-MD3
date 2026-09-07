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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 稍后再看 ViewModel：观察本地 read_later 表（类型筛选），支持移除单条 / 清空。
 * 加入 / 移出的主路径在卡片长按菜单（全局动作宿主），本页另提供列表内移除与清空。
 */
@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterDao: ReadLaterDao,
) : ViewModel() {

    /** 类型筛选（"illust" / "novel"）。 */
    private val _filter = MutableStateFlow("illust")
    val filter: StateFlow<String> = _filter.asStateFlow()

    /** 当前筛选类型下的稍后再看列表（按加入时间倒序；切筛选自动重订阅）。 */
    val items: StateFlow<List<ReadLaterEntity>> =
        _filter.flatMapLatest { type -> readLaterDao.observeByType(type) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 切换类型筛选。
     * @param type "illust" / "novel"
     */
    fun setFilter(type: String) {
        _filter.value = type
    }

    /** 移除单条（列表行操作；与长按菜单「移出」同一 DAO 通路，数据流自动同步全局状态）。 */
    fun remove(entity: ReadLaterEntity) {
        viewModelScope.launch { readLaterDao.delete(entity) }
    }

    /** 清空全部稍后再看（两种类型一并清空）。 */
    fun clearAll() {
        viewModelScope.launch { readLaterDao.clearAll() }
    }
}
