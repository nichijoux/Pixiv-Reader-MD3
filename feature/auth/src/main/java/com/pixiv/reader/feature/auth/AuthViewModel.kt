package com.pixiv.reader.feature.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.auth.PixivAuthResult
import com.pixiv.reader.core.network.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface AuthEvent {
    data class OpenLoginPage(val url: String) : AuthEvent
    data object LoginSuccess : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = sessionRepository.isLoggedIn

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 已处理的授权码（code 单次性去重，防止旋转/重复回调二次提交） */
    private var processedCode: String? = null

    init {
        // 监听 OAuth 回调（冷启动 / onNewIntent 均走这里）
        viewModelScope.launch {
            sessionRepository.pendingOAuthUri.collect { uri ->
                if (uri != null) processCallback(uri)
            }
        }
    }

    fun startLogin() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val url = sessionRepository.buildLoginUrl()
            _uiState.update { it.copy(isLoading = false) }
            _events.send(AuthEvent.OpenLoginPage(url))
        }
    }

    fun startProvisionalAccount() {
        viewModelScope.launch {
            _events.send(AuthEvent.OpenLoginPage(sessionRepository.buildProvisionalAccountUrl()))
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun processCallback(uri: Uri) {
        val code = uri.getQueryParameter("code").orEmpty()
        // 授权码去重：同一 code 只提交一次
        if (code.isEmpty() || code == processedCode) {
            sessionRepository.clearPendingCallback()
            return
        }
        processedCode = code

        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = sessionRepository.processLogin(uri)) {
            is PixivAuthResult.Success -> {
                _events.send(AuthEvent.LoginSuccess)
            }
            is PixivAuthResult.Failure.MissingCode ->
                _uiState.update { it.copy(error = "缺少授权码，请重试") }
            is PixivAuthResult.Failure.MissingVerifier ->
                _uiState.update { it.copy(error = "登录状态已过期，请重新登录") }
            is PixivAuthResult.Failure.ServerRejected ->
                _uiState.update {
                    it.copy(error = "登录被服务器拒绝（${result.httpCode}）：${result.message}")
                }
            is PixivAuthResult.Failure.NetworkError ->
                _uiState.update { it.copy(error = "网络错误，请检查网络连接") }
        }
        _uiState.update { it.copy(isLoading = false) }
    }
}
