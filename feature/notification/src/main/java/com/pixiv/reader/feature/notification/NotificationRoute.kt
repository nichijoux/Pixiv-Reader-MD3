package com.pixiv.reader.feature.notification

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.NotificationItem
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

private const val TAG = "Notification"

/**
 * 通知中心：收藏 / 关注 / 评论等消息流（/v1/notification/list）。
 *
 * 整行 / 头像 / 缩略图统一按 `target_url` 解析结果跳转（数据层无独立 userId/workId 字段）；
 * `view_more != null` 的行是分组头，点击进子列表页。
 *
 * @param onBack 返回
 * @param onOpenUser 打开用户主页（target_url = pixiv://user[s]/{id}）
 * @param onOpenIllust 打开插画详情（pixiv://illust[s]/{id}）
 * @param onOpenNovel 打开小说详情（pixiv://novel[s]/{id}）
 * @param onOpenGroup 打开分组子列表（组头 id + 组名）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRoute(
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenGroup: (groupId: Long, title: String?) -> Unit,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notification_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = viewModel::load,
                )

                items.isEmpty() -> EmptyBox(stringResource(R.string.notification_empty))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        if (item.view_more != null) {
                            NotificationGroupCard(
                                item = item,
                                onClick = { onOpenGroup(item.id, item.view_more?.title) },
                            )
                        } else {
                            NotificationRow(
                                item = item,
                                onClick = {
                                    openNotificationTarget(item, onOpenUser, onOpenIllust, onOpenNovel)
                                },
                            )
                        }
                        if (index != items.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 76.dp),
                            )
                        }
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LoadMoreItem(
                                isLoadingMore = isLoadingMore,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }
}

internal fun openNotificationTarget(
    item: NotificationItem,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
) {
    val target = parseNotificationTarget(item.target_url)
    // 真机收集真实 target_url 形态：每条都打，Unknown 另起 W 级便于过滤
    Log.d(TAG, "通知跳转: ${item.target_url} -> $target")
    when (target) {
        is NotificationTarget.User -> onOpenUser(target.userId)
        is NotificationTarget.Illust -> onOpenIllust(target.illustId)
        is NotificationTarget.Novel -> onOpenNovel(target.novelId)
        NotificationTarget.Unknown -> Log.w(TAG, "未识别的通知跳转目标: ${item.target_url}")
    }
}

/** 普通通知行：头像 + 文本 + 相对时间（未读蓝点）+ 右侧作品缩略图。 */
@Composable
internal fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    val content = item.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = null, avatarUrl = content?.left_image, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content?.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val time = relativeTime(item.created_datetime, System.currentTimeMillis())
            if (!item.is_read || time != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.is_read) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    when (time) {
                        is RelativeTime.Res -> Text(
                            text = stringResource(time.resId, time.arg),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        is RelativeTime.Date -> Text(
                            text = time.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        null -> Unit
                    }
                }
            }
        }
        content?.right_image?.let { url ->
            Spacer(Modifier.width(12.dp))
            PixivImage(
                url = url,
                contentDescription = null,
                modifier = Modifier
                    .size(Sizes.s44)
                    .clip(AppShapes.cardSmall),
            )
        }
    }
}

/** 分组头卡片（`view_more != null`）：primaryContainer 色块，点击进子列表页。 */
@Composable
internal fun NotificationGroupCard(item: NotificationItem, onClick: () -> Unit) {
    val more = item.view_more ?: return
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xsPlus)
            .fillMaxWidth()
            .clip(AppShapes.cardLarge)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.mdPlus, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.s40)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.s22),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = more.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (more.unread_exists) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}
