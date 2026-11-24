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

/**
 * 按组头通知 id 拉取子列表（/v1/notification/view-more）。
 */
@HiltViewModel
class NotificationGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    /** 路由参数（checkNotNull：缺参即构造失败，不给错组预览）。 */
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    /** 路由参数，可空（缺省回退通用标题）。 */
    val groupTitle: String? = savedStateHandle["title"]

    val paged = PagedState<NotificationItem>(keySelector = { it.id })

    init {
        load()
    }

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

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}

private const val TAG = "Notification"
