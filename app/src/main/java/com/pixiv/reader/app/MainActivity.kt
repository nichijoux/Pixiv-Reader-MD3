package com.pixiv.reader.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.network.PixivLang
import com.pixiv.reader.app.R
import com.pixiv.reader.app.navigation.PixivNavGraph
import com.pixiv.reader.core.common.localeFor
import com.pixiv.reader.core.common.pixivLanguageCode
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.datastore.readAppLanguageSync
import com.pixiv.reader.core.network.monitor.NetworkMonitor
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NotificationType
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.PixivReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * 应用唯一 Activity：装配根导航 + 主题（UserPreferences.themeMode/dynamicColor 生效）。
 * 处理 OAuth 深链回调（onCreate 冷启动 / onNewIntent 热启动均转发到会话层）；
 * 监听网络状态（离线时全局自定义通知）。
 * 沉浸式说明：外层 Scaffold 不处理系统栏 insets，底部导航/各页面自行 padding。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    /**
     * 应用 i18n：在 Hilt 装配前同步读取应用语言设置，按需用 createConfigurationContext 覆盖资源配置；
     * 同时设置 [Locale.setDefault]（供纯函数格式化读取）与网络语言 holder [PixivLang]。
     * 跟随系统时不覆盖，沿用系统配置。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = readAppLanguageSync(newBase)
        val locale = localeFor(lang)
        val effectiveLocale = locale ?: Locale.getDefault()
        Locale.setDefault(effectiveLocale)
        PixivLang.code = pixivLanguageCode(effectiveLocale)
        val wrapped = if (locale != null) {
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            newBase.createConfigurationContext(config)
        } else newBase
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统导航栏透明（无 scrim）：底部导航背景延伸覆盖导航栏，实现沉浸式。
        // 各页面（含沉浸式小说阅读器）自行处理 navigationBarsPadding，不受影响。
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val dynamicColor by userPreferences.dynamicColor.collectAsStateWithLifecycle(
                initialValue = true
            )
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val notificationHostState = rememberNotificationHostState()
            val context = LocalContext.current
            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    notificationHostState.show(
                        text = context.getString(R.string.network_disconnected),
                        type = NotificationType.Error,
                    )
                }
            }
            PixivReaderTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
            ) {
                // 外层 Scaffold 不 padding（系统栏 insets 由内层 NavigationBar / 各页面自行处理），
                // 保证 MainShell 底部导航延伸到系统导航栏（沉浸式），阅读器等沉浸页面不受影响。
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { NotificationHost(notificationHostState) },
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
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


