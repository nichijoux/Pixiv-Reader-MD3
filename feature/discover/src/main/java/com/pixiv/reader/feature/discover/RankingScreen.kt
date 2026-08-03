package com.pixiv.reader.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.PixivImage

/** 排行榜：分段切换 + 排名列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingRoute(
    onOpenIllust: (Long) -> Unit,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排行榜", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                items(RankingMode.entries) { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { viewModel.selectMode(m) },
                        label = { Text(m.label) },
                    )
                }
            }

            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    items(items, key = { it.id }) { illust ->
                        RankingRow(illust, rank = items.indexOf(illust) + 1, onClick = { onOpenIllust(illust.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LaunchedLoadMore(isLoadingMore, viewModel::loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingRow(illust: Illust, rank: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> MaterialTheme.colorScheme.primary
                2, 3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(28.dp),
        )
        PixivImage(
            url = illust.image_urls?.square_medium ?: illust.image_urls?.medium,
            contentDescription = illust.title,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = illust.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = illust.user?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
