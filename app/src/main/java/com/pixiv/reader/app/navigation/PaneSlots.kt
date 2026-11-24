package com.pixiv.reader.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import com.pixiv.reader.feature.novel.ui.NovelDetailPane
import com.pixiv.reader.feature.novel.ui.NovelSeriesPane

// 平板右栏共享槽位：只共享 VM 创建与 pane 组装，导航出口由宿主按各自导航层级注入

@Composable
internal fun NovelDetailPaneSlot(
    selectedId: Long?,
    novelVm: NovelViewModel,
    commentVm: CommentListViewModel,
    onOpenSeries: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    NovelDetailPane(
        selectedId = selectedId,
        placeholder = stringResource(
            com.pixiv.reader.feature.novel.R.string.novel_ranking_preview_placeholder
        ),
        onOpenReader = onOpenReader,
        onOpenUser = onOpenUser,
        onOpenSeries = onOpenSeries,
        onSearchTag = onSearchTag,
        commentVm = commentVm,
        viewModel = novelVm,
    )
}

@Composable
internal fun NovelSeriesPaneSlot(
    selectedId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val seriesVm: NovelSeriesViewModel = hiltViewModel()
    NovelSeriesPane(
        selectedId = selectedId,
        placeholder = stringResource(
            com.pixiv.reader.feature.novel.R.string.novel_series_pane_placeholder
        ),
        onOpenNovel = onOpenNovel,
        onOpenSeries = onOpenSeries,
        onOpenUser = onOpenUser,
        onOpenCover = onOpenCover,
        onSearchTag = onSearchTag,
        viewModel = seriesVm,
    )
}
