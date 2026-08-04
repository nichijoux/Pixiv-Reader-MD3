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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pixivapi.model.SearchGenreOption

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
    val title = when {
        type == SearchType.USER -> "筛选 · 用户"
        detailed && type == SearchType.ILLUST -> "高级筛选 · 插画"
        detailed && type == SearchType.NOVEL -> "高级筛选 · 小说"
        else -> "高级筛选"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)

            if (type == SearchType.USER) {
                Text(
                    text = "用户搜索仅支持关键词匹配，暂无高级筛选条件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // 通用区
                SectionSpacer()
                FilterChipRow(
                    label = "模式",
                    options = listOf(
                        SearchMode.LATEST.name to "最新",
                        SearchMode.HOT.name to "热门",
                    ),
                    selected = draft.mode.name,
                    onSelect = {
                        draft = draft.copy(
                            mode = if (it == SearchMode.HOT.name) SearchMode.HOT else SearchMode.LATEST,
                        )
                    },
                )
                FilterChipRow(
                    label = "排序",
                    options = listOf(
                        "date_desc" to "最新",
                        "date_asc" to "最旧",
                        "popular_desc" to "收藏多",
                    ),
                    selected = draft.sort,
                    onSelect = { draft = draft.copy(sort = it) },
                )
                FilterChipRow(
                    label = "匹配方式",
                    options = listOf(
                        "partial_match_for_tags" to "标签部分一致",
                        "exact_match_for_tags" to "标签完全一致",
                        "title_and_caption" to "标题简介",
                    ),
                    selected = draft.searchTarget,
                    onSelect = { draft = draft.copy(searchTarget = it) },
                )
                RangeInputRow(
                    label = "时间范围",
                    startValue = draft.startDate,
                    endValue = draft.endDate,
                    startPlaceholder = "2026-01-01",
                    endPlaceholder = "2026-08-01",
                    keyboardType = KeyboardType.Text,
                    onStart = { draft = draft.copy(startDate = it.ifBlank { null }) },
                    onEnd = { draft = draft.copy(endDate = it.ifBlank { null }) },
                )
                SingleInputRow(
                    label = "最低收藏",
                    value = draft.bookmarkNumMin?.toString().orEmpty(),
                    placeholder = "不限",
                    onValue = { draft = draft.copy(bookmarkNumMin = it.toIntOrNull()) },
                )
                FilterChipRow(
                    label = "AI 作品",
                    options = listOf("0" to "全部", "1" to "仅人绘", "2" to "仅 AI"),
                    selected = draft.aiType.toString(),
                    onSelect = { draft = draft.copy(aiType = it.toInt()) },
                )

                // 插画专属（搜索结果时展示）
                if (detailed && type == SearchType.ILLUST) {
                    SectionTitle("插画专属")
                    FilterChipRow(
                        label = "比例",
                        options = listOf(
                            "" to "全部",
                            "square" to "正方",
                            "wide" to "横长",
                            "tall" to "竖长",
                        ),
                        selected = draft.ratioPattern.orEmpty(),
                        onSelect = { draft = draft.copy(ratioPattern = it.ifBlank { null }) },
                    )
                    FilterChipRow(
                        label = "内容类型",
                        options = listOf(
                            "" to "全部",
                            "illust" to "插画",
                            "manga" to "漫画",
                            "ugoira" to "动图",
                        ),
                        selected = draft.contentType.orEmpty(),
                        onSelect = { draft = draft.copy(contentType = it.ifBlank { null }) },
                    )
                    if (toolOptions.isNotEmpty()) {
                        SectionTitle("绘制工具")
                        LazyScrollChips(
                            options = toolOptions.map { it to it },
                            selected = draft.tool,
                            onSelect = { draft = draft.copy(tool = it) },
                        )
                    }
                    RangeInputRow(
                        label = "宽度",
                        startValue = draft.widthMin?.toString().orEmpty(),
                        endValue = draft.widthMax?.toString().orEmpty(),
                        startPlaceholder = "最小 px",
                        endPlaceholder = "最大 px",
                        onStart = { draft = draft.copy(widthMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(widthMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = "高度",
                        startValue = draft.heightMin?.toString().orEmpty(),
                        endValue = draft.heightMax?.toString().orEmpty(),
                        startPlaceholder = "最小 px",
                        endPlaceholder = "最大 px",
                        onStart = { draft = draft.copy(heightMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(heightMax = it.toIntOrNull()) },
                    )
                }

                // 小说专属（搜索结果时展示）
                if (detailed && type == SearchType.NOVEL) {
                    SectionTitle("小说专属")
                    if (genreOptions.isNotEmpty()) {
                        SectionTitle("题材")
                        LazyScrollChips(
                            options = genreOptions.map { it.id.toString() to (it.label ?: "未知") },
                            selected = draft.genre?.toString(),
                            onSelect = { draft = draft.copy(genre = it?.toIntOrNull()) },
                        )
                    }
                    SwitchRow("仅原创", draft.isOriginalOnly == true) { draft = draft.copy(isOriginalOnly = it) }
                    SwitchRow("仅可转载", draft.isReplaceableOnly == true) { draft = draft.copy(isReplaceableOnly = it) }
                    RangeInputRow(
                        label = "正文文字数",
                        startValue = draft.textLengthMin?.toString().orEmpty(),
                        endValue = draft.textLengthMax?.toString().orEmpty(),
                        startPlaceholder = "最小",
                        endPlaceholder = "最大",
                        onStart = { draft = draft.copy(textLengthMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(textLengthMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = "字数",
                        startValue = draft.wordCountMin?.toString().orEmpty(),
                        endValue = draft.wordCountMax?.toString().orEmpty(),
                        startPlaceholder = "最小",
                        endPlaceholder = "最大",
                        onStart = { draft = draft.copy(wordCountMin = it.toIntOrNull()) },
                        onEnd = { draft = draft.copy(wordCountMax = it.toIntOrNull()) },
                    )
                    RangeInputRow(
                        label = "阅读时长",
                        startValue = draft.readingTimeMin?.toString().orEmpty(),
                        endValue = draft.readingTimeMax?.toString().orEmpty(),
                        startPlaceholder = "最小 分钟",
                        endPlaceholder = "最大",
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
                        Text("重置")
                    }
                    Button(
                        onClick = { onApply(draft) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("应用筛选")
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
                label = { Text("不限") },
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
