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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.list.RankingList
import com.pixiv.reader.core.ui.component.card.RankingRow

/**
 * 插画排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]），排名列表行点击打开作品详情。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    viewModel: IllustRankingViewModel = hiltViewModel(),
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 RankingList 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.illust_ranking_title))
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
            onModeSelect = viewModel::onPageSelected,
            stateFor = viewModel::stateFor,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.padding(padding),
            emptyText = stringResource(R.string.illust_ranking_empty),
        ) { item, rank ->
            RankingRow(
                rank = rank,
                illust = item,
                onClick = { onOpenIllust(item.id) },
            )
        }
    }
}
