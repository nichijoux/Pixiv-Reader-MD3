package com.pixiv.reader.feature.talk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.TalkMessage
import com.pixiv.api.model.TalkRoom
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.talk.R
import com.pixiv.reader.feature.talk.state.TalkListViewModel
import com.pixiv.reader.feature.talk.state.TalkRoomViewModel

/**
 * 私信会话列表（路由 `talk`，只读）。
 *
 * @param onBack 返回
 * @param onOpenRoom 打开会话消息历史（参数为会话 id）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkListRoute(
    onBack: () -> Unit,
    onOpenRoom: (Long) -> Unit,
    viewModel: TalkListViewModel = hiltViewModel(),
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.talk_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.talk_cd_back),
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
                isLoading && rooms.isEmpty() -> LoadingBox()
                error != null && rooms.isEmpty() -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = viewModel::load,
                )
                rooms.isEmpty() -> EmptyBox(stringResource(R.string.talk_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    items(rooms, key = { it.id }) { room ->
                        TalkRow(
                            partnerName = room.partnerUser?.name.orEmpty(),
                            avatarUrl = room.partnerUser?.profile_image_urls?.best(),
                            lastMessage = room.lastMessage?.content.orEmpty(),
                            date = room.lastMessage?.createdAt?.take(10).orEmpty(),
                            unread = room.unread,
                            onClick = { onOpenRoom(room.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 会话行：头像 + 对方名 + 最后一条消息 + 日期 + 未读点。 */
@Composable
private fun TalkRow(
    partnerName: String,
    avatarUrl: String?,
    lastMessage: String,
    date: String,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = partnerName,
            avatarUrl = avatarUrl,
            modifier = Modifier.size(Sizes.s44),
        )
        Column(
            modifier = Modifier
                .padding(start = Spacing.md)
                .weight(1f),
        ) {
            Text(
                text = partnerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (date.isNotBlank()) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unread) {
            Box(
                modifier = Modifier
                    .padding(start = Spacing.sm)
                    .size(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 私信消息历史（路由 `talk_room/{roomId}`，只读）：正序气泡列表 + 底部只读提示条。
 *
 * @param roomId 会话 id
 * @param partnerName 对方名称（路由可选参数，顶栏展示）
 * @param onBack 返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkRoomRoute(
    roomId: Long,
    partnerName: String?,
    onBack: () -> Unit,
    viewModel: TalkRoomViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val myUid = 0L // 只读模式：发送者身份由消息内 user 判断（未知视为对方）

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = partnerName ?: stringResource(R.string.talk_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.talk_cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            // 只读模式提示条
            Text(
                text = stringResource(R.string.talk_read_only_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.md),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && messages.isEmpty() -> LoadingBox()
                error != null && messages.isEmpty() -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = viewModel::load,
                )
                messages.isEmpty() -> EmptyBox(stringResource(R.string.talk_room_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(messages, key = { it.id }) { message ->
                        TalkMessageBubble(message = message)
                    }
                }
            }
        }
    }
}

/** 消息气泡（只读展示；sender 头像 + 文本气泡，右对齐为本人消息的预留位）。 */
@Composable
private fun TalkMessageBubble(message: TalkMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            name = message.user?.name,
            avatarUrl = message.user?.profile_image_urls?.best(),
            modifier = Modifier.size(Sizes.s28),
        )
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            message.user?.name?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
            ) {
                Text(
                    text = message.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message.createdAt?.take(10)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}
