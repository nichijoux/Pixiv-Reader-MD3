package com.pixiv.reader.feature.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 插画排行榜全屏页（薄壳）：交互全在 [IllustRankingScreen]，此处仅注入资源与 7 段插画 mode 的 VM。
 */
@Composable
fun IllustRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit = {},
    viewModel: IllustRankingViewModel = hiltViewModel(),
) {
    IllustRankingScreen(
        title = stringResource(R.string.illust_ranking_title),
        emptyText = stringResource(R.string.illust_ranking_empty),
        placeholderText = stringResource(R.string.manga_ranking_preview_placeholder),
        viewModel = viewModel,
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onOpenUser = onOpenUser,
        onOpenViewer = onOpenViewer,
        onSearchTag = onSearchTag,
    )
}
