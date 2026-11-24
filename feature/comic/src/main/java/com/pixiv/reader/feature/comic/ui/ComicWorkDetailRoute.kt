package com.pixiv.reader.feature.comic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import android.graphics.Color as AndroidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.ComicEpisodeEntry
import com.pixiv.api.model.ComicWork
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.NotificationHostState
import com.pixiv.reader.core.ui.component.feedback.NotificationType
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.common.LoadState
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R
import com.pixiv.reader.feature.comic.state.ComicEpisodesState
import com.pixiv.reader.feature.comic.state.ComicWorkDetailViewModel

/** 免费章节点进阅读器，付费章节只提示锁定（服务端 sales_type 决定）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicWorkDetailRoute(
    onBack: () -> Unit,
    onOpenReader: (Long) -> Unit,
    viewModel: ComicWorkDetailViewModel = hiltViewModel(),
) {
    val work by viewModel.work.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val notificationHost = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHost)

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = (work as? LoadState.Content)?.data?.name.orEmpty().ifEmpty { stringResource(R.string.comic_title) },
                onBack = onBack,
            )
        },
        snackbarHost = { NotificationHost(notificationHost) },
    ) { padding ->
        when (val currentWork = work) {
            LoadState.Loading -> LoadingBox(Modifier.padding(padding))
            is LoadState.Error -> ErrorBox(
                message = currentWork.message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            is LoadState.Content -> AdaptiveContentBox(modifier = Modifier.padding(padding).fillMaxSize()) {
                ComicWorkDetailContent(
                    data = currentWork.data,
                    episodes = episodes,
                    notificationHost = notificationHost,
                    onRetry = viewModel::retry,
                    onToggleOrder = viewModel::toggleOrder,
                    onOpenReader = onOpenReader,
                )
            }
        }
    }
}

@Composable
private fun ComicWorkDetailContent(
    data: ComicWork,
    episodes: ComicEpisodesState,
    notificationHost: NotificationHostState,
    onRetry: () -> Unit,
    onToggleOrder: () -> Unit,
    onOpenReader: (Long) -> Unit,
) {
    // 文案在组合期解析（点击回调 lambda 内不可调 stringResource）
    val lockedToast = stringResource(R.string.comic_locked_toast)
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "comic_work_header") { ComicWorkHeader(data) }
        if (data.tags.orEmpty().isNotEmpty() || data.categories.orEmpty().isNotEmpty()) {
            item(key = "comic_work_tags") { ComicTagRow(data) }
        }
        if (!cleanComicDescription(data.description).isNullOrEmpty()) {
            item(key = "comic_work_desc") { ComicDescription(data) }
        }
        data.firstEpisode?.takeIf { it.isReadable }?.let { first ->
            item(key = "comic_work_read_first") {
                Button(onClick = { onOpenReader(first.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.comic_work_read_first))
                }
            }
        }
        item(key = "comic_work_episodes_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.comic_work_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleOrder) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.comic_work_order_cd),
                    )
                }
            }
        }
        when (val epState = episodes.loadState) {
            LoadState.Loading ->
                item(key = "comic_work_episodes_loading") { LoadingBox() }
            is LoadState.Error ->
                item(key = "comic_work_episodes_error") {
                    ErrorBox(message = epState.message, onRetry = onRetry)
                }
            is LoadState.Content -> if (epState.data.isEmpty()) {
                item(key = "comic_work_episodes_empty") {
                    EmptyBox(text = stringResource(R.string.comic_empty_episodes))
                }
            } else {
                items(epState.data, key = { entry -> entry.episode?.id ?: entry.hashCode() }) { entry ->
                    ComicEpisodeRow(
                        entry = entry,
                        onOpenReader = onOpenReader,
                        onLocked = {
                            notificationHost.show(lockedToast, type = NotificationType.Error)
                        },
                        // 正序/倒序切换时整列条目平滑重排（该场景收益最高）
                        modifier = Modifier.animateItem(
                            fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                            placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                        ),
                    )
                }
            }
        }
    }
}

/** 封面 + 名称 / 作者 / 话数 / 点赞。 */
@Composable
private fun ComicWorkHeader(work: ComicWork) {
    Row(verticalAlignment = Alignment.Top) {
        PixivImage(
            url = work.coverUrl.ifEmpty { work.image?.thumbnail },
            contentDescription = work.name,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.71f),
        )
        Column(Modifier.padding(start = Spacing.lg)) {
            Text(
                text = work.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = work.author.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.comic_stories_count, work.storiesCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(
                text = stringResource(
                    R.string.comic_likes_count,
                    formatCount(work.likeCount.toLong()),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ComicTagRow(work: ComicWork) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(work.categories.orEmpty(), key = { "cat-${it.id}" }) { category ->
            // 服务端下发的是任意色值：解析失败或为空时回退主题容器色（不再硬编码 #888888）
            val parsedTagColor = remember(category.color) {
                runCatching {
                    category.color?.takeIf { it.isNotBlank() }
                        ?.let { Color(AndroidColor.parseColor(it)) }
                }.getOrNull()
            }
            val tagColor = parsedTagColor ?: MaterialTheme.colorScheme.secondaryContainer
            // 文字色按底色亮度取反：亮度 > 0.179 时黑字/白字均 ≥4.5:1，避免服务端浅色分类上白字看不清
            val onTagColor = when {
                parsedTagColor == null -> MaterialTheme.colorScheme.onSecondaryContainer
                tagColor.luminance() > 0.179f -> Color(0xFF1B1B1B)
                else -> Color.White
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = tagColor,
            ) {
                Text(
                    text = category.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onTagColor,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
        items(work.tags.orEmpty(), key = { "tag-${it.id}" }) { tag ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = tag.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ComicDescription(work: ComicWork) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = cleanComicDescription(work.description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded },
    )
}

/** 免费可点 / 付费锁定 / 未公开置灰三种态。 */
@Composable
private fun ComicEpisodeRow(
    entry: ComicEpisodeEntry,
    onOpenReader: (Long) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episode = entry.episode ?: return
    val readable = entry.state == "readable" && episode.isReadable
    val unpublished = entry.state != "readable"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !unpublished) {
                if (readable) onOpenReader(episode.id) else onLocked()
            }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.numberingTitle.orEmpty().ifEmpty { episode.subTitle.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.subTitle?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            readable -> Unit
            unpublished -> Text(
                text = stringResource(R.string.comic_not_publishing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.comic_locked_badge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = stringResource(R.string.comic_locked_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}
