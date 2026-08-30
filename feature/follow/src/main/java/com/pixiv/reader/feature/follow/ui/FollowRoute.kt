package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.feature.follow.R
import com.pixiv.reader.feature.follow.data.FollowType
import com.pixiv.reader.feature.follow.state.FollowViewModel
import kotlinx.coroutines.launch

/** 右列类型段（HorizontalPager 页 → 类型，顺序即页序）。 */
private val TYPE_TABS = listOf(FollowType.ALL, FollowType.NOVEL, FollowType.ILLUST)

/**
 * 关注页：左列关注用户 + 右列混合动态流（全部 / 小说 / 插画三段的滑动 Tab）。
 *
 * ## 布局（手机 / 平板统一左右结构）
 * - 左列 [FollowUserColumn]：手机窄版 60dp（头像 + 小字名），平板宽版 168dp（头像 + 完整名）
 * - 右列：`TabRow`（全部/小说/插画，均分占满）+ `HorizontalPager` 左右滑动切换；
 *   每页是独立类型流（数据驻留 VM，滑动切回不重复请求），手机上单列流、平板上瀑布流
 * - 左列点用户 → 加载该用户全部作品（插画/漫画/小说混合）；点「全部」恢复关注新作品流
 *
 * 回调经 MainShell 上抛到顶层导航（详情 / 查看器 / 用户页等全屏路由）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: FollowViewModel = hiltViewModel(),
) {
    val configuration = LocalConfiguration.current
    val windowClass = remember(configuration) {
        classifyWindowWidth(configuration.screenWidthDp)
    }
    val isCompact = windowClass == WindowSizeClass.Compact

    val users by viewModel.users.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()
    val usersLoading by viewModel.usersPaged.isLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val feedError by viewModel.feedError.collectAsStateWithLifecycle()

    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val novelItems by viewModel.novelItems.collectAsStateWithLifecycle()
    val illustItems by viewModel.illustItems.collectAsStateWithLifecycle()
    // 注意：不能 remember 捕获——三流为 StateFlow 的值快照，重组时必须取最新（否则永远空列表）
    val itemsByType = mapOf(
        FollowType.ALL to allItems,
        FollowType.NOVEL to novelItems,
        FollowType.ILLUST to illustItems,
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // ── 左列：关注用户列表 ──
        FollowUserColumn(
            users = users,
            selectedUserId = selectedUserId,
            isLoadingUsers = usersLoading,
            isCompact = isCompact,
            onSelectUser = viewModel::selectUser,
            onLoadMoreUsers = viewModel::loadMoreUsers,
        )

        // ── 右列：类型 Tab + 滑动 Pager ──
        AdaptiveContentBox(modifier = Modifier.weight(1f)) {
            val pagerState = rememberPagerState(pageCount = { TYPE_TABS.size })
            val scope = rememberCoroutineScope()
            val selectedIndex = pagerState.currentPage.coerceIn(0, TYPE_TABS.lastIndex)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 沉浸式：背景 surface 延伸到状态栏后面（状态栏透明），色彩统一
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // TabRow 仅内容让开状态栏文字区；背景与 Column 同色无缝延伸
                TabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.statusBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    TYPE_TABS.forEachIndexed { index, type ->
                        Tab(
                            selected = selectedIndex == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(stringResource(type.labelRes()))
                            },
                        )
                    }
                }
                // 下拉刷新：指示器出现在右列信息流顶部（TabRow 下方），按当前模式重拉
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::pullRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val type = TYPE_TABS.getOrNull(page) ?: FollowType.ALL
                        FollowFeed(
                            items = itemsByType.getValue(type),
                            isLoading = isLoading,
                            isLoadingMore = isLoadingMore,
                            hasError = feedError,
                            isCompact = isCompact,
                            onLoadMore = viewModel::loadMoreFeed,
                            onRetry = viewModel::retry,
                            onOpenIllust = onOpenIllust,
                            onOpenNovel = onOpenNovel,
                            onOpenUser = onOpenUser,
                            onOpenSeries = onOpenSeries,
                            onToggleIllustFavorite = viewModel::toggleIllustFavorite,
                            onToggleNovelFavorite = viewModel::toggleNovelFavorite,
                        )
                    }
                }
            }
        }
    }
}

/** 类型段 label 资源（全部 / 小说 / 插画）。 */
@Composable
private fun FollowType.labelRes(): Int = when (this) {
    FollowType.ALL -> R.string.follow_tab_all
    FollowType.NOVEL -> R.string.follow_tab_novel
    FollowType.ILLUST -> R.string.follow_tab_illust
}
