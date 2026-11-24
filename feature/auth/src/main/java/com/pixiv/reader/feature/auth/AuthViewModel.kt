package com.pixiv.reader.feature.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.auth.PixivAuthResult
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState

    data object Loading : AuthUiState

    data class Error(val message: UiMessage) : AuthUiState
}

sealed interface AuthEvent {
    data class OpenLoginPage(val url: String) : AuthEvent
    data object LoginSuccess : AuthEvent
}

/**
 * OAuth 授权码模式（PKCE）。监听 SessionRepository.pendingOAuthUri（冷启动 / onNewIntent 均触发），
 * 授权码单次去重，成功后发 LoginSuccess 由导航切换。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = sessionRepository.isLoggedIn

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** code 单次性去重：防旋转/重复回调二次提交。 */
    private var processedCode: String? = null

    init {
        viewModelScope.launch {
            sessionRepository.pendingOAuthUri.collect { uri ->
                if (uri != null) processCallback(uri)
            }
        }
    }

    fun startLogin() {
        if (_uiState.value is AuthUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val url = sessionRepository.buildLoginUrl()
            _uiState.value = AuthUiState.Idle
            _events.send(AuthEvent.OpenLoginPage(url))
        }
    }

    fun startProvisionalAccount() {
        viewModelScope.launch {
            _events.send(AuthEvent.OpenLoginPage(sessionRepository.buildProvisionalAccountUrl()))
        }
    }

    fun dismissError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    private suspend fun processCallback(uri: Uri) {
        val code = uri.getQueryParameter("code").orEmpty()
        if (code.isEmpty() || code == processedCode) {
            sessionRepository.clearPendingCallback()
            return
        }
        processedCode = code

        _uiState.value = AuthUiState.Loading
        when (val result = sessionRepository.processLogin(uri)) {
            is PixivAuthResult.Success -> {
                _uiState.value = AuthUiState.Idle
                _events.send(AuthEvent.LoginSuccess)
            }
            is PixivAuthResult.Failure.MissingCode ->
                _uiState.value = AuthUiState.Error(UiMessage(R.string.auth_error_missing_code))
            is PixivAuthResult.Failure.MissingVerifier ->
                _uiState.value = AuthUiState.Error(UiMessage(R.string.auth_error_missing_verifier))
            is PixivAuthResult.Failure.ServerRejected ->
                _uiState.value = AuthUiState.Error(
                    UiMessage(R.string.auth_error_server_rejected, listOf(result.httpCode, result.message)),
                )
            is PixivAuthResult.Failure.NetworkError ->
                _uiState.value = AuthUiState.Error(UiMessage(R.string.auth_error_network))
        }
    }
}
