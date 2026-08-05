package com.pixiv.reader.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox

/**
 * 漫画 Tab：顶部排行榜入口 banner + 推荐漫画瀑布流。
 *
 * @param onOpenIllust 点击漫画卡打开详情
 * @param onOpenMangaRanking 点击排行榜 banner / 顶栏奖杯打开漫画排行榜全屏页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenMangaRanking: () -> Unit,
    viewModel: MangaViewModel = hiltViewModel(),
) {
    val items by viewModel.recommendPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.recommendPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.recommendPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.recommendPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.recommendPaged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.manga_title), fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = onOpenMangaRanking) {
                        Icon(
                            Icons.Filled.Leaderboard,
                            contentDescription = stringResource(R.string.manga_cd_ranking),
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 排行榜入口 banner（纯 M3 primaryContainer 主色系，无渐变、无紫色突兀）
            MangaRankingBanner(onClick = onOpenMangaRanking)

            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(message = error, onRetry = viewModel::refresh)
                items.isEmpty() -> androidx.compose.material3.Text(
                    text = stringResource(R.string.manga_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
                else -> IllustWaterfallGrid(
                    illusts = items,
                    onItemClick = onOpenIllust,
                    onLoadMore = viewModel::loadMore,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                )
            }
        }
    }
}

/** 排行榜入口卡片：奖杯 + 标题/副文案 + 箭头。 */
@Composable
private fun MangaRankingBanner(
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onPrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.manga_ranking_banner),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.manga_ranking_banner_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}