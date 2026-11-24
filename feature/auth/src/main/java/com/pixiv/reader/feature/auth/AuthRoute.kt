package com.pixiv.reader.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthRoute(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    // 只消费打开网页事件；导航统一由会话状态驱动，避免两处同时跳转
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.OpenLoginPage -> openLoginCustomTab(context, event.url)
                AuthEvent.LoginSuccess -> Unit
            }
        }
    }

    // 观察会话状态而非 LoginSuccess 事件：OAuth 成功与进程恢复都需覆盖
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    LoginScreen(
        uiState = uiState,
        onLogin = viewModel::startLogin,
        onRegister = viewModel::startProvisionalAccount,
        onDismissError = viewModel::dismissError,
    )
}
