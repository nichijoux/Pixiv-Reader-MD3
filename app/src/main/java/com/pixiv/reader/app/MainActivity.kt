package com.pixiv.reader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.app.navigation.PixivNavGraph
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.ui.theme.PixivReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
            PixivReaderTheme {
                PixivNavGraph(
                    isLoggedIn = isLoggedIn,
                    onLogout = { sessionRepository.logout() },
                )
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
