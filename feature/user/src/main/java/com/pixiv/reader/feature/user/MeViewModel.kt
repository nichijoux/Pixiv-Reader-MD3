package com.pixiv.reader.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.User
import com.pixiv.reader.core.network.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 我的页 ViewModel：当前登录用户信息 + 一次性提示消息。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(sessionRepository.currentUser?.user)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    /** 未实现功能的占位提示。 */
    fun comingSoon() {
        viewModelScope.launch { _message.send("功能开发中") }
    }
}
