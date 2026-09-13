package com.pixiv.reader.feature.user.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.input.ConfirmDialogVariant
import com.pixiv.reader.core.ui.component.input.UpdateReleaseDialog
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.card.ProfileHeader
import com.pixiv.reader.core.ui.component.card.ProfileHeaderData
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.network.fanbox.FanboxHeaderInterceptor
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.MeViewModel

/**
 * 我的 Tab：个人中心/设置页——
 * ProfileHeader（头像/名称/@account/退出登录）+ Expressive 分组设置面板（导航行 / 外观 / 浏览 / 系统 / 关于）。
 * 数据驱动（SettingsCardItem + MeRow），Material 主题，自适应布局。
 * 各区块组件见 [MeAppearanceSection] / [MeBrowseSection] / [MeSystemSection] / [MeAboutSection]。
 *
 * @param onOpenFanbox 打开 FANBOX 原生首页（生态区 FANBOX 条目，已登录 FANBOX 时）
 * @param onOpenFanboxWeb 打开 FANBOX 内置网页（url, title）：未登录登录引导 / 创作者主页等
 */
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
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
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
    val context = LocalContext.current
    val activity = context as? Activity
    // 语言切换防抖：落盘期间忽略重复点击，避免连点导致 DataStore 并发写竞态
    var switchingLanguage by remember { mutableStateOf(false) }
    // 清除缓存确认
    var showClearCache by remember { mutableStateOf(false) }
    // 退出登录确认（提示类，非删除）
    var showLogoutConfirm by remember { mutableStateOf(false) }
    // 小说下载命名模板编辑
    var showFileNameTemplate by remember { mutableStateOf(false) }

    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        snackbarHost = {
            // 沉浸式底部（底部 inset 置 0）后 Scaffold 不再自动避开导航栏，
            // 通知条自行避让（手机端导航栏 inset 已被壳层消费，此处补 0，无双重避让）
            NotificationHost(
                notificationHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        // 沉浸式底部：底部 inset 置 0（内容直通系统导航栏），顶部保留状态栏避让（本页无 TopAppBar）
        contentWindowInsets = WindowInsets.statusBars,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(
            // 消费已应用的 padding，内部 navigationBarsPadding 按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // 沉浸式底部：内容尾部避开系统导航栏（手机端 inset 已被壳层消费，补 0）
                    .navigationBarsPadding()
                    .padding(Spacing.lg),
            ) {
                // ── 个人头部（头像/名称/@account + 退出登录） ──
                ProfileHeader(
                    profile = ProfileHeaderData(user),
                    onClickProfile = { viewModel.ownUid?.let(onOpenUser) },
                    actionLabel = stringResource(R.string.me_logout),
                    onAction = { showLogoutConfirm = true },
                )

                // ── 用户内容管理 ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_content))
                val contentItems = listOf(
                    SettingsCardItem(Icons.Filled.Favorite, stringResource(R.string.me_bookmarks_title), stringResource(R.string.me_bookmarks_desc), onClick = onOpenBookmarks),
                    SettingsCardItem(Icons.Filled.History, stringResource(R.string.me_history_title), stringResource(R.string.me_history_desc), onClick = onOpenHistory),
                    SettingsCardItem(Icons.Filled.Schedule, stringResource(R.string.me_read_later_title), stringResource(R.string.me_read_later_desc), onClick = onOpenReadLater),
                    SettingsCardItem(Icons.Filled.Notifications, stringResource(R.string.me_watchlist_title), stringResource(R.string.me_watchlist_desc), onClick = onOpenWatchlist),
                    SettingsCardItem(Icons.Filled.Download, stringResource(R.string.me_downloads_title), stringResource(R.string.me_downloads_desc), onClick = onOpenDownloads),
                    SettingsCardItem(Icons.Filled.Block, stringResource(R.string.me_blocked_title), stringResource(R.string.me_blocked_desc), onClick = onOpenBlocked),
                )
                MeGroupCard {
                    contentItems.forEachIndexed { index, item ->
                        if (index > 0) MeRowDivider()
                        MeRow(
                            icon = item.icon,
                            title = item.title,
                            subtitle = item.description.takeIf { it.isNotBlank() },
                            trailing = {
                                Icon(
                                    imageVector = item.trailingIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = item.onClick,
                        )
                    }
                }

                // ── pixiv 生态（FANBOX 原生 / COMIC / 私信 / pixivision） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_ecosystem))
                val fanboxTitle = stringResource(R.string.me_fanbox_title)
                val ecoItems = listOf(
                    SettingsCardItem(
                        Icons.Filled.FavoriteBorder,
                        fanboxTitle,
                        stringResource(R.string.me_fanbox_desc),
                        // FANBOX 按登录态分流：已登录（cookie 含 FANBOXSESSID）进原生首页，
                        // 否则进 App 内可见 WebView 登录（不走系统浏览器与网页 SSO 门控）
                        onClick = {
                            if (viewModel.hasFanboxSession()) {
                                onOpenFanbox()
                            } else {
                                // 未登录直达 pixiv 账号登录页（returnTo 回 fanbox），
                                // 移动版首页的登录入口藏在汉堡侧边栏且 WebView 内常点不开
                                onOpenFanboxWeb(FanboxHeaderInterceptor.FANBOX_LOGIN_URL, fanboxTitle)
                            }
                        },
                    ),
                    SettingsCardItem(
                        Icons.Filled.AutoStories,
                        stringResource(R.string.me_comic_title),
                        stringResource(R.string.me_comic_desc),
                        onClick = { viewModel.openEcosystemPage("https://comic.pixiv.net/") },
                    ),
                    SettingsCardItem(
                        Icons.Filled.Email,
                        stringResource(R.string.me_talk_title),
                        stringResource(R.string.me_talk_desc),
                        onClick = { viewModel.openEcosystemPage("https://www.pixiv.net/message.php") },
                    ),
                    SettingsCardItem(
                        Icons.Filled.TravelExplore,
                        stringResource(R.string.me_pixivision_title),
                        stringResource(R.string.me_pixivision_desc),
                        onClick = onOpenPixivision,
                    ),
                )
                MeGroupCard { ecoItems.forEachIndexed { i, item ->
                    if (i > 0) MeRowDivider()
                    MeRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.description.takeIf { it.isNotBlank() },
                        trailing = {
                            Icon(
                                imageVector = item.trailingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = item.onClick,
                    )
                } }

                // ── 外观设置（每项独立卡片：主题模式 / 动态取色 / 语言） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_appearance))
                MeAppearanceSection(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    fontScale = fontScale,
                    appLanguage = appLanguage,
                    switchingLanguage = switchingLanguage,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetDynamicColor = viewModel::setDynamicColor,
                    onSetFontScale = viewModel::setAppFontScale,
                    onSetAppLanguage = { value, onDone ->
                        switchingLanguage = true
                        viewModel.setAppLanguage(value) {
                            switchingLanguage = false
                            onDone()
                        }
                    },
                    onLanguageApplied = { activity?.recreate() },
                )

                // ── 浏览设置（内容/浏览类偏好） ──
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
                    onOpenFileNameTemplate = { showFileNameTemplate = true },
                )

                // ── 系统设置（每项独立卡片：自动更新 / 存储） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_system))
                MeSystemSection(
                    autoUpdate = autoUpdate,
                    novelExportDir = novelExportDir,
                    cacheSize = cacheSize,
                    onSetAutoUpdate = viewModel::setAutoUpdate,
                    onPickExportDir = { uri ->
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        viewModel.setNovelExportDir(uri.toString())
                    },
                    onResetExportDir = viewModel::resetNovelExportDir,
                    onClearCache = { showClearCache = true },
                )

                // ── 关于信息 ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_about))
                MeAboutSection(
                    versionName = viewModel.versionName,
                    onCheckUpdate = { viewModel.checkUpdate() },
                )
            }
        }
    }

    // 清除缓存确认（清空 cacheDir + 离线缓存，不可撤销）
    if (showClearCache) {
        ConfirmDialog(
            title = stringResource(R.string.me_cache_clear_title),
            message = stringResource(R.string.me_cache_clear_message),
            confirmText = stringResource(R.string.me_clear),
            onConfirm = {
                viewModel.clearCache()
                showClearCache = false
            },
            onDismiss = { showClearCache = false },
        )
    }

    // 小说下载命名模板编辑
    if (showFileNameTemplate) {
        NovelFileNameTemplateDialog(
            initialSingle = novelFileNameTemplate,
            initialSeries = novelFileNameTemplateSeries,
            onSave = { single, series ->
                viewModel.setNovelFileNameTemplates(single, series)
                showFileNameTemplate = false
            },
            onReset = {
                viewModel.resetNovelFileNameTemplates()
                showFileNameTemplate = false
            },
            onDismiss = { showFileNameTemplate = false },
        )
    }

    // 新版本更新对话框：changelog 正文（可滚动）+ 前往下载（浏览器打开 Release 页），
    // 走 core:ui 共享组件（与启动自动检查弹层一致）
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

    // 退出登录确认（提示类：Info 图标 + primary 蓝色系，非删除语义）
    if (showLogoutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.me_logout_confirm_title),
            message = stringResource(R.string.me_logout_confirm_message),
            confirmText = stringResource(R.string.me_logout),
            variant = ConfirmDialogVariant.WARNING,
            onConfirm = {
                onLogout()
                showLogoutConfirm = false
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}
