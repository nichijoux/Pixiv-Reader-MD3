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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.parse.PixivLinkType
import com.pixiv.reader.core.common.parse.PixivUrlParser
import com.pixiv.reader.core.common.parse.parseNumericId
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.common.model.TagType
import com.pixiv.reader.feature.discover.state.DiscoverViewModel
import com.pixiv.reader.feature.discover.state.SearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenPixivision: () -> Unit = {},
    onOpenUserRanking: () -> Unit = {},
    onOpenAiRanking: () -> Unit = {},
    onOpenEraRanking: () -> Unit = {},
    onOpenWallpaperRanking: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    // 仅 initialQuery 非空时生效；null 不切换当前类型
    initialTagType: TagType? = null,
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

    LaunchedEffect(initialQuery, initialTagType) {
        if (!initialQuery.isNullOrBlank()) {
            when (initialTagType) {
                TagType.NOVEL -> viewModel.setType(SearchType.NOVEL)
                TagType.ILLUST -> viewModel.setType(SearchType.ILLUST)
                null -> {}
            }
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

    // 发现页在 MainShell 中隐藏底栏，需自行消费系统导航栏 inset
    AdaptiveContentBox {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // 外部 modifier 仅挂在搜索栏容器上，供 MainShell 的 sharedBounds hero 过渡对齐
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
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                )
                query.isNotBlank() -> SuggestionList(
                    suggestions = suggestions,
                    numericId = parseNumericId(query),
                    onOpenNovel = onOpenNovel,
                    onOpenIllust = onOpenIllust,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                    onPick = { viewModel.onQueryChange(it); viewModel.search() },
                )
                else -> IdlePanel(
                    hotTags = hotTags,
                    history = history,
                    viewModel = viewModel,
                    onOpenPixivision = onOpenPixivision,
                    onOpenUserRanking = onOpenUserRanking,
                    onOpenAiRanking = onOpenAiRanking,
                    onOpenEraRanking = onOpenEraRanking,
                    onOpenWallpaperRanking = onOpenWallpaperRanking,
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
                if (query.isNotBlank() || hasSearched) {
                    viewModel.search()
                }
                showFilter = false
            },
        )
    }
}
