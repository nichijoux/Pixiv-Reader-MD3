package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.BlockSaveRequest
import com.pixiv.api.model.MuteTag
import com.pixiv.api.model.MuteUser
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 屏蔽管理 ViewModel：
 * - 服务端：已屏蔽用户 / 标签（getMutedHistory），取消屏蔽用户（saveBlock unblock）
 * - 本地：推荐过滤标签（UserPreferences.mutedTags）增删
 */
@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _mutedUsers = MutableStateFlow<List<MuteUser>>(emptyList())
    val mutedUsers: StateFlow<List<MuteUser>> = _mutedUsers.asStateFlow()

    private val _mutedTags = MutableStateFlow<List<MuteTag>>(emptyList())
    val mutedTags: StateFlow<List<MuteTag>> = _mutedTags.asStateFlow()

    /** 本地推荐过滤标签。 */
    val localTags: StateFlow<List<String>> =
        userPreferences.mutedTags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
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
                .onFailure { _message.send(UiMessage(R.string.blocked_load_failed, listOf(it.message ?: ""))) }
            _isLoading.value = false
        }
    }

    // ── 本地过滤标签 ──

    fun addLocalTag(tag: String) {
        val t = tag.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            runCatching {
                if (t !in localTags.value) userPreferences.setMutedTags(localTags.value + t)
            }
        }
    }

    fun removeLocalTag(tag: String) {
        viewModelScope.launch {
            runCatching { userPreferences.setMutedTags(localTags.value - tag) }
        }
    }

    fun clearLocalTags() {
        viewModelScope.launch {
            runCatching { userPreferences.setMutedTags(emptyList()) }
        }
    }

    /** 取消屏蔽用户（网页接口 saveBlock action=unblock）。 */
    fun unblockUser(muted: MuteUser) {
        val uid = muted.user?.id ?: return
        viewModelScope.launch {
            val token = csrfToken()
            if (token.isNullOrBlank()) {
                _message.send(UiMessage(R.string.blocked_csrf_unavailable))
                return@launch
            }
            runCatching {
                pixivRepository.webApi.saveBlock(
                    token,
                    BlockSaveRequest(user_id = uid.toString(), action = "unblock"),
                )
            }.onSuccess {
                _mutedUsers.value = _mutedUsers.value.filterNot { it.user?.id == uid }
                _message.send(UiMessage(R.string.blocked_unblocked))
            }.onFailure {
                _message.send(UiMessage(R.string.operation_failed, listOf(it.message ?: "")))
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
