package com.pixiv.reader.feature.user.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.reader.core.database.dao.PendingActionDao
import com.pixiv.reader.core.database.entity.PendingActionEntity
import com.pixiv.reader.core.network.action.OfflineActionQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 待同步操作页 ViewModel：离线操作队列表（收藏 / 关注 / 追更的断网暂存）展示与管理。
 * 数据由 Room 数据流回灌（单一数据源）；重试 / 删除 / 清空直接操作队列。
 *
 * @param pendingActionDao 离线队列 DAO（观察 + 删除 / 清空）
 * @param offlineActionQueue 离线操作队列（手动重试触发立即补发）
 */
@HiltViewModel
class PendingActionsViewModel @Inject constructor(
    private val pendingActionDao: PendingActionDao,
    private val offlineActionQueue: OfflineActionQueue,
) : ViewModel() {

    /** 全部待同步操作（按入队时间正序；含 failed 状态条目）。 */
    val entries: StateFlow<List<PendingActionEntity>> = pendingActionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 重试某条失败的待同步操作（复位状态并立即补发一轮）。
     *
     * @param entry 待重试的队列条目
     * @return 无返回值
     */
    fun retry(entry: PendingActionEntity) {
        viewModelScope.launch { offlineActionQueue.retry(entry.id) }
    }

    /**
     * 删除某条待同步操作（放弃补发；服务端状态以已生效部分为准）。
     *
     * @param entry 待删除的队列条目
     * @return 无返回值
     */
    fun delete(entry: PendingActionEntity) {
        viewModelScope.launch { pendingActionDao.delete(entry) }
    }

    /**
     * 清空全部待同步操作。
     *
     * @return 无返回值
     */
    fun clearAll() {
        viewModelScope.launch { pendingActionDao.clearAll() }
    }
}
