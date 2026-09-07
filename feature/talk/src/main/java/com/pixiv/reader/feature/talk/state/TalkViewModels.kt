package com.pixiv.reader.feature.talk.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.TalkMessage
import com.pixiv.api.model.TalkRoom
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 私信会话列表 ViewModel（`GET v1/talk/rooms`，只读）。
 * 官方接口无 next_url 分页，一次性加载全部会话。
 */
@HiltViewModel
class TalkListViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val _rooms = MutableStateFlow<List<TalkRoom>>(emptyList())
    val rooms: StateFlow<List<TalkRoom>> = _rooms.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    /** 加载会话列表（失败重试同入口）。 */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getTalkRooms() }
                .onSuccess { _rooms.value = it.talkRooms.orEmpty() }
                .onFailure { _error.value = it.message.orEmpty().ifBlank { "error" } }
            _isLoading.value = false
        }
    }
}

/**
 * 私信消息历史 ViewModel（`GET v1/talk/room/{roomId}/messages`，只读）。
 * 按时间正序展示（接口返回新→旧时在 VM 内反转）。
 */
@HiltViewModel
class TalkRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    /** 会话 id（路由参数）。 */
    private val roomId: Long = savedStateHandle.get<Long>("roomId") ?: 0L

    private val _messages = MutableStateFlow<List<TalkMessage>>(emptyList())
    val messages: StateFlow<List<TalkMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    /** 加载消息历史（失败重试同入口）。 */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getTalkMessages(roomId) }
                .onSuccess { resp ->
                    // 接口新→旧返回，反转为正序展示
                    _messages.value = resp.talkRoomMessages.orEmpty().asReversed()
                }
                .onFailure { _error.value = it.message.orEmpty().ifBlank { "error" } }
            _isLoading.value = false
        }
    }
}
