package com.pixiv.reader.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.pixiv.api.network.PixivLang
import com.pixiv.reader.app.navigation.PixivNavGraph
import com.pixiv.reader.core.common.parse.PixivLinkType
import com.pixiv.reader.core.common.parse.PixivUrlParser
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.core.common.config.localeFor
import com.pixiv.reader.core.common.config.pixivLanguageCode
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.datastore.readAppLanguageSync
import com.pixiv.reader.core.datastore.readStartupPrefsSync
import com.pixiv.reader.core.network.monitor.NetworkMonitor
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.update.AppRelease
import com.pixiv.reader.core.network.update.AppUpdateChecker
import com.pixiv.reader.core.network.update.AppUpdateVersion
import com.pixiv.reader.core.ui.component.feedback.LocalNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationHostState
import com.pixiv.reader.core.ui.component.feedback.NotificationType
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.input.UpdateReleaseDialog
import com.pixiv.reader.feature.user.R as UserR
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.component.layout.LocalBottomNavBarStyle
import com.pixiv.reader.core.ui.theme.LocalColorStyleEnabled
import com.pixiv.reader.core.ui.theme.PixivReaderTheme
import com.pixiv.reader.app.actions.AppCardActions
import com.pixiv.reader.core.ui.component.actions.CardActionsHost
import com.pixiv.reader.core.ui.component.actions.LocalFavoriteActions
import com.pixiv.reader.core.ui.component.detail.LocalSharedTransitionScope
import com.pixiv.reader.core.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

private const val STATE_PENDING_DEEP_LINK = "pendingContentDeepLink"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var downloadCompletionNotifier: DownloadCompletionNotifier

    @Inject
    lateinit var updateChecker: AppUpdateChecker

    @Inject
    lateinit var appCardActions: AppCardActions

    @Inject
    lateinit var favoriteActions: FavoriteActions

    private var pendingDeepLink by mutableStateOf<Intent?>(null)

    /** 在 Hilt 装配前同步覆盖应用语言，并同步 Locale.getDefault 与网络层 PixivLang.code。 */
    override fun attachBaseContext(newBase: Context) {
        val lang = readAppLanguageSync(newBase)
        val locale = localeFor(lang)
        // 跟随系统时须读基础 Configuration，防沿用同进程上次切换残留的 Locale.getDefault()
        val systemLocale = if (!newBase.resources.configuration.locales.isEmpty) {
            newBase.resources.configuration.locales[0]
        } else {
            Locale.getDefault()
        }
        val effectiveLocale = locale ?: systemLocale
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
        // 透明导航栏无 scrim，各页面自行处理 navigationBarsPadding
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        // 清空 launchIntent.data 防止 NavHost 装载图时提前消费，统一等引导与会话就绪后再导航
        val launchIntent = intent
        if (launchIntent?.data?.scheme == "pixiv") {
            setIntent(Intent(launchIntent).apply { data = null })
        }
        if (savedInstanceState == null) {
            handleIntent(launchIntent)
        } else {
            pendingDeepLink = savedInstanceState.getString(STATE_PENDING_DEEP_LINK)?.let { uri ->
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            }
        }
        setContent {
            val startupPrefs = remember { readStartupPrefsSync(this@MainActivity) }
            var onboardingComplete by rememberSaveable {
                mutableStateOf(startupPrefs.onboardingComplete)
            }
            val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
            val fontScale by userPreferences.appFontScale.collectAsStateWithLifecycle(
                initialValue = startupPrefs.appFontScale,
            )
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = startupPrefs.themeMode,
            )
            val dynamicColor by userPreferences.dynamicColor.collectAsStateWithLifecycle(
                initialValue = startupPrefs.dynamicColor,
            )
            val colorStyleEnabled by userPreferences.colorStyleEnabled.collectAsStateWithLifecycle(
                initialValue = startupPrefs.colorStyleEnabled,
            )
            val bottomNavBarStyle by userPreferences.bottomNavBarStyle.collectAsStateWithLifecycle(
                initialValue = startupPrefs.bottomNavBarStyle,
            )
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemDark
            }
            // 应用内明暗主题覆盖系统时，同步更新系统栏图标明暗
            LaunchedEffect(isDark, isSystemDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
            val notificationHostState = rememberNotificationHostState()
            val updateRelease = remember { mutableStateOf<AppRelease?>(null) }
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val hasActiveDownloads by downloadCompletionNotifier.hasActiveDownloads
                .collectAsStateWithLifecycle()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    downloadCompletionNotifier.refreshNotification()
                }
            }
            // 仅首次出现活跃下载时申请通知权限，避免拒后反复弹窗
            LaunchedEffect(hasActiveDownloads) {
                if (hasActiveDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted && !userPreferences.notificationPermissionPrompted.first()) {
                        userPreferences.setNotificationPermissionPrompted(true)
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            // 仅 STARTED 前台订阅应用内提示，退后台自动取消以交由系统通知栏避免双响
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    downloadCompletionNotifier.events.collect { msg ->
                        notificationHostState.show(
                            context.getString(msg.res, *msg.args.toTypedArray()),
                            type = msg.type.toNotificationType(),
                        )
                    }
                }
            }
            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    notificationHostState.show(
                        text = context.getString(R.string.network_disconnected),
                        type = NotificationType.Error,
                    )
                }
            }
            LaunchedEffect(Unit) {
                if (!userPreferences.autoUpdate.first()) return@LaunchedEffect
                updateChecker.latestRelease().onSuccess { release ->
                    val localVersion = packageManager.getPackageInfo(packageName, 0)
                        .versionName.orEmpty()
                    // release == null 表示仓库尚无 Release（404）
                    if (release != null && AppUpdateVersion.isNewer(localVersion, release.tagName)) {
                        updateRelease.value = release
                    }
                }
            }
            // 三层触发（ON_RESUME / 登录态就绪 / 800ms 兜底）防冷启动首帧或会话恢复晚于首次 ON_RESUME 漏检
            val clipboardLinkPrompt by userPreferences.clipboardLinkPrompt.collectAsStateWithLifecycle(
                initialValue = true
            )
            val navController = rememberNavController()
            val lastOpenedClip = remember { mutableStateOf<String?>(null) }
            val currentIsLoggedIn by rememberUpdatedState(isLoggedIn)
            val currentClipboardPrompt by rememberUpdatedState(clipboardLinkPrompt)
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME &&
                        currentIsLoggedIn && currentClipboardPrompt
                    ) {
                        checkClipboardLink(
                            context,
                            navController,
                            notificationHostState,
                            lastOpenedClip
                        )
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(isLoggedIn, clipboardLinkPrompt) {
                if (isLoggedIn && clipboardLinkPrompt &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    checkClipboardLink(
                        context,
                        navController,
                        notificationHostState,
                        lastOpenedClip
                    )
                }
            }
            LaunchedEffect(Unit) {
                delay(800.milliseconds)
                if (currentIsLoggedIn && currentClipboardPrompt) {
                    checkClipboardLink(
                        context,
                        navController,
                        notificationHostState,
                        lastOpenedClip
                    )
                }
            }
            StandardFontSize(fontScale) {
            PixivReaderTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
            ) {
                CompositionLocalProvider(
                    LocalColorStyleEnabled provides colorStyleEnabled,
                    LocalBottomNavBarStyle provides bottomNavBarStyle,
                ) {
                    // 不消费系统栏 insets，让 MainShell 底栏与阅读器等沉浸页自行处理
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            // SharedTransitionLayout 提升至根部，使内层 Tab 与外层详情跨 NavHost 共用同一坐标系
                            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this,
                                    LocalFavoriteActions provides favoriteActions,
                                    LocalNotificationHostState provides notificationHostState,
                                ) {
                                    CardActionsHost(
                                        actions = appCardActions,
                                        favoriteActions = favoriteActions,
                                    ) {
                                        PixivNavGraph(
                                            isLoggedIn = isLoggedIn,
                                            onboardingComplete = onboardingComplete,
                                            onCompleteOnboarding = {
                                                onboardingComplete = true
                                                lifecycleScope.launch {
                                                    runCatching { userPreferences.setOnboardingComplete(true) }
                                                }
                                            },
                                            onLogout = { sessionRepository.logout() },
                                            navController = navController,
                                            pendingDeepLink = pendingDeepLink,
                                            onDeepLinkHandled = { handled ->
                                                if (pendingDeepLink === handled) pendingDeepLink = null
                                            },
                                        )
                                    }
                                }
                            }
                            // 置于 LocalNotificationHostState 提供域外层，由根宿主统一在右上角安全区渲染
                            NotificationHost(
                                state = notificationHostState,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Top + WindowInsetsSides.End,
                                        ),
                                    )
                                    .padding(top = Spacing.sm, end = Spacing.md),
                            )
                        }
                    }
                    updateRelease.value?.let { release ->
                        UpdateReleaseDialog(
                            release = release,
                            title = stringResource(UserR.string.me_update_available_title, release.tagName),
                            confirmText = stringResource(UserR.string.me_update_download),
                            dismissText = stringResource(UserR.string.me_update_later),
                            onConfirm = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                                }
                                updateRelease.value = null
                            },
                            onDismiss = { updateRelease.value = null },
                        )
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // OAuth 回调已由会话层消费，仅持久化待导航的内容深链
        pendingDeepLink?.dataString?.let { outState.putString(STATE_PENDING_DEEP_LINK, it) }
    }

