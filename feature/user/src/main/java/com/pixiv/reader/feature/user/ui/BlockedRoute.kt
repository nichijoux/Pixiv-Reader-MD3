package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.BlockedViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 屏蔽管理：卡片分组 + pill 标签——
 * 1. 本地过滤标签（推荐/搜索过滤，可增删/清空）
 * 2. 服务端屏蔽（标签展示 / 用户可取消）
 *
 * @param onBack 返回
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockedRoute(
    onBack: () -> Unit,
    viewModel: BlockedViewModel = hiltViewModel(),
) {
    val mutedUsers by viewModel.mutedUsers.collectAsStateWithLifecycle()
    val mutedTags by viewModel.mutedTags.collectAsStateWithLifecycle()
    val localTags by viewModel.localTags.collectAsStateWithLifecycle()
    val localBlockedWorks by viewModel.localBlockedWorks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    // 本地过滤标签清空 / 单条删除确认
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        topBar = {
            BackTopAppBar(title = stringResource(R.string.blocked_title), onBack = onBack) {
                if (localTags.isNotEmpty()) {
                    // 清空本地过滤标签：点击弹确认框
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.blocked_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading -> LoadingBox()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // ── 本地过滤标签卡片 ──
                    item(key = "local_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.lg)) {
                                Text(
                                    text = stringResource(R.string.blocked_local_tags_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.blocked_local_tags_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                                // 添加输入
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    OutlinedTextField(
                                        value = draft,
                                        onValueChange = { draft = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(stringResource(R.string.blocked_local_tags_input_hint)) },
                                        singleLine = true,
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            viewModel.addLocalTag(draft)
                                            draft = ""
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add))
                                    }
                                }
                                // 标签列表
                                Spacer(Modifier.height(12.dp))
                                if (localTags.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.blocked_local_tags_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    ) {
                                        localTags.forEach { tag ->
                                            TagPill(text = tag, onRemove = { pendingDeleteTag = tag })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 本地屏蔽作品卡片（卡片长按就地屏蔽的作品，可解除） ──
                    item(key = "local_works_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.lg)) {
                                Text(
                                    text = stringResource(R.string.blocked_local_works_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.blocked_local_works_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                                if (localBlockedWorks.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.blocked_local_works_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.md),
                                    )
                                } else {
                                    Spacer(Modifier.height(12.dp))
                                    // 键格式 "illust:123" / "novel:456"：类型徽标 + id + 解除按钮
                                    localBlockedWorks.sorted().forEach { key ->
                                        val parts = key.split(':')
                                        val type = parts.getOrNull(0).orEmpty()
                                        val id = parts.getOrNull(1).orEmpty()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = Spacing.xs),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = type,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier
                                                    .clip(AppShapes.small)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .padding(horizontal = Spacing.sm, vertical = 3.dp),
                                            )
                                            Text(
                                                text = "ID $id",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier
                                                    .padding(start = Spacing.md)
                                                    .weight(1f),
                                            )
                                            TextButton(onClick = { viewModel.removeLocalBlockedWork(key) }) {
                                                Text(stringResource(R.string.blocked_unblock), color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ── 服务端屏蔽卡片 ──
                    item(key = "server_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.lg)) {
                                Text(
                                    text = stringResource(R.string.blocked_server_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.blocked_server_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                                if (mutedTags.isEmpty() && mutedUsers.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.blocked_server_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.md),
                                    )
                                } else {
                                    // 服务端标签
                                    if (mutedTags.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        ) {
                                            mutedTags.forEach { tag ->
                                                TagPill(
                                                    text = tag.translated_name ?: tag.tag_name.orEmpty(),
                                                    onRemove = null,
                                                )
                                            }
                                        }
                                    }
                                    // 服务端用户
                                    if (mutedUsers.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        mutedUsers.forEach { muted ->
                                            val u = muted.user
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = Spacing.xs),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                UserAvatar(
                                                    name = u?.name,
                                                    avatarUrl = u?.profile_image_urls?.best(),
                                                    modifier = Modifier.size(Sizes.s36),
                                                )
                                                Text(
                                                    text = u?.name.orEmpty(),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .padding(start = Spacing.md)
                                                        .weight(1f),
                                                )
                                                TextButton(onClick = { viewModel.unblockUser(muted) }) {
                                                    Text(stringResource(R.string.blocked_unblock), color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 清空本地过滤标签确认
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.blocked_clear_title),
            message = stringResource(R.string.blocked_clear_message),
            confirmText = stringResource(R.string.blocked_clear),
            onConfirm = {
                viewModel.clearLocalTags()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
    // 单条本地过滤标签删除确认
    pendingDeleteTag?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.blocked_delete_title),
            message = stringResource(R.string.blocked_delete_message, tag),
            confirmText = stringResource(com.pixiv.reader.core.ui.R.string.common_delete),
            onConfirm = {
                viewModel.removeLocalTag(tag)
                pendingDeleteTag = null
            },
            onDismiss = { pendingDeleteTag = null },
        )
    }
}

/** 标签 pill（可删除时带 ✕，点击删除）。 */
@Composable
private fun TagPill(
    text: String,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clip(AppShapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onRemove != null) Modifier.clickable(onClick = onRemove) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRemove != null) {
            Spacer(Modifier.size(4.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_delete_tag, text),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
