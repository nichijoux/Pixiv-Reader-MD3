package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.parse.PixivLinkType
import com.pixiv.reader.core.common.parse.PixivUrlParser
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.feature.discover.state.DiscoverViewModel

/**
 * 发现页搜索：热门 + 历史 → 联想 → 结果（TabRow + HorizontalPager 滑动切换插画/小说/用户）。
 * 筛选入口在搜索框右侧，面板按当前类型动态渲染。
 *
 * 子组件：搜索栏 [SearchField]、初始面板 [IdlePanel]、联想 [SuggestionList]、
 * 结果 [SearchResultPager] / [IllustSearchResults] / [NovelSearchResults] / [UserSearchResults]、
 * 筛选 [FilterBottomSheet]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val hotTags by viewModel.hotTags.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val toolOptions by viewModel.toolOptions.collectAsStateWithLifecycle()
    val genreOptions by viewModel.genreOptions.collectAsStateWithLifecycle()
    val langOptions by viewModel.langOptions.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }

    /**
     * 统一搜索提交入口：若输入是 pixiv 链接（如 novel/show.php?id=…）则直接跳对应详情页，
     * 否则走普通搜索。URL 跳转不写搜索历史、不发搜索请求。
     */
    val submitSearch: () -> Unit = {
        val link = PixivUrlParser.parse(query)
        when (link?.type) {
            PixivLinkType.NOVEL -> onOpenNovel(link.id)
            PixivLinkType.SERIES -> onOpenSeries(link.id)
            PixivLinkType.ILLUST -> onOpenIllust(link.id)
            PixivLinkType.USER -> onOpenUser(link.id)
            null -> viewModel.search()
        }
    }

    // 跨 Tab 标签搜索：从别处带关键词进入则自动搜索；携带 pixiv 链接则直接跳详情
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            val link = PixivUrlParser.parse(initialQuery)
            when (link?.type) {
                PixivLinkType.NOVEL -> onOpenNovel(link.id)
                PixivLinkType.SERIES -> onOpenSeries(link.id)
                PixivLinkType.ILLUST -> onOpenIllust(link.id)
                PixivLinkType.USER -> onOpenUser(link.id)
                null -> viewModel.search()
            }
        }
    }

    // 平板限宽居中：搜索栏 + TabRow + 结果列表不超过 MAX_CONTENT_WIDTH_DP。
    // 发现页为全屏页（MainShell 隐藏底栏），底部需自行避让系统导航栏
    AdaptiveContentBox {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // 搜索栏（聚焦高亮 + 清除/搜索按钮）；外层共享元素修饰（MainShell 构造）驱动 hero 过渡
            Box(modifier = modifier) {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = submitSearch,
                    onClear = viewModel::clearSearch,
                    onOpenFilter = { showFilter = true },
                )
            }

            when {
                hasSearched && query.isNotBlank() -> SearchResultPager(
                    viewModel = viewModel,
                    onOpenIllust = onOpenIllust,
                    onOpenNovel = onOpenNovel,
                    onOpenCover = onOpenCover,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                )
                query.isNotBlank() -> SuggestionList(
                    suggestions = suggestions,
                    onPick = { viewModel.onQueryChange(it); viewModel.search() },
                )
                else -> IdlePanel(
                    hotTags = hotTags,
                    history = history,
                    viewModel = viewModel,
                )
            }
        }
    }

    if (showFilter) {
        FilterBottomSheet(
            filters = filters,
            type = type,
            toolOptions = toolOptions,
            genreOptions = genreOptions,
            langOptions = langOptions,
            isPremium = isPremium,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.applyFilters(it)
                viewModel.search()
                showFilter = false
            },
        )
    }
}
