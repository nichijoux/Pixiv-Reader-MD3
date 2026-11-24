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
import com.pixiv.reader.core.datastore.readOnboardingCompleteSync
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

    @Inject
    lateinit var downloadCompletionNotifier: DownloadCompletionNotifier

    @Inject
    lateinit var updateChecker: AppUpdateChecker

    /** 卡片本地动作（稍后再看 / 就地屏蔽）：经 [CardActionsHost] 下发给所有卡片。 */
    @Inject
    lateinit var appCardActions: AppCardActions

    /** 全局收藏与关注状态宿主（同步所有列表卡片与详情页状态）。 */
    @Inject
    lateinit var favoriteActions: FavoriteActions

    private var pendingDeepLink by mutableStateOf<Intent?>(null)

    /**
     * 应用 i18n：在 Hilt 装配前同步读取应用语言设置，按需用 createConfigurationContext 覆盖资源配置；
     * 同时设置 [Locale.setDefault]（供纯函数格式化读取）与网络语言 holder [PixivLang]。
     * 跟随系统时不覆盖，沿用系统配置。
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = readAppLanguageSync(newBase)
        val locale = localeFor(lang)
        // 跟随系统时必须读取基础 Configuration 的主系统 Locale，防止沿用同进程上次切换留下的 Locale.getDefault()。
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

    /**
     * 初始化窗口、应用导航，并分发首次启动的链接。
     *
     * @param savedInstanceState 上次保存的 Activity 状态；首次启动时为空
     * @return 无返回值
     */
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
        // 统一由根导航在引导及会话就绪后处理内容链接，防止 NavHost 装载图时提前消费。
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
            // 每个 Activity 实例只读取一次；完成引导后立即更新内存状态，再异步写盘。
            var onboardingComplete by rememberSaveable {
                mutableStateOf(readOnboardingCompleteSync(this@MainActivity))
            }
            val isLoggedIn by sessionRepository.isLoggedIn.collectAsStateWithLifecycle()
            val fontScale by userPreferences.appFontScale.collectAsStateWithLifecycle(initialValue = 1f)
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.FOLLOW_SYSTEM)
            val dynamicColor by userPreferences.dynamicColor.collectAsStateWithLifecycle(
                initialValue = true
            )
            // 彩色风格开关：关闭（默认）时各页点缀色回退主题 primary（纯 M3 单色）
            val colorStyleEnabled by userPreferences.colorStyleEnabled.collectAsStateWithLifecycle(
                initialValue = false
            )
            // 手机端底部导航栏样式：默认 M3 Expressive 贴边短栏，可在外观设置切至悬浮胶囊
            val bottomNavBarStyle by userPreferences.bottomNavBarStyle.collectAsStateWithLifecycle(
                initialValue = BottomNavBarStyle.DOCKED
            )
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemDark
            }
            // 应用内明暗主题覆盖系统配置时，系统栏仍需匹配 Compose 背景的明暗。
            LaunchedEffect(isDark, isSystemDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
            val notificationHostState = rememberNotificationHostState()
            // 启动自动检查到的新版本 Release：非空时弹更新对话框（与「检测更新」按钮一致）
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
            // 首次出现活跃下载任务时请求 Android 13+ 系统通知权限（仅引导一次，避免拒后反复弹窗）
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
            // 仅前台（STARTED）订阅应用内完成/失败提示流；退后台自动取消订阅，交由系统通知栏提醒
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
            // 启动自动检查更新：开关（「我的」页-系统设置）开启时每进程冷启动静默查一次
            // GitHub Release，有新版弹更新对话框（changelog + 前往下载，与「检测更新」按钮一致，
            // 对话框渲染见下方主题内 UpdateReleaseDialog）；失败/已最新均静默不打扰。
            LaunchedEffect(Unit) {
                if (!userPreferences.autoUpdate.first()) return@LaunchedEffect
                updateChecker.latestRelease().onSuccess { release ->
                    val localVersion = packageManager.getPackageInfo(packageName, 0)
                        .versionName.orEmpty()
                    // release == null：仓库尚无发布（404）→ 静默视为无更新
                    if (release != null && AppUpdateVersion.isNewer(localVersion, release.tagName)) {
                        updateRelease.value = release
                    }
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
            // ② 登录态就绪且页面已 RESUMED 时补检
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
            // ③ 冷启动兜底：延时补检一次（剪贴板未变不重复提示）
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
            // 应用字号缩放：默认 1.0 = 系统「标准」档，我的页-外观滑动条可调（0.8~1.3）
            StandardFontSize(fontScale) {
            PixivReaderTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
            ) {
                // 彩色风格与底部导航栏样式通篇下发，全局即时生效；包在内容最外层保证全路由可见
                CompositionLocalProvider(
                    LocalColorStyleEnabled provides colorStyleEnabled,
                    LocalBottomNavBarStyle provides bottomNavBarStyle,
                ) {
                    // 外层 Scaffold 不 padding（系统栏 insets 由内层 NavigationBar / 各页面自行处理），
                    // 保证 MainShell 底部导航延伸到系统导航栏（沉浸式），阅读器等沉浸页面不受影响。
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            // hero 过渡 scope 提升到应用根部：内层 Tab 卡片（MainShell 内的 NavHost）
                            // 与外层详情路由（顶层 NavHost）跨 NavHost 层级共享元素，必须同一个
                            // SharedTransitionLayout 才有共同坐标系；scope 经 CompositionLocal 下发，
                            // 卡片 / 详情组件内部自行消费，页面层无需透传
                            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this,
                                    LocalFavoriteActions provides favoriteActions,
                                    LocalNotificationHostState provides notificationHostState,
                                ) {
                                    // 卡片长按动作全局宿主（稍后再看 / 就地屏蔽）：所有列表卡片自动获得长按能力
                                    CardActionsHost(actions = appCardActions) {
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
                            // 统一应用内通知堆叠宿主：固定右上角并避让状态栏与右侧刘海/挖孔，
                            // 放在 LocalNotificationHostState 提供域外层以便根宿主接管渲染
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
                    // 启动自动检查到新版本：弹与「检测更新」按钮一致的更新对话框
                    // （changelog 正文 + 前往下载浏览器打开 Release 页 / 下次再说）
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

    /**
     * 接收已有实例的 OAuth 回调或内容链接。
     *
     * @param intent Android 交给现有 Activity 的 Intent
     * @return 无返回值
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * 保存等待会话就绪的内容 URI，以便 Activity 重建后继续导航。
     *
     * @param outState Android 提供的状态容器
     * @return 无返回值
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // OAuth 回调由会话层消费，状态容器只保留未导航的内容链接。
        pendingDeepLink?.dataString?.let { outState.putString(STATE_PENDING_DEEP_LINK, it) }
    }

/** 应用字号缩放：覆盖 fontScale（屏幕密度不变），默认 1.0 = 系统「标准」档，
 *  我的页-外观滑动条可调；应用内所有文字（含 Dialog/Popup，继承 CompositionLocal）即时生效。 */
@Composable
private fun StandardFontSize(fontScale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, fontScale = fontScale),
        content = content,
    )
}

    /**
     * 将 OAuth 回调交给会话层，内容链接交给已就绪的根导航。
     *
     * @param intent Activity 收到的启动或热启动 Intent
     * @return 无返回值
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "pixiv") return
        if (uri.host == "account" && uri.path == "/login") {
            // OAuth 库的回调判断可能只检查 scheme，先区分登录路径与作品路径。
            if (sessionRepository.isOAuthCallback(uri)) sessionRepository.onOAuthCallback(uri)
        } else if (uri.host in setOf("illust", "novel", "reader", "user", "downloads")) {
            // 只传 URI，避免外部 Intent 的任务标志触发 Navigation 重启 Activity。
            pendingDeepLink = Intent(Intent.ACTION_VIEW, uri)
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
    val clip =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager) ?: return null
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


