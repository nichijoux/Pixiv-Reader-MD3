package com.pixiv.reader.feature.notification

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem

/**
 * 通知分组子列表（/v1/notification/view-more）。
 * 子项均为普通通知行，点击同样按 `target_url` 跳转。
 *
 * @param onBack 返回
 * @param onOpenUser / onOpenIllust / onOpenNovel 同 [NotificationRoute]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationGroupRoute(
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    viewModel: NotificationGroupViewModel = hiltViewModel(),
) {
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    // 顶栏标题：优先用入口传入的组名，缺省回退通用文案
    val passedTitle = viewModel.groupTitle
    val title = passedTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.notification_group_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
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
                        NotificationRow(
                            item = item,
                            onClick = {
                                openNotificationTarget(item, onOpenUser, onOpenIllust, onOpenNovel)
                            },
                        )
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
