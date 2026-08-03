package com.pixiv.reader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.app.navigation.PixivNavGraph
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.monitor.NetworkMonitor
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.ui.theme.PixivReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val dynamicColor by userPreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    snackbarHostState.showSnackbar(
                        message = "网络连接已断开",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
            PixivReaderTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        PixivNavGraph(
                            isLoggedIn = isLoggedIn,
                            onLogout = { sessionRepository.logout() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 转发 OAuth 深链回调（pixiv://account/login?code=…）到会话层 */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (sessionRepository.isOAuthCallback(uri)) {
            sessionRepository.onOAuthCallback(uri)
        }
    }
}


