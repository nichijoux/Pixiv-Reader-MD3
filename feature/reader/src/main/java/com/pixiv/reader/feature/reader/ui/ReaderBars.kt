package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

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
                        onDismissRequest = { menuOpen = false },
                        shape = AppShapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
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
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .background(themeColors.topBar, MaterialTheme.shapes.large)
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

@Composable
private fun ReaderDotSlider(
    fraction: Float,
    dotColor: Color,
    thumbColor: Color,
    capsuleColor: Color,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 拖动期间优先展示拖拽值，避免跳转完成前受外部旧进度回弹
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)
    val value = (dragValue ?: fraction).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(CAPSULE_HEIGHT)
            .background(capsuleColor, AppShapes.pill)
            .pointerInput(Unit) {
                // 经 rememberUpdatedState 读取最新回调，避免 pointerInput(Unit) 捕获陈旧闭包
                detectTapGestures { offset ->
                    if (size.width <= 0) return@detectTapGestures
                    currentOnSeekFinished((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (size.width <= 0) return@detectHorizontalDragGestures
                        dragValue =
                            (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragValue?.let(currentOnSeekFinished)
                        dragValue = null
                    },
                    onDragCancel = { dragValue = null },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
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

private val CAPSULE_HEIGHT = 52.dp

private val THUMB_SIZE = 32.dp

@Composable
private fun ReaderChapterButton(
    enabled: Boolean,
    forward: Boolean,
    themeColors: ReaderThemeColors,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(CAPSULE_HEIGHT),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = themeColors.text.copy(alpha = 0.08f),
            contentColor = themeColors.text,
            disabledContainerColor = themeColors.text.copy(alpha = 0.04f),
            disabledContentColor = themeColors.text.copy(alpha = 0.3f),
        ),
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
