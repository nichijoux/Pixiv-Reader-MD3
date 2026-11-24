package com.pixiv.reader.core.ui.component.list

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.feedPhase

data class PagedUiState<T>(
    val items: List<T>,
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: String?,
)

@Composable
fun <T> PagedState<T>.collectAsPagedUiState(): PagedUiState<T> {
    val items by items.collectAsStateWithLifecycle()
    val isLoading by isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by hasMore.collectAsStateWithLifecycle()
    val error by error.collectAsStateWithLifecycle()
    return PagedUiState(items, isLoading, isLoadingMore, hasMore, error)
}

@Composable
fun <T> PagedFeed(
    paged: PagedState<T>,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { LoadingBox() },
    content: @Composable (state: PagedUiState<T>) -> Unit,
) {
    val state = paged.collectAsPagedUiState()
    when (feedPhase(loading = state.isLoading, hasItems = state.items.isNotEmpty(), hasError = state.error != null)) {
        FeedPhase.LOADING -> Box(modifier) { loadingContent() }
        FeedPhase.ERROR -> Box(modifier) { ErrorBox(message = state.error, onRetry = onRetry) }
        FeedPhase.EMPTY -> Box(modifier) { EmptyBox(emptyText) }
        FeedPhase.CONTENT -> content(state)
    }
}
