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

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val paged = PagedState<NotificationItem>(keySelector = { it.id })

    init {
        load()
    }

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

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}

private const val TAG = "Notification"
