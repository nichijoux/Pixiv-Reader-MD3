package com.pixiv.reader.feature.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.auth.PixivAuthResult
import com.pixiv.reader.core.common.UiMessage
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

/** 登录页 UI 状态（加载中 / 错误文案事件）。 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: UiMessage? = null,
)

/** 登录事件：打开网页登录 / 登录成功（导航切 main）。 */
sealed interface AuthEvent {
    data class OpenLoginPage(val url: String) : AuthEvent
    data object LoginSuccess : AuthEvent
}

/**
 * 登录 ViewModel：OAuth 授权码模式（PKCE）。
 * 监听 SessionRepository.pendingOAuthUri（冷启动 / onNewIntent 均触发），
 * 授权码单次去重，成功后发出 LoginSuccess 事件由导航切换。
 */
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

    /** 发起 OAuth 登录：构建授权链接并发 OpenLoginPage 事件（由 UI 打开网页）。 */
    fun startLogin() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val url = sessionRepository.buildLoginUrl()
            _uiState.update { it.copy(isLoading = false) }
            _events.send(AuthEvent.OpenLoginPage(url))
        }
    }

    /** 申请 pixiv 官方「临时账号」（provisional account），同样走网页授权。 */
    fun startProvisionalAccount() {
        viewModelScope.launch {
            _events.send(AuthEvent.OpenLoginPage(sessionRepository.buildProvisionalAccountUrl()))
        }
    }

    /** 清除错误文案。 */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 处理 OAuth 回调：提取 code 交换 token；同一 code 只提交一次。 */
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
                _uiState.update { it.copy(error = UiMessage(R.string.auth_error_missing_code)) }
            is PixivAuthResult.Failure.MissingVerifier ->
                _uiState.update { it.copy(error = UiMessage(R.string.auth_error_missing_verifier)) }
            is PixivAuthResult.Failure.ServerRejected ->
                _uiState.update {
                    it.copy(error = UiMessage(R.string.auth_error_server_rejected, listOf(result.httpCode, result.message)))
                }
            is PixivAuthResult.Failure.NetworkError ->
                _uiState.update { it.copy(error = UiMessage(R.string.auth_error_network)) }
        }
        _uiState.update { it.copy(isLoading = false) }
    }
}
