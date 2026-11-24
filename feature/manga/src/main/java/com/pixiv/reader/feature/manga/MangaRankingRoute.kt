package com.pixiv.reader.feature.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 漫画排行榜全屏页（薄壳）：交互全在 [IllustRankingScreen]，此处仅注入资源与 5 段漫画 mode 的 VM。
 */
@Composable
fun MangaRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit = {},
    viewModel: MangaRankingViewModel = hiltViewModel(),
) {
    IllustRankingScreen(
        title = stringResource(R.string.manga_ranking_title),
        emptyText = stringResource(R.string.manga_ranking_empty),
        placeholderText = stringResource(R.string.manga_ranking_preview_placeholder),
        viewModel = viewModel,
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onOpenUser = onOpenUser,
        onOpenViewer = onOpenViewer,
        onSearchTag = onSearchTag,
    )
}
