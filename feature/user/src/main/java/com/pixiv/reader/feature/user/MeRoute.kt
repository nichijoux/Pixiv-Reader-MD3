package com.pixiv.reader.feature.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.UserAvatar

/**
 * 我的 Tab（P5）：当前用户头部 + 功能入口（历史 / 收藏 / 追更 / 屏蔽）+ 登出。
 *
 * @param onOpenHistory 打开阅读历史
 * @param onOpenBookmarks 打开我的收藏
 * @param onOpenWatchlist 打开追更
 * @param onOpenBlocked 打开屏蔽管理
 */
@Composable
fun MeRoute(
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenBlocked: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                // 当前用户头部
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = user?.name,
                        avatarUrl = user?.profile_image_urls?.best(),
                        modifier = Modifier.size(64.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = user?.name ?: "未登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!user?.account.isNullOrBlank()) {
                            Text(
                                text = "@${user?.account}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 功能入口
                MeEntry(Icons.Filled.History, "阅读历史", onClick = onOpenHistory)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MeEntry(Icons.Filled.Favorite, "我的收藏", onClick = onOpenBookmarks)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MeEntry(Icons.Filled.Notifications, "追更", onClick = onOpenWatchlist)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MeEntry(Icons.Filled.Block, "屏蔽管理", onClick = onOpenBlocked)

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("退出登录")
                }
            }
        }
    }
}

@Composable
private fun MeEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
