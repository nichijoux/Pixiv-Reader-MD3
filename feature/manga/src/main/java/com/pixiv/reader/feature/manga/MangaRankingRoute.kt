package com.pixiv.reader.feature.manga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.RankingList
import com.pixiv.reader.core.ui.component.RankingRow

/**
 * 漫画排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]），排名列表行点击打开作品详情。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    viewModel: MangaRankingViewModel = hiltViewModel(),
) {
    val selectedValue by viewModel.selectedValue.collectAsStateWithLifecycle()
    val dataVersion by viewModel.dataVersion.collectAsStateWithLifecycle()
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.manga_ranking_title), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.manga_cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* 更多（暂保留） */ }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.manga_cd_more),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        RankingList(
            modes = viewModel.modes,
            selectedValue = selectedValue,
            onModeSelect = viewModel::selectMode,
            items = items,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            error = error,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.padding(padding),
            emptyText = stringResource(R.string.manga_ranking_empty),
            dataKey = dataVersion,
        ) { item, rank ->
            RankingRow(
                rank = rank,
                illust = item,
                onClick = { onOpenIllust(item.id) },
            )
        }
    }
}