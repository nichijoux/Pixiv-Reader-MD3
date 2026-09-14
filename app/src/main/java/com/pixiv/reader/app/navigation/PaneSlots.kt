package com.pixiv.reader.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import com.pixiv.reader.feature.novel.ui.NovelDetailPane
import com.pixiv.reader.feature.novel.ui.NovelSeriesPane

/**
 * 平板 Master-Detail 右栏槽位共享件（app 组合根）：
 * 用户页（PixivNavGraph，直连 navController.navigate）与关注页（MainShell，回调链）
 * 注入同一组 pane，本文件只共享「ViewModel 创建 + pane 组装」——
 * 导航出口（阅读器 / 用户主页 / 全屏大图 / 标签搜索）一律由调用方以回调注入。
 */

/**
 * 小说详情 pane 槽位：组装 [NovelDetailPane]（小说卡点击 → 右栏小说详情）。
 * 详情/评论 ViewModel 由宿主 pane 管线传入；占位文案统一为本文件内的
 * 小说排行右栏占位资源（两处调用点原本即同款）。
 *
 * @param selectedId 当前选中小说 id（null = 未选中，显示占位）
 * @param novelVm 小说详情 ViewModel（宿主传入）
 * @param commentVm 评论 ViewModel（宿主传入，进入评论区时按当前小说 switchTo）
 * @param onOpenSeries 「查看完整系列」回调（宿主分流：pane 内切换系列 pane / 全屏）
 * @param onOpenReader 开始阅读回调（全屏路由）
 * @param onOpenUser 点击作者打开用户主页回调（全屏路由）
 * @return 无返回值（UI 渲染）
 */
@Composable
internal fun NovelDetailPaneSlot(
    selectedId: Long?,
    novelVm: NovelViewModel,
    commentVm: CommentListViewModel,
    onOpenSeries: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    NovelDetailPane(
        selectedId = selectedId,
        placeholder = stringResource(
            com.pixiv.reader.feature.novel.R.string.novel_ranking_preview_placeholder
        ),
        onOpenReader = onOpenReader,
        onOpenUser = onOpenUser,
        // 「查看完整系列」由宿主分流（pane 内切换系列 pane / 全屏）
        onOpenSeries = onOpenSeries,
        commentVm = commentVm,
        viewModel = novelVm,
    )
}

/**
 * 小说系列 pane 槽位：组装 [NovelSeriesPane]（小说卡系列标题点击 → 右栏系列详情）。
 * 系列 ViewModel 在本槽位内 hiltViewModel 创建（槽位签名不暴露 feature:novel 类型）。
 *
 * @param selectedId 当前选中系列 id（null = 未选中，显示占位）
 * @param onOpenNovel 分册点击回调（宿主分流：pane 内切换小说详情 / 全屏路由）
 * @param onOpenSeries 分册卡系列标题点击回调（宿主维护返回栈后原地切换系列）
 * @param onOpenUser 点击作者打开用户主页回调
 * @param onOpenCover 打开封面全屏大图回调
 * @param onSearchTag 标签搜索回调（宿主跳发现页）
 * @return 无返回值（UI 渲染）
 */
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
