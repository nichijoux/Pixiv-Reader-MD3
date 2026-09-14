package com.pixiv.reader.feature.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 漫画排行榜全屏页（薄壳）：分段 Tab / 日期筛选 / 平板双栏交互全部在共享
 * [IllustRankingScreen]，本壳仅注入标题/空态资源与 [MangaRankingViewModel]（5 段漫画 mode）。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情（小屏单栏路径）
 * @param onOpenUser 点击作者打开用户主页
 * @param onOpenViewer 点击图片打开全屏查看器
 * @param onSearchTag 标签点击跳转标签搜索（卡片标签与详情 pane 标签共用）
 * @return 无返回值
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
