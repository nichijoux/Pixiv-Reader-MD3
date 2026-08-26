package com.pixiv.reader.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.pixiv.api.model.NotificationItem
import com.pixiv.reader.core.network.paging.PagedState
import kotlinx.coroutines.launch
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 通知中心（/v1/notification/list，next_url 翻页）。 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val paged = PagedState<NotificationItem>()

    /** 首次加载 / 错误重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getNotifications() },
                fetchNext = { pixivRepository.api.getNextNotifications(it) },
            )
            Log.d(
                TAG,
                "通知加载完成: ${paged.items.value.size} 条, hasMore=${paged.hasMore.value}" +
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
