package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.BlockSaveRequest
import com.example.pixivapi.model.MuteTag
import com.example.pixivapi.model.MuteUser
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 屏蔽管理 ViewModel：已屏蔽用户 / 标签（getMutedHistory），支持取消屏蔽用户。
 */
@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val _mutedUsers = MutableStateFlow<List<MuteUser>>(emptyList())
    val mutedUsers: StateFlow<List<MuteUser>> = _mutedUsers.asStateFlow()

    private val _mutedTags = MutableStateFlow<List<MuteTag>>(emptyList())
    val mutedTags: StateFlow<List<MuteTag>> = _mutedTags.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { pixivRepository.api.getMutedHistory() }
                .onSuccess { history ->
                    _mutedUsers.value = history.mute_users.orEmpty()
                    _mutedTags.value = history.mute_tags.orEmpty()
                }
                .onFailure { _message.send("加载失败：${it.message}") }
            _isLoading.value = false
        }
    }

    /** 取消屏蔽用户（网页接口 saveBlock action=unblock）。 */
    fun unblockUser(muted: MuteUser) {
        val uid = muted.user?.id ?: return
        viewModelScope.launch {
            val token = csrfToken()
            if (token.isNullOrBlank()) {
                _message.send("无法获取 CSRF Token，操作不可用")
                return@launch
            }
            runCatching {
                pixivRepository.webApi.saveBlock(
                    token,
                    BlockSaveRequest(user_id = uid.toString(), action = "unblock"),
                )
            }.onSuccess {
                _mutedUsers.value = _mutedUsers.value.filterNot { it.user?.id == uid }
                _message.send("已取消屏蔽")
            }.onFailure {
                _message.send("操作失败：${it.message}")
            }
        }
    }

    private fun csrfToken(): String? {
        return pixivRepository.pixivApi.session.cookie()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("csrf_token=") }
            ?.substringAfter('=')
    }
}
