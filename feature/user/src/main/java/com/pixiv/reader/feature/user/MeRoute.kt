package com.pixiv.reader.feature.user

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.AppLanguage
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ProfileHeader
import com.pixiv.reader.core.ui.component.ProfileHeaderData
import com.pixiv.reader.core.ui.component.SettingsCard
import com.pixiv.reader.core.ui.component.SettingsCardItem

/** 开源仓库地址。 */
private const val OPEN_SOURCE_URL = "https://github.com/nichijoux/Pixiv-Material"

/**
 * 我的 Tab：个人中心/设置页——
 * ProfileHeader（头像/名称/@account/退出登录）+ 分组设置导航卡片 + 外观/语言/系统设置内嵌 + 关于信息。
 * 数据驱动（SettingsCardItem），Material 主题，自适应布局。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeRoute(
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    // 语言切换防抖：落盘期间忽略重复点击，避免连点导致 DataStore 并发写竞态
    var switchingLanguage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            snackbarHostState.showSnackbar(context.getString(msg.res, *msg.args.toTypedArray()))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onAction = onLogout,
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
                    SettingsCardItem(Icons.Filled.Label, stringResource(R.string.me_tags_title), stringResource(R.string.me_tags_desc), onClick = onOpenTags),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Block, stringResource(R.string.me_blocked_title), stringResource(R.string.me_blocked_desc), onClick = onOpenBlocked),
                )

                // ── 账户管理 ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_account))
                SettingsCard(
                    SettingsCardItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(R.string.me_logout),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onLogout,
                    ),
                )

                // ── 外观设置（每项独立卡片：主题模式 / 动态取色 / 语言） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_appearance))
                // 主题模式
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.me_theme_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                0 to R.string.me_theme_follow_system,
                                1 to R.string.me_theme_light,
                                2 to R.string.me_theme_dark,
                            ).forEach { (mode, labelRes) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                    }
                }
                CardSpacer()
                // 动态取色
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.me_dynamic_color),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.me_dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }
                CardSpacer()
                // 语言（切换后重建 Activity 生效）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.me_language),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                AppLanguage.SYSTEM to R.string.me_language_follow_system,
                                AppLanguage.ZH to R.string.me_language_chinese,
                                AppLanguage.EN to R.string.me_language_english,
                            ).forEach { (value, labelRes) ->
                                FilterChip(
                                    selected = appLanguage == value,
                                    enabled = !switchingLanguage,
                                    onClick = {
                                        // 已选语言/切换中不重复触发；写入落盘完成后再重建，避免异步写入被取消
                                        if (!switchingLanguage && appLanguage != value) {
                                            switchingLanguage = true
                                            viewModel.setAppLanguage(value) {
                                                switchingLanguage = false
                                                activity?.recreate()
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                    }
                }

                // ── 系统设置（每项独立卡片：自动更新 / 存储） ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_system))
                // 自动更新
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.me_auto_update),
                            subtitle = stringResource(R.string.me_auto_update_desc),
                            checked = autoUpdate,
                            onCheckedChange = viewModel::setAutoUpdate,
                        )
                    }
                }
                CardSpacer()
                // 存储：清除缓存
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.me_clear_cache), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.me_cache_size, cacheSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = viewModel::clearCache) {
                            Text(stringResource(R.string.me_clear), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // ── 关于信息 ──
                SectionSpacer()
                SectionTitle(stringResource(R.string.me_section_about))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = "Pixiv Reader",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.me_version, viewModel.versionName),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.me_about_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = stringResource(R.string.me_open_source_license),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                CardSpacer()
                // 开源仓库
                SettingsCard(
                    SettingsCardItem(
                        icon = Icons.Filled.OpenInNew,
                        title = stringResource(R.string.me_open_source_repo),
                        trailingIcon = Icons.Filled.OpenInNew,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_URL)))
                            }
                        },
                    ),
                )
                CardSpacer()
                // 检查更新
                SettingsCard(
                    SettingsCardItem(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(R.string.me_check_update),
                        onClick = viewModel::checkUpdate,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun CardSpacer() {
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
