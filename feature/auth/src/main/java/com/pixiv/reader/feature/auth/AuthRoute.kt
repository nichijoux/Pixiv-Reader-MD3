package com.pixiv.reader.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 登录页路由。
 *
 * @param onLoginSuccess 登录成功（导航到首页）
 * @param viewModel 登录状态与网页登录事件的来源
 * @return 无返回值
 */
@Composable
fun AuthRoute(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    // 消费打开网页登录的事件，导航统一由会话状态触发。
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.OpenLoginPage -> openLoginCustomTab(context, event.url)
                AuthEvent.LoginSuccess -> Unit
            }
        }
    }

    // 直接观察会话状态，覆盖 OAuth 成功及进程恢复。
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
