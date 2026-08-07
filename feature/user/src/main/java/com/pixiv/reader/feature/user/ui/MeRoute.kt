package com.pixiv.reader.feature.user.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ConfirmDialog
import com.pixiv.reader.core.ui.component.ConfirmDialogVariant
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.ProfileHeader
import com.pixiv.reader.core.ui.component.ProfileHeaderData
import com.pixiv.reader.core.ui.component.SettingsCard
import com.pixiv.reader.core.ui.component.SettingsCardItem
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.MeViewModel

/**
 * 我的 Tab：个人中心/设置页——
 * ProfileHeader（头像/名称/@account/退出登录）+ 分组设置导航卡片 + 外观/语言/系统设置内嵌 + 关于信息。
 * 数据驱动（SettingsCardItem），Material 主题，自适应布局。
 * 各区块组件见 [MeAppearanceSection] / [MeBrowseSection] / [MeSystemSection] / [MeAboutSection]。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MeRoute(
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val novelDefaultTab by viewModel.novelDefaultTab.collectAsStateWithLifecycle()
    val viewerOrientation by viewModel.viewerOrientation.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val novelExportDir by viewModel.novelExportDir.collectAsStateWithLifecycle()
    val novelFileNameTemplate by viewModel.novelFileNameTemplate.collectAsStateWithLifecycle()
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

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()), type = msg.type.toNotificationType())
        }
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
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
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Favorite, stringResource(R.string.me_bookmarks_title), stringResource(R.string.me_bookmarks_desc), onClick = onOpenBookmarks),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.History, stringResource(R.string.me_history_title), stringResource(R.string.me_history_desc), onClick = onOpenHistory),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Download, stringResource(R.string.me_downloads_title), stringResource(R.string.me_downloads_desc), onClick = onOpenDownloads),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Block, stringResource(R.string.me_blocked_title), stringResource(R.string.me_blocked_desc), onClick = onOpenBlocked),
                )

                // ── 外观设置（每项独立卡片：主题模式 / 动态取色 / 语言） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_appearance))
                MeAppearanceSection(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    appLanguage = appLanguage,
                    switchingLanguage = switchingLanguage,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetDynamicColor = viewModel::setDynamicColor,
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
                    clipboardLinkPrompt = clipboardLinkPrompt,
                    novelFileNameTemplate = novelFileNameTemplate,
                    onSetNovelDefaultTab = viewModel::setNovelDefaultTab,
                    onSetViewerOrientation = viewModel::setViewerOrientation,
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
                    onCheckUpdate = viewModel::checkUpdate,
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
            initialTemplate = novelFileNameTemplate,
            onSave = { template ->
                viewModel.setNovelFileNameTemplate(template)
                showFileNameTemplate = false
            },
            onReset = {
                viewModel.resetNovelFileNameTemplate()
                showFileNameTemplate = false
            },
            onDismiss = { showFileNameTemplate = false },
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
