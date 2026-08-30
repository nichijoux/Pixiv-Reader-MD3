package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 阅读器顶栏浮层：返回 / 标题 / 更多菜单（收藏 / 阅读书签 / 追更）。
 * 由外层 [ReaderRoute] 在工具栏可见时叠加显示；本组件内部持有菜单展开状态。
 * 注意：[modifier] 由调用方在 BoxScope 内传入（如 `Modifier.align(Alignment.TopCenter)`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    themeColors: ReaderThemeColors,
    title: String,
    isOffline: Boolean,
    isBookmarked: Boolean,
    isMarked: Boolean,
    isWatchlisted: Boolean,
    canWatch: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleMark: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.topBar),
    ) {
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = themeColors.text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isOffline) {
                        Text(
                            text = stringResource(R.string.reader_local_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.text.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = Spacing.xsPlus),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_cd_back),
                        tint = themeColors.text
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.reader_cd_more),
                            tint = themeColors.text
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isBookmarked) stringResource(R.string.reader_menu_unbookmark) else stringResource(
                                        R.string.reader_menu_bookmark
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onToggleBookmark() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isMarked) stringResource(R.string.reader_menu_remove_mark) else stringResource(
                                        R.string.reader_menu_add_mark
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isMarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onToggleMark() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isWatchlisted) stringResource(R.string.reader_menu_unwatch) else stringResource(
                                        R.string.reader_menu_watch
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                    contentDescription = null,
                                )
                            },
                            enabled = canWatch,
                            onClick = { menuOpen = false; onToggleWatchlist() },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

/**
 * 阅读器底栏浮层：目录 / 搜索 / 设置。
 * 沉浸式：Box 实色背景覆盖导航栏（小白条）区域，内部内容再避让导航栏。
 */
@Composable
internal fun ReaderBottomToolBar(
    themeColors: ReaderThemeColors,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.topBar),
    ) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            ReaderToolBar(
                themeColors = themeColors,
                onToc = onToc,
                onSearch = onSearch,
                onSettings = onSettings,
            )
        }
    }
}

@Composable
private fun ReaderToolBar(
    themeColors: ReaderThemeColors,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeColors.topBar)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToc) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.reader_cd_toc),
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSearch) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.reader_cd_search),
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.reader_cd_settings),
                tint = themeColors.text
            )
        }
    }
}
