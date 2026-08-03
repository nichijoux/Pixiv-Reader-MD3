package com.pixiv.reader.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 登录页路由。
 *
 * @param onLoginSuccess 登录成功（导航到首页）
 */
@Composable
fun AuthRoute(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    // 打开登录页 / 登录成功事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.OpenLoginPage -> openLoginCustomTab(context, event.url)
                is AuthEvent.LoginSuccess -> onLoginSuccess()
            }
        }
    }

    // 兜底：直接观察登录态（进程恢复等场景）
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
