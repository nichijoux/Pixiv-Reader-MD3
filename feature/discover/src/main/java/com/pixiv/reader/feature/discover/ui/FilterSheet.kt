package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.SearchGenreOption
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.SearchFilters
import com.pixiv.reader.feature.discover.state.SearchMode
import com.pixiv.reader.feature.discover.state.SearchType

/**
 * 高级筛选底部面板：
 * - 通用区（模式/排序/匹配/时间/收藏/AI）始终平铺显示（公共条件，与类型无关）
 * - [detailed] = true 时追加当前类型专属条件（搜索结果时展示完整条件）
 * - 用户类型始终提示无筛选
 * 输入行均用 Row + weight 平分 + 固定分隔，保证不超宽。
 * 通用表单原语见 FilterFormComponents.kt。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheet(
    filters: SearchFilters,
    type: SearchType,
    toolOptions: List<String>,
    genreOptions: List<SearchGenreOption>,
    detailed: Boolean,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    val genreUnknown = stringResource(R.string.filter_genre_unknown)
    val titleRes = when {
        type == SearchType.USER -> R.string.filter_title_user
        detailed && type == SearchType.ILLUST -> R.string.filter_title_illust
        detailed && type == SearchType.NOVEL -> R.string.filter_title_novel
        else -> R.string.filter_title_default
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleLarge)

            if (type == SearchType.USER) {
                Text(
                    text = stringResource(R.string.filter_user_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // 通用区
                FilterSectionSpacer()
                FilterChipRow(
                    label = stringResource(R.string.filter_mode),
                    options = listOf(
                        SearchMode.LATEST.name to stringResource(R.string.filter_latest),
                        SearchMode.HOT.name to stringResource(R.string.filter_hot),
                    ),
                    selected = draft.mode.name,
                    onSelect = {
                        draft = draft.copy(
                            mode = if (it == SearchMode.HOT.name) SearchMode.HOT else SearchMode.LATEST,
                        )
                    },
                )
                FilterChipRow(
                    label = stringResource(R.string.filter_sort),
                    options = listOf(
                        "date_desc" to stringResource(R.string.filter_latest),
                        "date_asc" to stringResource(R.string.filter_oldest),
                        "popular_desc" to stringResource(R.string.filter_popular),
                    ),
                    selected = draft.sort,
                    onSelect = { draft = draft.copy(sort = it) },
                )
                FilterChipRow(
                    label = stringResource(R.string.filter_match),
                    options = listOf(
                        "partial_match_for_tags" to stringResource(R.string.filter_match_partial),
                        "exact_match_for_tags" to stringResource(R.string.filter_match_exact),
                        "title_and_caption" to stringResource(R.string.filter_match_title),
                    ),
                    selected = draft.searchTarget,
                    onSelect = { draft = draft.copy(searchTarget = it) },
                )
                RangeInputRow(
                    label = stringResource(R.string.filter_date_range),
                    startValue = draft.startDate,
                    endValue = draft.endDate,
                    startPlaceholder = stringResource(R.string.filter_date_start_hint),
                    endPlaceholder = stringResource(R.string.filter_date_end_hint),
                    keyboardType = KeyboardType.Text,
                    onStart = { draft = draft.copy(startDate = it.ifBlank { null }) },
                    onEnd = { draft = draft.copy(endDate = it.ifBlank { null }) },
                )
                SingleInputRow(
                    label = stringResource(R.string.filter_bookmark_min),
                    value = draft.bookmarkNumMin?.toString().orEmpty(),
                    placeholder = stringResource(R.string.filter_unlimited),
                    onValue = { draft = draft.copy(bookmarkNumMin = it.toIntOrNull()) },
                )
                FilterChipRow(
                    label = stringResource(R.string.filter_ai),
                    options = listOf(
                        "0" to stringResource(R.string.filter_all),
                        "1" to stringResource(R.string.filter_ai_human),
                        "2" to stringResource(R.string.filter_ai_only),
                    ),
                    selected = draft.aiType.toString(),
                    onSelect = { draft = draft.copy(aiType = it.toInt()) },
                )

                // 插画专属（搜索结果时展示）
                if (detailed && type == SearchType.ILLUST) {
                    FilterSectionTitle(stringResource(R.string.filter_illust_section))
                    FilterChipRow(
                        label = stringResource(R.string.filter_ratio),
                        options = listOf(
                            "" to stringResource(R.string.filter_all),
                            "square" to stringResource(R.string.filter_ratio_square),
                            "wide" to stringResource(R.string.filter_ratio_wide),
                            "tall" to stringResource(R.string.filter_ratio_tall),
                        ),
                        selected = draft.ratioPattern.orEmpty(),
                        onSelect = { draft = draft.copy(ratioPattern = it.ifBlank { null }) },
                    )
                    FilterChipRow(
                        label = stringResource(R.string.filter_content_type),
                        options = listOf(
                            "" to stringResource(R.string.filter_all),
                            "illust" to stringResource(R.string.filter_content_illust),
                            "manga" to stringResource(R.string.filter_content_manga),
                            "ugoira" to stringResource(R.string.filter_content_ugoira),
                        ),
                        selected = draft.contentType.orEmpty(),
                        onSelect = { draft = draft.copy(contentType = it.ifBlank { null }) },
                    )
                    if (toolOptions.isNotEmpty()) {
                        FilterSectionTitle(stringResource(R.string.filter_tool_section))
                        LazyScrollChips(
                            options = toolOptions.map { it to it },
                            selected = draft.tool,
                            onSelect = { draft = draft.copy(tool = it) },
                        )
                    }
                    RangeInputRow(
                        label = stringResource(R.string.filter_width),
                        startValue = draft.widthMin?.toString().orEmpty(),
                        endValue = draft.widthMax?.toString().orEmpty(),
                        startPlaceholder = stringResource(R.string.filter_px_min),
                        endPlaceholder = stringResource(R.string.filter_px_max),
                        onStart = { draft = draft.copy(widthMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(widthMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = stringResource(R.string.filter_height),
                        startValue = draft.heightMin?.toString().orEmpty(),
                        endValue = draft.heightMax?.toString().orEmpty(),
                        startPlaceholder = stringResource(R.string.filter_px_min),
                        endPlaceholder = stringResource(R.string.filter_px_max),
                        onStart = { draft = draft.copy(heightMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(heightMax = it.toIntOrNull()) },
                    )
                }

                // 小说专属（搜索结果时展示）
                if (detailed && type == SearchType.NOVEL) {
                    FilterSectionTitle(stringResource(R.string.filter_novel_section))
                    if (genreOptions.isNotEmpty()) {
                        FilterSectionTitle(stringResource(R.string.filter_genre_section))
                        LazyScrollChips(
                            options = genreOptions.map { it.id.toString() to (it.label ?: genreUnknown) },
                            selected = draft.genre?.toString(),
                            onSelect = { draft = draft.copy(genre = it?.toIntOrNull()) },
                        )
                    }
                    SwitchRow(stringResource(R.string.filter_original_only), draft.isOriginalOnly == true) { draft = draft.copy(isOriginalOnly = it) }
                    SwitchRow(stringResource(R.string.filter_replaceable_only), draft.isReplaceableOnly == true) { draft = draft.copy(isReplaceableOnly = it) }
                    RangeInputRow(
                        label = stringResource(R.string.filter_text_length),
                        startValue = draft.textLengthMin?.toString().orEmpty(),
                        endValue = draft.textLengthMax?.toString().orEmpty(),
                        startPlaceholder = stringResource(R.string.filter_min),
                        endPlaceholder = stringResource(R.string.filter_max),
                        onStart = { draft = draft.copy(textLengthMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(textLengthMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = stringResource(R.string.filter_word_count),
                        startValue = draft.wordCountMin?.toString().orEmpty(),
                        endValue = draft.wordCountMax?.toString().orEmpty(),
                        startPlaceholder = stringResource(R.string.filter_min),
                        endPlaceholder = stringResource(R.string.filter_max),
                        onStart = { draft = draft.copy(wordCountMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(wordCountMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = stringResource(R.string.filter_reading_time),
                        startValue = draft.readingTimeMin?.toString().orEmpty(),
                        endValue = draft.readingTimeMax?.toString().orEmpty(),
                        startPlaceholder = stringResource(R.string.filter_reading_time_min),
                        endPlaceholder = stringResource(R.string.filter_max),
                        onStart = { draft = draft.copy(readingTimeMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(readingTimeMax = it.toIntOrNull()) },
                    )
                }

                // 操作
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { draft = SearchFilters() },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.filter_reset))
                    }
                    Button(
                        onClick = { onApply(draft) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.filter_apply))
                    }
                }
            }
        }
    }
}
