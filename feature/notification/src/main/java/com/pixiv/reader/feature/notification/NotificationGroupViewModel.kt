package com.pixiv.reader.feature.notification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.NotificationItem
import com.pixiv.reader.core.network.paging.PagedState
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 通知分组子列表（/v1/notification/view-more，按组头通知 id 拉取，next_url 翻页）。 */
@HiltViewModel
class NotificationGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    /** 路由参数：分组头通知的 id。 */
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    /** 路由参数：组名（顶栏标题，可空）。 */
    val groupTitle: String? = savedStateHandle["title"]

    val paged = PagedState<NotificationItem>()

    /** 首次加载 / 错误重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getNotificationMore(groupId) },
                fetchNext = { pixivRepository.api.getNextNotifications(it) },
            )
            Log.d(
                TAG,
                "分组[$groupId]加载完成: ${paged.items.value.size} 条, hasMore=${paged.hasMore.value}" +
                    (paged.error.value?.let { ", error=$it" } ?: ""),
            )
        }
    }

    /** 触底加载下一页。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}

private const val TAG = "Notification"