@Composable
private fun StandardFontSize(fontScale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, fontScale = fontScale),
        content = content,
    )
}

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "pixiv") return
        if (uri.host == "account" && uri.path == "/login") {
            // OAuth 库可能只校验 scheme，先按 host/path 区分登录回调 pixiv://account/login 与作品深链
            if (sessionRepository.isOAuthCallback(uri)) sessionRepository.onOAuthCallback(uri)
        } else if (uri.host in setOf("illust", "novel", "reader", "user", "downloads")) {
            // 重建仅含 URI 的 Intent，防外部任务标志触发 Navigation 重启 Activity
            pendingDeepLink = Intent(Intent.ACTION_VIEW, uri)
        }
    }
}

private fun checkClipboardLink(
    context: Context,
    navController: NavHostController,
    notificationState: NotificationHostState,
    lastOpenedClip: MutableState<String?>,
) {
    val text = readClipboardText(context) ?: return
    val link = PixivUrlParser.parse(text) ?: return
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
            // 仅记录已点开的链接：未点开回前台继续提示，已点开不再重复打扰
            lastOpenedClip.value = text
            navController.navigate(route)
        },
    )
}

private fun readClipboardText(context: Context): String? {
    val clip =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) ?: return null
    val items = clip.primaryClip ?: return null
    for (i in 0 until items.itemCount) {
        val item = items.getItemAt(i) ?: continue
        val text = item.text?.toString()
        if (!text.isNullOrBlank()) return text
        // 部分 App 分享链接存在 uri 项而非 text 项
        val uriText = item.uri?.toString()
        if (!uriText.isNullOrBlank() && uriText.startsWith("http")) return uriText
    }
    return null
}


