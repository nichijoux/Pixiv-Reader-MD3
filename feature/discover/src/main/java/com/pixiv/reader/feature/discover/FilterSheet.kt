package com.pixiv.reader.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.SearchGenreOption
import com.pixiv.reader.feature.discover.R

/**
 * 高级筛选底部面板：
 * - 通用区（模式/排序/匹配/时间/收藏/AI）始终平铺显示（公共条件，与类型无关）
 * - [detailed] = true 时追加当前类型专属条件（搜索结果时展示完整条件）
 * - 用户类型始终提示无筛选
 * 输入行均用 Row + weight 平分 + 固定分隔，保证不超宽。
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
                SectionSpacer()
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
                    SectionTitle(stringResource(R.string.filter_illust_section))
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
                        SectionTitle(stringResource(R.string.filter_tool_section))
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
                    SectionTitle(stringResource(R.string.filter_novel_section))
                    if (genreOptions.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.filter_genre_section))
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

// ── 基础组件 ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionSpacer() {
    Text("", modifier = Modifier.height(8.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/** 单选 FilterChip 行（同组互斥，横向可滚动）。 */
@Composable
private fun FilterChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it.first }) { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

/** 横向滚动选项（工具 / 题材，可选取消）。 */
@Composable
private fun LazyScrollChips(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.filter_unlimited)) },
            )
        }
        items(options, key = { it.first }) { (value, text) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) null else value) },
                label = { Text(text) },
            )
        }
    }
}

/** 区间输入行（两端 weight 平分，不超宽）。 */
@Composable
private fun RangeInputRow(
    label: String,
    startValue: String?,
    endValue: String?,
    startPlaceholder: String,
    endPlaceholder: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        OutlinedTextField(
            value = startValue.orEmpty(),
            onValueChange = onStart,
            modifier = Modifier.weight(1f),
            placeholder = { Text(startPlaceholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        Text(
            text = "~",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        OutlinedTextField(
            value = endValue.orEmpty(),
            onValueChange = onEnd,
            modifier = Modifier.weight(1f),
            placeholder = { Text(endPlaceholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/** 单值输入行。 */
@Composable
private fun SingleInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

/** 开关行。 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
