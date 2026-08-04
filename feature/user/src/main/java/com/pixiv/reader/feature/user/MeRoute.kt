package com.pixiv.reader.feature.user

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ProfileHeader
import com.pixiv.reader.core.ui.component.ProfileHeaderData
import com.pixiv.reader.core.ui.component.SettingsCard
import com.pixiv.reader.core.ui.component.SettingsCardItem

/** 开源仓库地址。 */
private const val OPEN_SOURCE_URL = "https://github.com/nichijoux/Pixiv-Material"

/**
 * 我的 Tab：个人中心/设置页——
 * ProfileHeader（头像/名称/@account/个人主页）+ 分组设置导航卡片 + 关于信息。
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
    onOpenSettings: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
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
                    actionLabel = "退出登录",
                    onAction = onLogout,
                )

                // ── 用户内容管理 ──
                SectionSpacer()
                SectionTitle("用户内容管理")
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Favorite, "我的收藏", "收藏的作品", onClick = onOpenBookmarks),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.History, "浏览历史", "浏览过的作品", onClick = onOpenHistory),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Download, "下载管理", "已下载的内容", onClick = onOpenDownloads),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Label, "收藏标签", "按标签浏览收藏", onClick = onOpenTags),
                )
                CardSpacer()
                SettingsCard(
                    SettingsCardItem(Icons.Filled.Block, "屏蔽管理", "已屏蔽的用户与标签", onClick = onOpenBlocked),
                )

                // ── 账户管理 ──
                SectionSpacer()
                SectionTitle("账户管理")
                SettingsCard(
                    SettingsCardItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "退出登录",
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onLogout,
                    ),
                )

                // ── 外观设置（内嵌，不跳转） ──
                SectionSpacer()
                SectionTitle("外观设置")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "主题模式",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "动态取色",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "Android 12+ 使用壁纸取色",
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
                }

                // ── 系统设置 ──
                SectionSpacer()
                SectionTitle("系统设置")
                SettingsCard(
                    SettingsCardItem(
                        icon = Icons.Filled.Download,
                        title = "缓存与更新",
                        description = "清除缓存、自动更新",
                        onClick = onOpenSettings,
                    ),
                )

                // ── 关于信息 ──
                SectionSpacer()
                SectionTitle("关于信息")
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
                                    text = "版本 ${viewModel.versionName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "一款开源 Pixiv Android 客户端：浏览推荐/排行/发现，查看插画与小说，" +
                                "支持收藏、评论、下载导出、离线阅读等能力。数据来自 pixiv 官方接口，仅供学习交流使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_URL)))
                                    }
                                }
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "开源仓库",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = "开源许可：项目基于 pixiv 开放接口，仅供学习与交流使用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
