package com.pixiv.reader.feature.comic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R
import com.pixiv.reader.feature.comic.state.ComicSearchViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * COMIC 搜索路由：顶栏内嵌搜索框（进入自动聚焦键盘），结果为作品网格 +
 * 触底翻页（服务端页号分页，由 VM 适配 PagedState）。
 *
 * @param onBack 返回回调（上层 safeBack）
 * @param onOpenWork 进作品详情（作品 id）
 * @param viewModel Hilt 注入的搜索 VM
 * @return 无返回值
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSearchRoute(
    onBack: () -> Unit,
    onOpenWork: (Long) -> Unit,
    viewModel: ComicSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val items by viewModel.resultsPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.resultsPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.resultsPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.resultsPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.resultsPaged.error.collectAsStateWithLifecycle()
    val notificationHost = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHost)

    // 输入框本地状态：回车 / 提交才真正发搜索
    var input by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.comic_cd_back),
                        )
                    }
                },
                title = {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.comic_search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        trailingIcon = {
                            if (input.isNotEmpty()) {
                                IconButton(onClick = { input = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.comic_search_cd_clear),
                                    )
                                }
                            }
                        },
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.search(input)
                        },
                    ) { Text(stringResource(R.string.comic_search_action)) }
                },
            )
        },
        snackbarHost = { NotificationHost(notificationHost) },
    ) { padding ->
        when {
            // 尚未搜索：占位
            items.isEmpty() && !isLoading && error == null -> EmptyBox(
                text = stringResource(R.string.comic_search_hint),
                modifier = Modifier.padding(padding),
            )
            items.isEmpty() && error != null -> EmptyBox(
                text = stringResource(R.string.comic_search_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(SEARCH_GRID_COLUMNS),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(items, key = { it.id }) { work ->
                    ComicWorkCard(
                        title = work.name.orEmpty(),
                        author = work.author.orEmpty(),
                        coverUrl = work.image?.thumbnail ?: work.coverUrl,
                        storiesCount = work.storiesCount,
                        onClick = { onOpenWork(work.id) },
                    )
                }
                if (hasMore || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_search_more") {
                        LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
                    }
                }
            }
        }
    }

    // 首次组合即聚焦输入框弹键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/** 搜索网格列数。 */
private const val SEARCH_GRID_COLUMNS = 3
