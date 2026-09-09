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

/**
 * 稍后再看 ViewModel：观察本地 read_later 表（类型筛选），支持清空。
 * 加入 / 移出的唯一入口在卡片长按菜单（全局动作宿主），本页仅提供类型筛选与清空。
 */
@HiltViewModel
class ReadLaterViewModel @Inject constructor(
    private val readLaterDao: ReadLaterDao,
) : ViewModel() {

    /**
     * 类型筛选（"illust" / "novel"）：由页面 Pager 落页经 [setFilter] 回写；
     * 选中态由 UI 侧 Pager 状态持有，不对外暴露。
     */
    private val _filter = MutableStateFlow("illust")

    /** 当前筛选类型下的稍后再看列表（按加入时间倒序；切筛选自动重订阅）。 */
    val items: StateFlow<List<ReadLaterEntity>> =
        _filter.flatMapLatest { type -> readLaterDao.observeByType(type) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 切换类型筛选。
     *
     * @param type "illust" / "novel"
     * @return 无返回值
     */
    fun setFilter(type: String) {
        _filter.value = type
    }

    /** 清空全部稍后再看（两种类型一并清空）。 */
    fun clearAll() {
        viewModelScope.launch { readLaterDao.clearAll() }
    }
}
