package com.pixiv.reader.feature.user.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.component.layout.SectionSpacer
import com.pixiv.reader.core.ui.component.layout.SectionTitle
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.input.ConfirmDialogVariant
import com.pixiv.reader.core.ui.component.input.UpdateReleaseDialog
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationType
import com.pixiv.reader.core.ui.component.card.ProfileHeaderData
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.theme.SettingAccentAmber
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.network.fanbox.FanboxHeaderInterceptor
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.MeViewModel
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MeRoute(
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenReadLater: () -> Unit,
    onOpenPixivision: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenFanbox: () -> Unit,
    onOpenFanboxWeb: (String, String) -> Unit,
    onOpenComic: () -> Unit,
    // 主壳「再次点击当前 Tab」回顶信号（计数 key，变化即滚回顶部）
    reselectKey: Int = 0,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val colorStyleEnabled by viewModel.colorStyleEnabled.collectAsStateWithLifecycle()
    val bottomNavBarStyle by viewModel.bottomNavBarStyle.collectAsStateWithLifecycle()
    val fontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val novelDefaultTab by viewModel.novelDefaultTab.collectAsStateWithLifecycle()
    val viewerOrientation by viewModel.viewerOrientation.collectAsStateWithLifecycle()
    val updateRelease by viewModel.updateDialog.collectAsStateWithLifecycle()
    val followSortMode by viewModel.followSortMode.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val novelFileNameTemplate by viewModel.novelFileNameTemplate.collectAsStateWithLifecycle()
    val novelFileNameTemplateSeries by viewModel.novelFileNameTemplateSeries.collectAsStateWithLifecycle()
    val novelExportDir by viewModel.novelExportDir.collectAsStateWithLifecycle()
    val clipboardLinkPrompt by viewModel.clipboardLinkPrompt.collectAsStateWithLifecycle()
    val notificationHostState = rememberNotificationHostState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val exportDirPermFailed = stringResource(R.string.me_export_dir_permission_failed)
    // 落盘期间忽略重复点击，否则连点会让 DataStore 并发写竞态
    var switchingLanguage by remember { mutableStateOf(false) }
    var activeDialog by remember { mutableStateOf<MeDialog?>(null) }

    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        snackbarHost = {
            // 沉浸式底部后 Scaffold 不再自动避开导航栏，通知条自行避让（壳层已消费手机端导航栏 inset，此处补 0）
            NotificationHost(
                notificationHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        // 沉浸式底部：底部 inset 置 0（内容直通系统导航栏），顶部保留状态栏避让（本页无 TopAppBar）
        contentWindowInsets = WindowInsets.statusBars,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(
            // 消费已应用的 padding，内部 navigationBarsPadding 才能按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val listState = rememberLazyListState()
            var lastScrollKey by remember { mutableIntStateOf(reselectKey) }
            LaunchedEffect(reselectKey) {
                if (reselectKey != lastScrollKey) {
                    lastScrollKey = reselectKey
                    listState.animateScrollToItem(0)
                }
            }
            val fanboxTitle = stringResource(R.string.me_fanbox_title)
            val navBarBottomPadding = LocalNavBarBottomPadding.current
            // 按分组拆成 LazyColumn 懒加载项，首帧只组合当前屏可见卡片，避免屏幕外 6 个 PillSegmentedRow 一次子组合卡顿
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    top = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.lg + navBarBottomPadding,
                ),
            ) {
                // ── 账号信息（个人资料 + 退出登录，与下方设置统一分组卡片风格） ──
                item(key = "account") {
                    SectionTitle(stringResource(R.string.me_section_account))
                    MeAccountSection(
                        profile = ProfileHeaderData(user),
                        onClickProfile = viewModel.ownUid?.let { uid -> { onOpenUser(uid) } },
                        onLogout = { activeDialog = MeDialog.LOGOUT },
                    )
                }

                // ── 用户内容管理 ──
                item(key = "content") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_content))
                    val contentItems = listOf(
                        SettingsCardItem(Icons.Filled.Favorite, stringResource(R.string.me_bookmarks_title), stringResource(R.string.me_bookmarks_desc), accent = SettingAccentPink, onClick = onOpenBookmarks),
                        SettingsCardItem(Icons.Filled.History, stringResource(R.string.me_history_title), stringResource(R.string.me_history_desc), accent = SettingAccentBlue, onClick = onOpenHistory),
                        SettingsCardItem(Icons.Filled.Schedule, stringResource(R.string.me_read_later_title), stringResource(R.string.me_read_later_desc), accent = SettingAccentOrange, onClick = onOpenReadLater),
                        SettingsCardItem(Icons.Filled.Notifications, stringResource(R.string.me_watchlist_title), stringResource(R.string.me_watchlist_desc), accent = SettingAccentGreen, onClick = onOpenWatchlist),
                        SettingsCardItem(Icons.Filled.Download, stringResource(R.string.me_downloads_title), stringResource(R.string.me_downloads_desc), accent = SettingAccentViolet, onClick = onOpenDownloads),
                        SettingsCardItem(Icons.Filled.Block, stringResource(R.string.me_blocked_title), stringResource(R.string.me_blocked_desc), accent = SettingAccentTeal, onClick = onOpenBlocked),
                    )
                    MeItemGroup(contentItems)
                }

                // ── pixiv 生态（FANBOX 原生 / COMIC / 私信 / pixivision） ──
                item(key = "ecosystem") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_ecosystem))
                    val ecoItems = listOf(
                        SettingsCardItem(
                            Icons.Filled.FavoriteBorder,
                            fanboxTitle,
                            stringResource(R.string.me_fanbox_desc),
                            accent = SettingAccentAmber,
                            // FANBOX 按登录态分流：已登录进原生首页，否则进 App 内可见 WebView 登录
                            // （不走系统浏览器与网页 SSO 门控）
                            onClick = {
                                if (viewModel.hasFanboxSession()) {
                                    onOpenFanbox()
                                } else {
                                    // 未登录直达 pixiv 账号登录页（returnTo 回 fanbox）：移动版首页的登录入口
                                    // 藏在汉堡侧边栏且 WebView 内常点不开
                                    onOpenFanboxWeb(FanboxHeaderInterceptor.FANBOX_LOGIN_URL, fanboxTitle)
                                }
                            },
                        ),
                        SettingsCardItem(
                            Icons.Filled.AutoStories,
                            stringResource(R.string.me_comic_title),
                            stringResource(R.string.me_comic_desc),
                            accent = SettingAccentOrange,
                            // COMIC 免费内容无需登录，直达原生首页
                            onClick = onOpenComic,
                        ),
                        SettingsCardItem(
                            Icons.Filled.Email,
                            stringResource(R.string.me_talk_title),
                            stringResource(R.string.me_talk_desc),
                            accent = SettingAccentBlue,
                            onClick = { viewModel.openEcosystemPage("https://www.pixiv.net/message.php") },
                        ),
                        SettingsCardItem(
                            Icons.Filled.TravelExplore,
                            stringResource(R.string.me_pixivision_title),
                            stringResource(R.string.me_pixivision_desc),
                            accent = SettingAccentTeal,
                            onClick = onOpenPixivision,
                        ),
                    )
                    MeItemGroup(ecoItems)
                }

                // ── 外观设置（每项独立卡片：主题模式 / 动态取色 / 语言） ──
                item(key = "appearance") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_appearance))
                    MeAppearanceSection(
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        colorStyleEnabled = colorStyleEnabled,
                        bottomNavBarStyle = bottomNavBarStyle,
                        fontScale = fontScale,
                        appLanguage = appLanguage,
                        switchingLanguage = switchingLanguage,
                        onSetThemeMode = viewModel::setThemeMode,
                        onSetDynamicColor = viewModel::setDynamicColor,
                        onSetColorStyleEnabled = viewModel::setColorStyleEnabled,
                        onSetBottomNavBarStyle = viewModel::setBottomNavBarStyle,
                        onSetFontScale = viewModel::setAppFontScale,
                        onSetAppLanguage = { value, onDone ->
                            switchingLanguage = true
                            viewModel.setAppLanguage(value) { success ->
                                switchingLanguage = false
                                if (success) onDone()
                            }
                        },
                        onLanguageApplied = { activity?.recreate() },
                    )
                }

                // ── 浏览设置（内容/浏览类偏好） ──
                item(key = "browse") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_browse))
                    MeBrowseSection(
                        novelDefaultTab = novelDefaultTab,
                        viewerOrientation = viewerOrientation,
                        followSortMode = followSortMode,
                        clipboardLinkPrompt = clipboardLinkPrompt,
                        novelFileNameTemplate = novelFileNameTemplate,
                        onSetNovelDefaultTab = viewModel::setNovelDefaultTab,
                        onSetViewerOrientation = viewModel::setViewerOrientation,
                        onSetFollowSortMode = viewModel::setFollowSortMode,
                        onSetClipboardLinkPrompt = viewModel::setClipboardLinkPrompt,
                        onOpenFileNameTemplate = { activeDialog = MeDialog.FILE_NAME_TEMPLATE },
                    )
                }

                // ── 系统设置（每项独立卡片：自动更新 / 存储） ──
                item(key = "system") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_system))
                    MeSystemSection(
                        autoUpdate = autoUpdate,
                        novelExportDir = novelExportDir,
                        cacheSize = cacheSize,
                        onSetAutoUpdate = viewModel::setAutoUpdate,
                        onPickExportDir = { uri ->
                            // 只有持久化权限授予成功才覆盖下载位置，失败保留原配置
                            val granted = runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            }.isSuccess
                            if (granted) {
                                viewModel.setNovelExportDir(uri.toString())
                            } else {
                                scope.launch {
                                    notificationHostState.show(exportDirPermFailed, NotificationType.Error)
                                }
                            }
                        },
                        onResetExportDir = viewModel::resetNovelExportDir,
                        onClearCache = { activeDialog = MeDialog.CLEAR_CACHE },
                    )
                }

                // ── 关于信息 ──
                item(key = "about") {
                    SectionSpacer()
                    SectionTitle(stringResource(R.string.me_section_about))
                    MeAboutSection(
                        versionName = viewModel.versionName,
                        onCheckUpdate = { viewModel.checkUpdate() },
                    )
                }
            }
        }
    }

    when (activeDialog) {
        MeDialog.CLEAR_CACHE -> ConfirmDialog(
            title = stringResource(R.string.me_cache_clear_title),
            message = stringResource(R.string.me_cache_clear_message),
            confirmText = stringResource(R.string.me_clear),
            onConfirm = {
                viewModel.clearCache()
                activeDialog = null
            },
            onDismiss = { activeDialog = null },
        )

        MeDialog.FILE_NAME_TEMPLATE -> NovelFileNameTemplateDialog(
            initialSingle = novelFileNameTemplate,
            initialSeries = novelFileNameTemplateSeries,
            onSave = { single, series ->
                viewModel.setNovelFileNameTemplates(single, series)
                activeDialog = null
            },
            onReset = {
                viewModel.resetNovelFileNameTemplates()
                activeDialog = null
            },
            onDismiss = { activeDialog = null },
        )

        MeDialog.LOGOUT -> ConfirmDialog(
            title = stringResource(R.string.me_logout_confirm_title),
            message = stringResource(R.string.me_logout_confirm_message),
            confirmText = stringResource(R.string.me_logout),
            variant = ConfirmDialogVariant.WARNING,
            onConfirm = {
                onLogout()
                activeDialog = null
            },
            onDismiss = { activeDialog = null },
        )

        null -> Unit
    }

    // 与启动自动检查弹层共用 core:ui 的 UpdateReleaseDialog
    updateRelease?.let { release ->
        UpdateReleaseDialog(
            release = release,
            title = stringResource(R.string.me_update_available_title, release.tagName),
            confirmText = stringResource(R.string.me_update_download),
            dismissText = stringResource(R.string.me_update_later),
            onConfirm = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                }
                viewModel.dismissUpdateDialog()
            },
            onDismiss = viewModel::dismissUpdateDialog,
        )
    }
}

private enum class MeDialog {
    CLEAR_CACHE,
    FILE_NAME_TEMPLATE,
    LOGOUT,
}
