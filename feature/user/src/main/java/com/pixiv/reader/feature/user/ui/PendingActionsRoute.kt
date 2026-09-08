package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.database.entity.PendingActionEntity
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.PendingActionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待同步操作页：断网时的收藏 / 关注 / 追更暂存列表（OfflineActionQueue 联网自动补发）。
 * failed 条目（服务端明确拒绝）可手动重试或删除；支持清空全部。
 *
 * @param onBack 返回
 * @param viewModel 页面 ViewModel（hilt 注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingActionsRoute(
    onBack: () -> Unit,
    viewModel: PendingActionsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pending_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pending_clear))
                        }
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
            if (entries.isEmpty()) {
                EmptyBox(stringResource(R.string.pending_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        PendingActionRow(
                            entry = entry,
                            onRetry = { viewModel.retry(entry) },
                            onDelete = { viewModel.delete(entry) },
                        )
                    }
                }
            }
        }
    }

    // 清空确认（删除全部队列记录，不可撤销）
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.pending_clear_title),
            message = stringResource(R.string.pending_clear_message, entries.size),
            confirmText = stringResource(R.string.pending_clear),
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

/** 单条待同步操作行：家族图标 + 描述 + 状态徽标；failed 条目附重试 / 删除按钮。 */
@Composable
private fun PendingActionRow(
    entry: PendingActionEntity,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = familyIcon(entry.family),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = familyLabel(entry.family, entry.targetState, entry.targetId),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = if (entry.status == PendingActionEntity.STATUS_FAILED) {
                    stringResource(R.string.pending_status_failed)
                } else {
                    stringResource(R.string.pending_status_pending)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == PendingActionEntity.STATUS_FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        overlineContent = {
            Text(
                text = TIME_FORMAT.get().format(Date(entry.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (entry.status == PendingActionEntity.STATUS_FAILED) {
                androidx.compose.foundation.layout.Row {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.pending_retry))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 家族 → 图标。 */
private fun familyIcon(family: String): ImageVector = when (family) {
    PendingActionEntity.FAMILY_ILLUST_BOOKMARK,
    PendingActionEntity.FAMILY_NOVEL_BOOKMARK,
    -> Icons.Filled.Favorite

    PendingActionEntity.FAMILY_FOLLOW_USER -> Icons.Filled.PersonAdd
    PendingActionEntity.FAMILY_MANGA_WATCHLIST,
    PendingActionEntity.FAMILY_NOVEL_WATCHLIST,
    -> Icons.Filled.Notifications

    else -> Icons.Filled.CloudOff
}

/**
 * 家族 + 目标状态 → 一句话描述（如「收藏插画 #123456 · 加入」）。
 * 家族名走字符串资源（i18n）；目标 id 内联展示。
 */
@Composable
private fun familyLabel(family: String, targetState: Boolean, targetId: Long): String {
    val familyName = when (family) {
        PendingActionEntity.FAMILY_ILLUST_BOOKMARK -> stringResource(R.string.pending_family_illust_bookmark)
        PendingActionEntity.FAMILY_NOVEL_BOOKMARK -> stringResource(R.string.pending_family_novel_bookmark)
        PendingActionEntity.FAMILY_FOLLOW_USER -> stringResource(R.string.pending_family_follow_user)
        PendingActionEntity.FAMILY_MANGA_WATCHLIST -> stringResource(R.string.pending_family_manga_watchlist)
        PendingActionEntity.FAMILY_NOVEL_WATCHLIST -> stringResource(R.string.pending_family_novel_watchlist)
        else -> family
    }
    val stateName = stringResource(if (targetState) R.string.pending_state_add else R.string.pending_state_remove)
    return "$familyName #$targetId · $stateName"
}

/** 行内时间格式（线程局部，避免每次组合新建 SimpleDateFormat）。 */
private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
}
