package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.BlockedViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState

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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    // 本地过滤标签清空 / 单条删除确认
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (localTags.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.blocked_clear), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading -> LoadingBox()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── 本地过滤标签卡片 ──
                    item(key = "local_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.blocked_local_tags_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.blocked_local_tags_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                // 添加输入
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        localTags.forEach { tag ->
                                            TagPill(text = tag, onRemove = { pendingDeleteTag = tag })
                                        }
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
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.blocked_server_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.blocked_server_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                if (mutedTags.isEmpty() && mutedUsers.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.blocked_server_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                } else {
                                    // 服务端标签
                                    if (mutedTags.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                UserAvatar(
                                                    name = u?.name,
                                                    avatarUrl = u?.profile_image_urls?.best(),
                                                    modifier = Modifier.size(36.dp),
                                                )
                                                Text(
                                                    text = u?.name.orEmpty(),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .padding(start = 12.dp)
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onRemove != null) Modifier.clickable(onClick = onRemove) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
