package com.pixiv.reader.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 发现页：搜索（联想 + 热门 + V3 筛选）与结果列表。
 * @param onOpenIllust 点击插画结果打开详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverRoute(
    onOpenIllust: (Long) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        // 搜索输入框
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            placeholder = { Text("搜索作品、画师、标签") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = viewModel::search) {
                        Text("搜索")
                    }
                }
            },
        )

        // 类型 + 筛选
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
        ) {
            items(SearchType.entries) { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { viewModel.setType(t) },
                    label = { Text(t.label) },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { showFilter = true },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.height(14.dp))
                            Text("筛选")
                        }
                    },
                )
            }
        }

        if (hasSearched && query.isNotBlank()) {
            SearchResults(viewModel, onOpenIllust)
        } else {
            SearchSuggestions(viewModel)
        }
    }

    if (showFilter) {
        FilterBottomSheet(
            filters = filters,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.applyFilters(it)
                viewModel.search()
                showFilter = false
            },
        )
    }
}

@Composable
private fun SearchResults(viewModel: DiscoverViewModel, onOpenIllust: (Long) -> Unit) {
    when (viewModel.type.value) {
        SearchType.ILLUST -> IllustSearchResults(viewModel, onOpenIllust)
        SearchType.NOVEL -> NovelSearchResults(viewModel)
        SearchType.USER -> UserSearchResults(viewModel)
    }
}

@Composable
private fun SearchSuggestions(viewModel: DiscoverViewModel) {
    val hotTags by viewModel.hotTags.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "热门搜索",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        hotTags.take(6).forEachIndexed { index, tag ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tag.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            Text(
                text = "搜索联想",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp),
            )
            suggestions.forEach { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.height(16.dp))
                    Text(text = tag.name.orEmpty())
                }
            }
        }
    }
}

private fun com.example.pixivapi.model.TrendingTag.displayName(): String =
    translated_name ?: tag ?: ""
