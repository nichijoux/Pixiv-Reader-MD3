package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
 * 阅读器底栏浮层：进度胶囊卡片（点状轨道滑条 + 上/下一章圆钮，独立圆角浮层）
 * 与工具栏（目录/搜索/设置）两个分离浮层。
 * 沉浸式：工具栏背景延伸覆盖导航栏（小白条）区域。
 * 两者一起滑入滑出；拖动松手后回调 [onSeekFinished]（0..1 比例）执行快速跳转。
 *
 * @param progressFraction 当前进度 0..1（翻页模式 = 跨页位置，滚动模式 = 全章百分比）
 * @param onSeekFinished 松手回调（参数 = 目标位置比例）
 * @param canPrevChapter 是否存在上一章（无则禁用上一章圆钮）
 * @param canNextChapter 是否存在下一章（无则禁用下一章圆钮）
 * @param onPrevChapter 跳转上一章
 * @param onNextChapter 跳转下一章
 */
@Composable
internal fun ReaderBottomToolBar(
    themeColors: ReaderThemeColors,
    progressFraction: Float,
    onSeekFinished: (Float) -> Unit,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 进度卡片：独立圆角浮层，背景与工具栏同色
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .background(themeColors.topBar, RoundedCornerShape(20.dp))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ReaderChapterButton(
                enabled = canPrevChapter,
                forward = false,
                themeColors = themeColors,
                onClick = onPrevChapter,
            )
            ReaderDotSlider(
                fraction = progressFraction,
                dotColor = themeColors.text.copy(alpha = 0.45f),
                thumbColor = themeColors.text,
                capsuleColor = themeColors.text.copy(alpha = 0.06f),
                onSeekFinished = onSeekFinished,
                modifier = Modifier.weight(1f),
            )
            ReaderChapterButton(
                enabled = canNextChapter,
                forward = true,
                themeColors = themeColors,
                onClick = onNextChapter,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        // 工具栏：背景覆盖导航栏区域（沉浸）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.topBar),
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ReaderToolBar(
                    themeColors = themeColors,
                    onToc = onToc,
                    onSearch = onSearch,
                    onSettings = onSettings,
                )
            }
        }
    }
}

/**
 * 进度胶囊滑条（点状轨道 + 大圆 thumb）：点击/横向拖拽定位，松手回调 [onSeekFinished]（0..1）。
 * 拖动期间以拖拽值为准——跳转完成前页面进度尚未变化，直接绑定会回弹。
 *
 * @param dotColor 轨道圆点颜色
 * @param thumbColor 滑块圆颜色
 * @param capsuleColor 胶囊背景颜色
 */
@Composable
private fun ReaderDotSlider(
    fraction: Float,
    dotColor: Color,
    thumbColor: Color,
    capsuleColor: Color,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val value = (dragValue ?: fraction).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(CAPSULE_HEIGHT)
            .background(capsuleColor, RoundedCornerShape(percent = 50))
            .pointerInput(Unit) {
                // 点击直接定位并回调
                detectTapGestures { offset ->
                    onSeekFinished((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                // 横向拖拽跟手，松手回调
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragValue =
                            (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragValue?.let(onSeekFinished)
                        dragValue = null
                    },
                    onDragCancel = { dragValue = null },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            // 点状轨道：留出 thumb 半径边距，圆点均匀满铺
            val pad = THUMB_SIZE.toPx() / 2f
            val dotRadius = 2.dp.toPx()
            val gap = 11.dp.toPx()
            val span = size.width - pad * 2
            val count = (span / gap).toInt().coerceAtLeast(1)
            val step = span / count
            val cy = size.height / 2f
            for (i in 0..count) {
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(pad + i * step, cy))
            }
            // thumb：大圆 + 中心小点
            val thumbX = pad + value * span
            val thumbR = THUMB_SIZE.toPx() / 2f
            drawCircle(color = thumbColor, radius = thumbR, center = Offset(thumbX, cy))
            drawCircle(
                color = dotColor,
                radius = 3.dp.toPx(),
                center = Offset(thumbX, cy),
            )
        }
    }
}

/** 胶囊高度。 */
private val CAPSULE_HEIGHT = 52.dp

/** 滑块圆直径。 */
private val THUMB_SIZE = 32.dp

/**
 * 章节跳转圆形按钮（上一章/下一章）：独立圆形浮钮，禁用时降透明度且不可点。
 *
 * @param enabled 是否可点（存在邻章）
 * @param forward true = 下一章（快进图标），false = 上一章（快退图标）
 */
@Composable
private fun ReaderChapterButton(
    enabled: Boolean,
    forward: Boolean,
    themeColors: ReaderThemeColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(CAPSULE_HEIGHT)
            .background(
                color = themeColors.text.copy(alpha = if (enabled) 0.08f else 0.04f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (forward) {
                Icons.Filled.SkipNext
            } else {
                Icons.Filled.SkipPrevious
            },
            contentDescription = stringResource(
                if (forward) R.string.reader_cd_next_chapter else R.string.reader_cd_prev_chapter
            ),
            tint = themeColors.text.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(24.dp),
        )
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
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // 紧凑高度：40dp 触控（标准 48dp 降低观感高度，仍满足最低触控要求）
        IconButton(onClick = onToc, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.reader_cd_toc),
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.reader_cd_search),
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.reader_cd_settings),
                tint = themeColors.text
            )
        }
    }
}
