package com.pixiv.reader.app

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pixiv.api.network.PixivLang
import com.pixiv.reader.app.R
import com.pixiv.reader.app.navigation.PixivNavGraph
import com.pixiv.reader.core.common.PixivLinkType
import com.pixiv.reader.core.common.PixivUrlParser
import com.pixiv.reader.core.common.ThemeMode
import com.pixiv.reader.core.common.localeFor
import com.pixiv.reader.core.common.pixivLanguageCode
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.datastore.readAppLanguageSync
import com.pixiv.reader.core.network.monitor.NetworkMonitor
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NotificationHostState
import com.pixiv.reader.core.ui.component.NotificationType
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.PixivReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay

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
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.FOLLOW_SYSTEM)
            val dynamicColor by userPreferences.dynamicColor.collectAsStateWithLifecycle(
                initialValue = true
            )
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
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
            // 剪贴板 pixiv 链接检测：回前台（ON_RESUME）且已登录、设置开启时，读取剪贴板解析
            // pixiv URL → 弹「打开」提示，点击后才导航（避免复制旧链接被劫持）。
            // 三层触发保证冷启动不漏检（observer 注册可能晚于首次 ON_RESUME）：
            // ① ON_RESUME 检测（key 不含 isLoggedIn，登录态变化不重注册、不丢事件）
            // ② 登录态就绪补检（冷启动登录恢复晚于 ON_RESUME 时兜底）
            // ③ 启动延时兜底（首帧组合晚于 ON_RESUME 事件时兜底）
            // lastOpenedClip 保证已点开过的链接不再提示；未点开的每次回前台都会重新提示。
            // 开关（「我的」页设置）关闭时三层全部跳过。
            val clipboardLinkPrompt by userPreferences.clipboardLinkPrompt.collectAsStateWithLifecycle(
                initialValue = true
            )
            val navController = rememberNavController()
            val lastOpenedClip = remember { mutableStateOf<String?>(null) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val currentIsLoggedIn by rememberUpdatedState(isLoggedIn)
            val currentClipboardPrompt by rememberUpdatedState(clipboardLinkPrompt)
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME &&
                        currentIsLoggedIn && currentClipboardPrompt
                    ) {
                        checkClipboardLink(context, navController, notificationHostState, lastOpenedClip)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            // ② 登录态就绪且页面已 RESUMED 时补检
            LaunchedEffect(isLoggedIn, clipboardLinkPrompt) {
                if (isLoggedIn && clipboardLinkPrompt &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    checkClipboardLink(context, navController, notificationHostState, lastOpenedClip)
                }
            }
            // ③ 冷启动兜底：延时补检一次（剪贴板未变不重复提示）
            LaunchedEffect(Unit) {
                delay(800)
                if (currentIsLoggedIn && currentClipboardPrompt) {
                    checkClipboardLink(context, navController, notificationHostState, lastOpenedClip)
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
                    // 外层 Scaffold 不处理系统栏 insets（沉浸式）：剪贴板链接提示等全局通知
                    // 需自行避让系统导航栏，否则会贴底被手势条遮挡。
                    // 避让 padding 放 contentModifier（仅卡片展示时占位）：若放宿主根节点，
                    // 无通知时空宿主也会占非零高度，导致 bottom bar 上方残留空白
                    snackbarHost = {
                        NotificationHost(
                            notificationHostState,
                            contentModifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                        )
                    },
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        PixivNavGraph(
                            isLoggedIn = isLoggedIn,
                            onLogout = { sessionRepository.logout() },
                            navController = navController,
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

/**
 * 读取剪贴板并解析 pixiv 链接 → 弹「打开」提示，点击后才导航到对应详情页。
 *
 * @param lastOpenedClip 记录**已点击打开过**的剪贴板文本：
 *   未打开的链接每次回前台（ON_RESUME）都会重新提示（切走再回来仍可跳转）；
 *   已点开过的链接不再提示（避免重复打扰）；重新复制新链接立即重新触发。
 */
private fun checkClipboardLink(
    context: Context,
    navController: NavHostController,
    notificationState: NotificationHostState,
    lastOpenedClip: MutableState<String?>,
) {
    val text = readClipboardText(context) ?: return
    val link = PixivUrlParser.parse(text) ?: return
    // 该链接已点击打开过 → 不重复提示
    if (text == lastOpenedClip.value) return
    val (messageRes, route) = when (link.type) {
        PixivLinkType.NOVEL -> R.string.clipboard_link_novel to "novel/${link.id}"
        PixivLinkType.SERIES -> R.string.clipboard_link_series to "novel_series/${link.id}"
        PixivLinkType.ILLUST -> R.string.clipboard_link_illust to "illust/${link.id}"
        PixivLinkType.USER -> R.string.clipboard_link_user to "user/${link.id}"
    }
    notificationState.show(
        text = context.getString(messageRes),
        type = NotificationType.Info,
        actionText = context.getString(R.string.clipboard_link_open),
        onAction = {
            // 点击打开后记录，避免后续回前台反复提示同一链接
            lastOpenedClip.value = text
            navController.navigate(route)
        },
    )
}

/**
 * 读取剪贴板文本：遍历全部 ClipData 项，优先取纯文本项；
 * 部分 App 分享链接以 URI 项存在，其 uri 串本身即为链接，一并兜底。
 */
private fun readClipboardText(context: Context): String? {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) ?: return null
    val items = clip.primaryClip ?: return null
    for (i in 0 until items.itemCount) {
        val item = items.getItemAt(i) ?: continue
        val text = item.text?.toString()
        if (!text.isNullOrBlank()) return text
        val uriText = item.uri?.toString()
        if (!uriText.isNullOrBlank() && uriText.startsWith("http")) return uriText
    }
    return null
}


