package com.pixiv.reader.feature.discover.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.SearchGenreOption
import com.pixiv.api.model.SearchLangOption
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.layout.SectionTitle
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.AiFilter
import com.pixiv.reader.feature.discover.state.R18Filter
import com.pixiv.reader.feature.discover.state.SearchFilters
import com.pixiv.reader.feature.discover.state.SearchType
import com.pixiv.reader.core.ui.component.list.formatDatePickerMillis
import com.pixiv.reader.core.ui.component.list.parseDatePickerMillis
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

private sealed interface Picker {
    data class Simple(
        val title: String,
        val labels: List<String>,
        val selected: Int,
        val headerIndices: Set<Int> = emptySet(),
        val onSelect: (Int) -> Unit,
    ) : Picker

    data object Duration : Picker
    data object DateRange : Picker
    data object BodyLength : Picker
    data object Other : Picker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheet(
    filters: SearchFilters,
    type: SearchType,
    toolOptions: List<String>,
    genreOptions: List<SearchGenreOption>,
    langOptions: List<SearchLangOption>,
    isPremium: Boolean,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    var picker by remember { mutableStateOf<Picker?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
    // transitionSpec 非组合上下文，动效规格需在组合期提前读取
    val slideInSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val slideOutSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = picker,
        transitionSpec = {
            val forward = targetState != null && initialState == null
            if (forward) {
                (slideInHorizontally(animationSpec = slideInSpec) { it } + fadeIn(animationSpec = fadeSpec))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = slideOutSpec) { -it / 3 } +
                            fadeOut(animationSpec = fadeSpec)
                    )
            } else {
                (slideInHorizontally(animationSpec = slideInSpec) { -it } + fadeIn(animationSpec = fadeSpec))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = slideOutSpec) { it / 3 } +
                            fadeOut(animationSpec = fadeSpec)
                    )
            }
        },
        label = "filterPicker",
    ) { p ->
            when (p) {
                null -> MainFilterContent(
                    draft = draft,
                    type = type,
                    isPremium = isPremium,
                    toolOptions = toolOptions,
                    genreOptions = genreOptions,
                    langOptions = langOptions,
                    onDraftChange = { draft = it },
                    onOpenPicker = { picker = it },
                    onApply = { onApply(draft); onDismiss() },
                    onReset = { draft = SearchFilters() },
                    onDismiss = onDismiss,
                )

                is Picker.Simple -> PickerListContent(
                    title = p.title,
                    labels = p.labels,
                    selected = p.selected,
                    headerIndices = p.headerIndices,
                    onSelect = { idx -> p.onSelect(idx); picker = null },
                    onClose = { picker = null },
                )

                Picker.Duration -> DurationPickerContent(
                    durationBucket = draft.durationBucket,
                    hasCustom = draft.startDate != null || draft.endDate != null,
                    onPick = { bucket ->
                        draft =
                            draft.copy(durationBucket = bucket, startDate = null, endDate = null)
                        picker = null
                    },
                    onOpenCustom = { picker = Picker.DateRange },
                    onClose = { picker = null },
                )

                Picker.DateRange -> DateRangeContent(
                    startDate = draft.startDate,
                    endDate = draft.endDate,
                    onConfirm = { s, e ->
                        draft = draft.copy(durationBucket = null, startDate = s, endDate = e)
                        picker = null
                    },
                    onClose = { picker = null },
                )

                Picker.BodyLength -> BodyLengthPickerContent(
                    unit = draft.bodyLengthUnit,
                    min = draft.bodyLengthMin,
                    max = draft.bodyLengthMax,
                    onPick = { unit, min, max ->
                        draft = draft.copy(
                            bodyLengthUnit = unit.takeIf { it >= 0 },
                            bodyLengthMin = min, bodyLengthMax = max,
                        )
                        picker = null
                    },
                    onClose = { picker = null },
                )

                Picker.Other -> OtherPickerContent(
                    draft = draft,
                    type = type,
                    toolOptions = toolOptions,
                    onConfirm = { ai: AiFilter, r18: R18Filter, originalOnly: Boolean?, replaceableOnly: Boolean?, tool: String? ->
                        draft = draft.copy(
                            aiType = ai, r18Mode = r18,
                            isOriginalOnly = originalOnly, isReplaceableOnly = replaceableOnly,
                            tool = tool,
                        )
                        picker = null
                    },
                    onClose = { picker = null },
                )
            }
        }
    }
}

@Composable
private fun MainFilterContent(
    draft: SearchFilters,
    type: SearchType,
    isPremium: Boolean,
    toolOptions: List<String>,
    genreOptions: List<SearchGenreOption>,
    langOptions: List<SearchLangOption>,
    onDraftChange: (SearchFilters) -> Unit,
    onOpenPicker: (Picker) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // onClick 非组合上下文，所有 picker 文案需在组合期提前构造
    val isNovel = type == SearchType.NOVEL
    val targetPicker = Picker.Simple(
        stringResource(R.string.filter_row_target),
        targetLabels(type),
        targetLabels(type).indexOf(targetSummary(draft.searchTarget)).coerceAtLeast(0),
        onSelect = { idx -> onDraftChange(draft.copy(searchTarget = (if (isNovel) NOVEL_TARGETS else ILLUST_TARGETS)[idx])) },
    )
    val sortPicker = Picker.Simple(
        stringResource(R.string.filter_row_sort),
        sortLabels(isPremium),
        sortLabels(isPremium).indexOf(sortSummary(draft.sort)).coerceAtLeast(0),
        onSelect = { idx ->
            val s = sortValues(isPremium)[idx]
            // 时间排序与标题简介匹配互斥（400）：选时间排序时匹配方式回退默认
            onDraftChange(
                if (s == "date_desc" || s == "date_asc") {
                    draft.copy(sort = s, searchTarget = "partial_match_for_tags")
                } else {
                    draft.copy(sort = s)
                },
            )
        },
    )
    val contentTypePicker = Picker.Simple(
        stringResource(R.string.filter_row_content_type),
        CONTENT_TYPE_LABELS(),
        contentTypeIndex(draft.contentType),
        onSelect = { idx -> onDraftChange(draft.copy(contentType = CONTENT_TYPE_VALUES[idx])) },
    )
    val bookmarkPicker = Picker.Simple(
        stringResource(R.string.filter_row_bookmark),
        BOOKMARK_LABELS(),
        BOOKMARK_VALUES.indexOf(draft.bookmarkNumMin ?: 0).coerceAtLeast(0),
        onSelect = { idx -> onDraftChange(draft.copy(bookmarkNumMin = BOOKMARK_VALUES[idx].takeIf { it > 0 })) },
    )
    val keywordBookmarkPicker = Picker.Simple(
        stringResource(R.string.filter_row_keyword_bookmark),
        KEYWORD_USERS_LABELS(),
        KEYWORD_USERS_VALUES.indexOf(draft.keywordUsersBucket ?: 0).coerceAtLeast(0),
        onSelect = { idx -> onDraftChange(draft.copy(keywordUsersBucket = KEYWORD_USERS_VALUES[idx].takeIf { it > 0 })) },
    )
    val genrePicker = Picker.Simple(
        stringResource(R.string.filter_row_genre),
        listOf(stringResource(R.string.filter_all_summary)) + genreOptions.map { it.label ?: "" },
        if (draft.genre == null) 0 else genreOptions.indexOfFirst { it.id == draft.genre }
            .let { if (it < 0) 0 else it + 1 },
        onSelect = { idx ->
            onDraftChange(
                draft.copy(
                    genre = if (idx == 0) null else genreOptions.getOrNull(
                        idx - 1
                    )?.id
                )
            )
        },
    )
    val langPicker = Picker.Simple(
        stringResource(R.string.filter_row_lang),
        listOf(stringResource(R.string.filter_all_summary)) + langOptions.map {
            it.name ?: it.code ?: ""
        },
        if (draft.lang == null) 0 else langOptions.indexOfFirst { it.code == draft.lang }
            .let { if (it < 0) 0 else it + 1 },
        onSelect = { idx ->
            onDraftChange(
                draft.copy(
                    lang = if (idx == 0) null else langOptions.getOrNull(
                        idx - 1
                    )?.code
                )
            )
        },
    )
    val ratioPicker = Picker.Simple(
        stringResource(R.string.filter_row_ratio),
        RATIO_LABELS(),
        ratioIndex(draft.ratioPattern),
        onSelect = { idx -> onDraftChange(draft.copy(ratioPattern = if (idx == 0) null else RATIO_VALUES[idx - 1])) },
    )
    val resolutionPicker = Picker.Simple(
        stringResource(R.string.filter_row_resolution),
        RESOLUTION_LABELS(),
        resolutionIndex(draft.resolutionBucket),
        onSelect = { idx -> onDraftChange(draft.copy(resolutionBucket = if (idx == 0) null else RESOLUTION_VALUES[idx - 1])) },
    )

    Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lgPlus, end = Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.filter_title_default),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lgPlus),
        ) {
            FilterCard(
                icon = Icons.Filled.Tune,
                accent = SettingAccentBlue,
            ) {
                FilterRow(
                    stringResource(R.string.filter_row_target),
                    targetSummary(draft.searchTarget)
                ) {
                    onOpenPicker(targetPicker)
                }
                FilterRow(
                    stringResource(R.string.filter_row_sort),
                    sortSummary(draft.sort)
                ) {
                    onOpenPicker(sortPicker)
                }
            }
            FilterCard(
                icon = Icons.Filled.FilterAlt,
                accent = SettingAccentTeal,
            ) {
                FilterRow(stringResource(R.string.filter_row_duration), durationSummary(draft)) {
                    onOpenPicker(Picker.Duration)
                }
                FilterRow(
                    stringResource(R.string.filter_row_bookmark),
                    bookmarkSummary(draft.bookmarkNumMin)
                ) {
                    onOpenPicker(bookmarkPicker)
                }
                FilterRow(
                    stringResource(R.string.filter_row_keyword_bookmark),
                    keywordBookmarkSummary(draft.keywordUsersBucket)
                ) {
                    onOpenPicker(keywordBookmarkPicker)
                }
                if (!isNovel) {
                    FilterRow(
                        stringResource(R.string.filter_row_content_type),
                        contentTypeSummary(draft.contentType)
                    ) {
                        onOpenPicker(contentTypePicker)
                    }
                    FilterRow(
                        stringResource(R.string.filter_row_ratio),
                        ratioSummary(draft.ratioPattern)
                    ) {
                        onOpenPicker(ratioPicker)
                    }
                    FilterRow(
                        stringResource(R.string.filter_row_resolution),
                        resolutionSummary(draft.resolutionBucket)
                    ) {
                        onOpenPicker(resolutionPicker)
                    }
                }
                if (isNovel) {
                    FilterRow(
                        stringResource(R.string.filter_row_genre),
                        genreSummary(draft.genre, genreOptions)
                    ) {
                        onOpenPicker(genrePicker)
                    }
                    FilterRow(
                        stringResource(R.string.filter_row_lang),
                        langSummary(draft.lang, langOptions)
                    ) {
                        onOpenPicker(langPicker)
                    }
                    FilterRow(
                        stringResource(R.string.filter_row_body_length),
                        bodyLengthSummary(draft)
                    ) {
                        onOpenPicker(Picker.BodyLength)
                    }
                }
            }
            FilterCard(
                icon = Icons.Filled.MoreHoriz,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                FilterRow(stringResource(R.string.filter_row_other), otherSummary(draft, type)) {
                    onOpenPicker(Picker.Other)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lgPlus, end = Spacing.lgPlus),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalButton(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.filter_reset))
            }
            Button(
                onClick = onApply,
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.search_action))
            }
        }
    }
}

// 分组图标点缀色由 AccentIconBadge 经 resolveAccent 解析，彩色风格关闭时回退主题主色
@Composable
private fun FilterCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
    ) {
        Column {
            if (icon != null) {
                Row(
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.md,
                        bottom = Spacing.xs,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentIconBadge(
                        icon = icon,
                        accent = accent,
                        size = Sizes.s28,
                        cornerRadius = 9.dp,
                        iconSize = Sizes.s16,
                    )
                }
            }
            Column(content = content)
        }
    }
}

@Composable
private fun FilterRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.lg, top = 13.dp, bottom = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = Spacing.xs),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s20),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 13.dp), thickness = 0.5.dp)
    }
}

@Composable
private fun PickerListContent(
    title: String,
    labels: List<String>,
    selected: Int,
    headerIndices: Set<Int>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = Spacing.xl)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lgPlus),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lgPlus)
                .verticalScroll(rememberScrollState()),
        ) {
            labels.forEachIndexed { idx, label ->
                if (idx in headerIndices) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.mdPlus, bottom = Spacing.xsPlus),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(idx) }
                            .padding(vertical = Spacing.mdPlus),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (idx == selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun DurationPickerContent(
    durationBucket: String?,
    hasCustom: Boolean,
    onPick: (String?) -> Unit,
    onOpenCustom: () -> Unit,
    onClose: () -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.filter_duration_all),
        stringResource(R.string.filter_duration_24h),
        stringResource(R.string.filter_duration_week),
        stringResource(R.string.filter_duration_month),
        stringResource(R.string.filter_duration_half_year),
        stringResource(R.string.filter_duration_year),
        stringResource(R.string.filter_duration_custom),
    )
    val selected = when {
        durationBucket == null && hasCustom -> 6
        durationBucket == "Last24Hours" -> 1
        durationBucket == "LastWeek" -> 2
        durationBucket == "LastMonth" -> 3
        durationBucket == "LastHalfYear" -> 4
        durationBucket == "LastYear" -> 5
        else -> 0
    }
    PickerListContent(
        title = stringResource(R.string.filter_row_duration),
        labels = labels,
        selected = selected,
        headerIndices = emptySet(),
        onSelect = { idx ->
            if (idx == 6) onOpenCustom() else onPick(DURATION_VALUES[idx])
        },
        onClose = onClose,
    )
}

private enum class DateSlot { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeContent(
    startDate: String?,
    endDate: String?,
    onConfirm: (String?, String?) -> Unit,
    onClose: () -> Unit,
) {
    var pickingSlot by remember { mutableStateOf<DateSlot?>(null) }
    var draftStart by remember { mutableStateOf(startDate) }
    var draftEnd by remember { mutableStateOf(endDate) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = Spacing.xl)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lgPlus),
        ) {
            Text(
                text = stringResource(R.string.filter_date_range),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
            }
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.lgPlus)) {
            DateFieldRow(stringResource(R.string.filter_date_start), draftStart) {
                pickingSlot = DateSlot.START
            }
            DateFieldRow(stringResource(R.string.filter_date_end), draftEnd) {
                pickingSlot = DateSlot.END
            }
            Button(
                onClick = { onConfirm(draftStart, draftEnd) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lgPlus)
                    .height(48.dp),
            ) { Text(stringResource(R.string.common_confirm)) }
        }
    }

    pickingSlot?.let { slot ->
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (slot == DateSlot.START) draftStart else draftEnd)?.let(::parseDatePickerMillis),
        )
        DatePickerDialog(
            onDismissRequest = { pickingSlot = null },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickerState.selectedDateMillis?.let(::formatDatePickerMillis)
                    if (slot == DateSlot.START) draftStart = date else draftEnd = date
                    pickingSlot = null
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickingSlot = null
                }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateFieldRow(label: String, value: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.mdPlus),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value ?: stringResource(R.string.filter_unlimited),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun BodyLengthPickerContent(
    unit: Int?,
    min: Int?,
    max: Int?,
    onPick: (Int, Int?, Int?) -> Unit,
    onClose: () -> Unit,
) {
    var customUnit by remember { mutableStateOf<Int?>(null) }
    val labels = buildList {
        add(stringResource(R.string.filter_body_length_all))
        add(stringResource(R.string.filter_body_length_section_char))
        CHAR_BUCKETS.forEach { add(stringResource(it.labelRes)) }
        add(stringResource(R.string.filter_body_length_custom_char))
        add(stringResource(R.string.filter_body_length_section_word))
        WORD_BUCKETS.forEach { add(stringResource(it.labelRes)) }
        add(stringResource(R.string.filter_body_length_custom_word))
        add(stringResource(R.string.filter_body_length_section_time))
        TIME_BUCKETS.forEach { add(stringResource(it.labelRes)) }
        add(stringResource(R.string.filter_reading_time_custom))
    }
    val headers = setOf(1, 7, 13)

    PickerListContent(
        title = stringResource(R.string.filter_row_body_length),
        labels = labels,
        selected = bodyLengthSelectedIdx(unit, min, max),
        headerIndices = headers,
        onSelect = { idx -> handleBodyLengthPick(idx, onPick) { customUnit = it } },
        onClose = onClose,
    )

    // 只预填同单位的范围，避免把字符数带入阅读分钟等字段。
    customUnit?.let { selectedUnit ->
        BodyLengthRangeDialog(
            unit = selectedUnit,
            initialMin = min.takeIf { unit == selectedUnit },
            initialMax = max.takeIf { unit == selectedUnit },
            onDismiss = { customUnit = null },
            onConfirm = { lower, upper ->
                customUnit = null
                onPick(selectedUnit, lower, upper)
            },
        )
    }
}

@Composable
private fun BodyLengthRangeDialog(
    unit: Int,
    initialMin: Int?,
    initialMax: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Int?) -> Unit,
) {
    var minInput by remember { mutableStateOf(initialMin?.toString().orEmpty()) }
    var maxInput by remember { mutableStateOf(initialMax?.toString().orEmpty()) }
    val min = minInput.trim().toIntOrNull()?.takeIf { it > 0 }
    val max = maxInput.trim().toIntOrNull()?.takeIf { it > 0 }
    val invalidMin = minInput.isNotBlank() && min == null
    val invalidMax = maxInput.isNotBlank() && max == null
    val reversed = min != null && max != null && min > max
    val valid = !invalidMin && !invalidMax && !reversed && (min != null || max != null)
    val title = when (unit) {
        0 -> R.string.filter_body_length_custom_char
        1 -> R.string.filter_body_length_custom_word
        else -> R.string.filter_reading_time_custom
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(
                        if (unit == 2) R.string.filter_body_length_minutes_hint
                        else R.string.filter_body_length_bounds_hint,
                    ),
                )
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    label = { Text(stringResource(R.string.filter_body_length_minimum)) },
                    singleLine = true,
                    isError = invalidMin || reversed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it },
                    label = { Text(stringResource(R.string.filter_body_length_maximum)) },
                    singleLine = true,
                    isError = invalidMax || reversed,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalidMin || invalidMax || reversed) {
                    Text(
                        text = stringResource(R.string.filter_body_length_invalid_bounds),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(min, max) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun bodyLengthSelectedIdx(unit: Int?, min: Int?, max: Int?): Int = when (unit) {
    0 -> {
        val b = CHAR_BUCKETS.indexOfFirst { it.min == min && it.max == max }
        if (b >= 0) 2 + b else 6
    }

    1 -> {
        val b = WORD_BUCKETS.indexOfFirst { it.min == min && it.max == max }
        if (b >= 0) 8 + b else 12
    }

    2 -> {
        val b = TIME_BUCKETS.indexOfFirst { it.min == min && it.max == max }
        if (b >= 0) 14 + b else 18
    }

    else -> 0
}

private fun handleBodyLengthPick(
    idx: Int,
    onPick: (Int, Int?, Int?) -> Unit,
    onPickCustom: (Int) -> Unit,
) {
    when (idx) {
        0 -> onPick(-1, null, null) // -1 表示不限（清空 unit）
        in 2..5 -> {
            val b = CHAR_BUCKETS[idx - 2]
            onPick(0, b.min, b.max)
        }

        6 -> onPickCustom(0)
        in 8..11 -> {
            val b = WORD_BUCKETS[idx - 8]
            onPick(1, b.min, b.max)
        }

        12 -> onPickCustom(1)
        in 14..17 -> {
            val b = TIME_BUCKETS[idx - 14]
            onPick(2, b.min, b.max)
        }

        18 -> onPickCustom(2)
    }
}

@Composable
private fun OtherPickerContent(
    draft: SearchFilters,
    type: SearchType,
    toolOptions: List<String>,
    onConfirm: (ai: AiFilter, r18: R18Filter, originalOnly: Boolean?, replaceableOnly: Boolean?, tool: String?) -> Unit,
    onClose: () -> Unit,
) {
    var ai by remember { mutableStateOf(draft.aiType) }
    var r18 by remember { mutableStateOf(draft.r18Mode) }
    var originalOnly by remember { mutableStateOf(draft.isOriginalOnly == true) }
    var replaceableOnly by remember { mutableStateOf(draft.isReplaceableOnly == true) }
    var tool by remember { mutableStateOf(draft.tool) }
    var showToolPicker by remember { mutableStateOf(false) }
    val isNovel = type == SearchType.NOVEL

    if (showToolPicker) {
        val labels = listOf(stringResource(R.string.filter_all_summary)) + toolOptions
        val selected =
            tool?.let { toolOptions.indexOf(it).let { i -> if (i < 0) 0 else i + 1 } } ?: 0
        PickerListContent(
            title = stringResource(R.string.filter_row_tool),
            labels = labels,
            selected = selected,
            headerIndices = emptySet(),
            onSelect = { idx ->
                tool = if (idx == 0) null else toolOptions.getOrNull(idx - 1); showToolPicker =
                false
            },
            onClose = { showToolPicker = false },
        )
        return
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = Spacing.xl)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lgPlus),
        ) {
            Text(
                text = stringResource(R.string.filter_row_other),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lgPlus)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(
                text = stringResource(R.string.filter_ai),
                modifier = Modifier.padding(top = Spacing.lg),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            val aiOptions = listOf(
                AiFilter.ALL to R.string.filter_all,
                AiFilter.HUMAN_ONLY to R.string.filter_ai_human,
                AiFilter.AI_ONLY to R.string.filter_ai_only,
            )
            PillSegmentedRow(
                options = aiOptions,
                selected = ai,
                onSelect = { ai = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isNovel) {
                SectionTitle(
                    text = stringResource(R.string.filter_row_tool),
                    modifier = Modifier.padding(top = Spacing.lg),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showToolPicker = true }
                        .padding(vertical = Spacing.mdPlus),
                ) {
                    Text(
                        text = stringResource(R.string.filter_row_tool),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = tool ?: stringResource(R.string.filter_all_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isNovel) {
                SectionTitle(
                    text = stringResource(R.string.filter_row_novel_switches),
                    modifier = Modifier.padding(top = Spacing.lg),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SwitchRow(
                    stringResource(R.string.filter_original_only),
                    originalOnly
                ) { originalOnly = it }
                SwitchRow(
                    stringResource(R.string.filter_replaceable_only),
                    replaceableOnly
                ) { replaceableOnly = it }
            }

            SectionTitle(
                text = stringResource(R.string.filter_r18),
                modifier = Modifier.padding(top = Spacing.lg),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            val r18Options = listOf(
                R18Filter.ALL to R.string.filter_all,
                R18Filter.SAFE_ONLY to R.string.filter_r18_safe,
                R18Filter.R18_ONLY to R.string.filter_r18_only,
            )
            PillSegmentedRow(
                options = r18Options,
                selected = r18,
                onSelect = { r18 = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onConfirm(
                        ai, r18,
                        if (isNovel) originalOnly else null,
                        if (isNovel) replaceableOnly else null,
                        if (isNovel) null else tool,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lgPlus)
                    .height(48.dp),
            ) { Text(stringResource(R.string.common_confirm)) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = Spacing.smPlus),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun targetSummary(target: String): String = stringResource(
    when (target) {
        "exact_match_for_tags" -> R.string.filter_match_exact
        "title_and_caption" -> R.string.filter_match_title
        "text" -> R.string.filter_match_text
        "keyword" -> R.string.filter_match_keyword
        else -> R.string.filter_match_partial
    },
)

@Composable
private fun targetLabels(type: SearchType): List<String> {
    val isNovel = type == SearchType.NOVEL
    return (if (isNovel) NOVEL_TARGETS else ILLUST_TARGETS).map {
        stringResource(
            when (it) {
                "exact_match_for_tags" -> R.string.filter_match_exact
                "title_and_caption" -> R.string.filter_match_title
                "text" -> R.string.filter_match_text
                "keyword" -> R.string.filter_match_keyword
                else -> R.string.filter_match_partial
            },
        )
    }
}

@Composable
private fun sortSummary(sort: String): String = stringResource(
    when (sort) {
        "popular_preview" -> R.string.filter_sort_preview
        "date_desc" -> R.string.filter_latest
        "date_asc" -> R.string.filter_oldest
        "popular_male_desc" -> R.string.filter_sort_male
        "popular_female_desc" -> R.string.filter_sort_female
        else -> R.string.filter_popular
    },
)

private fun sortValues(isPremium: Boolean): List<String> = buildList {
    add("popular_preview")
    add("date_desc")
    add("date_asc")
    add("popular_desc")
    if (isPremium) {
        add("popular_male_desc")
        add("popular_female_desc")
    }
}

@Composable
private fun sortLabels(isPremium: Boolean): List<String> = buildList {
    add(stringResource(R.string.filter_sort_preview))
    add(stringResource(R.string.filter_latest))
    add(stringResource(R.string.filter_oldest))
    add(stringResource(R.string.filter_popular))
    if (isPremium) {
        add(stringResource(R.string.filter_sort_male))
        add(stringResource(R.string.filter_sort_female))
    }
}

@Composable
private fun contentTypeSummary(value: String?): String = stringResource(
    when (value) {
        "illust_and_ugoira" -> R.string.filter_content_illust_ugoira
        "illust" -> R.string.filter_content_illust
        "ugoira" -> R.string.filter_content_ugoira
        "manga" -> R.string.filter_content_manga
        else -> R.string.filter_content_default
    },
)

@Composable
private fun durationSummary(f: SearchFilters): String = when (f.durationBucket) {
    "Last24Hours" -> stringResource(R.string.filter_duration_24h)
    "LastWeek" -> stringResource(R.string.filter_duration_week)
    "LastMonth" -> stringResource(R.string.filter_duration_month)
    "LastHalfYear" -> stringResource(R.string.filter_duration_half_year)
    "LastYear" -> stringResource(R.string.filter_duration_year)
    else -> if (f.startDate != null || f.endDate != null) {
        "${f.startDate ?: "—"} → ${f.endDate ?: "—"}"
    } else stringResource(R.string.filter_duration_all)
}

@Composable
private fun bookmarkSummary(min: Int?): String =
    if (min == null || min <= 0) stringResource(R.string.filter_unlimited)
    else stringResource(R.string.filter_bookmark_min_fmt, min)

@Composable
private fun keywordBookmarkSummary(bucket: Int?): String =
    if (bucket == null || bucket <= 0) stringResource(R.string.filter_unlimited)
    else stringResource(R.string.filter_keyword_bucket_fmt, bucket)

@Composable
private fun genreSummary(genre: Int?, options: List<SearchGenreOption>): String {
    if (genre == null) return stringResource(R.string.filter_all_summary)
    return options.firstOrNull { it.id == genre }?.label
        ?: stringResource(R.string.filter_all_summary)
}

@Composable
private fun langSummary(lang: String?, options: List<SearchLangOption>): String {
    if (lang == null) return stringResource(R.string.filter_all_summary)
    return options.firstOrNull { it.code == lang }?.name ?: lang
}

@Composable
private fun ratioSummary(pattern: String?): String = stringResource(
    when (pattern) {
        "landscape" -> R.string.filter_ratio_landscape
        "portrait" -> R.string.filter_ratio_portrait
        "square" -> R.string.filter_ratio_square
        else -> R.string.filter_all_summary
    },
)

@Composable
private fun resolutionSummary(bucket: String?): String = stringResource(
    when (bucket) {
        "Above3000" -> R.string.filter_resolution_above_3000
        "Between1000And2999" -> R.string.filter_resolution_1000_2999
        "Below1000" -> R.string.filter_resolution_below_1000
        else -> R.string.filter_all_summary
    },
)

@Composable
private fun bodyLengthSummary(f: SearchFilters): String {
    val unit = f.bodyLengthUnit ?: return stringResource(R.string.filter_body_length_all)
    return when (unit) {
        0 -> CHAR_BUCKETS.firstOrNull { it.min == f.bodyLengthMin && it.max == f.bodyLengthMax }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(
                R.string.filter_body_length_custom_char_fmt,
                rangeText(f.bodyLengthMin, f.bodyLengthMax)
            )

        1 -> WORD_BUCKETS.firstOrNull { it.min == f.bodyLengthMin && it.max == f.bodyLengthMax }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(
                R.string.filter_body_length_custom_word_fmt,
                rangeText(f.bodyLengthMin, f.bodyLengthMax)
            )

        else -> TIME_BUCKETS.firstOrNull { it.min == f.bodyLengthMin && it.max == f.bodyLengthMax }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(
                R.string.filter_reading_time_custom_fmt,
                rangeText(f.bodyLengthMin, f.bodyLengthMax)
            )
    }
}

private fun rangeText(min: Int?, max: Int?): String = when {
    min != null && max != null -> "$min–$max"
    min != null -> "≥$min"
    max != null -> "≤$max"
    else -> "—"
}

@Composable
private fun otherSummary(f: SearchFilters, type: SearchType): String {
    val isNovel = type == SearchType.NOVEL
    val flags = mutableListOf<String>()
    when (f.aiType) {
        AiFilter.HUMAN_ONLY -> flags += stringResource(R.string.filter_ai_human)
        AiFilter.AI_ONLY -> flags += stringResource(R.string.filter_ai_only)
        else -> {}
    }
    when (f.r18Mode) {
        R18Filter.SAFE_ONLY -> flags += stringResource(R.string.filter_r18_safe)
        R18Filter.R18_ONLY -> flags += stringResource(R.string.filter_r18_only)
        else -> {}
    }
    if (!isNovel && f.tool != null) flags += f.tool
    if (isNovel && f.isOriginalOnly == true) flags += stringResource(R.string.filter_original_only)
    if (isNovel && f.isReplaceableOnly == true) flags += stringResource(R.string.filter_replaceable_only)
    return if (flags.isEmpty()) stringResource(R.string.filter_other_none) else flags.joinToString(" · ")
}

private val ILLUST_TARGETS =
    listOf("partial_match_for_tags", "exact_match_for_tags", "title_and_caption")
private val NOVEL_TARGETS =
    listOf("partial_match_for_tags", "exact_match_for_tags", "text", "keyword")

private val CONTENT_TYPE_VALUES = listOf(
    null, "illust_and_ugoira", "illust", "ugoira", "manga",
)

@Composable
private fun CONTENT_TYPE_LABELS(): List<String> = listOf(
    stringResource(R.string.filter_content_default),
    stringResource(R.string.filter_content_illust_ugoira),
    stringResource(R.string.filter_content_illust),
    stringResource(R.string.filter_content_ugoira),
    stringResource(R.string.filter_content_manga),
)

private fun contentTypeIndex(value: String?): Int = when (value) {
    "illust_and_ugoira" -> 1
    "illust" -> 2
    "ugoira" -> 3
    "manga" -> 4
    else -> 0
}

private val BOOKMARK_VALUES =
    listOf(0, 100, 500, 1000, 2000, 5000, 7500, 10000, 20000, 30000, 50000, 100000)

@Composable
private fun BOOKMARK_LABELS(): List<String> = BOOKMARK_VALUES.map {
    if (it == 0) stringResource(R.string.filter_unlimited) else stringResource(
        R.string.filter_bookmark_min_fmt,
        it
    )
}

private val KEYWORD_USERS_VALUES =
    listOf(0, 500, 1000, 2000, 5000, 7500, 10000, 20000, 50000, 100000)

@Composable
private fun KEYWORD_USERS_LABELS(): List<String> = KEYWORD_USERS_VALUES.map {
    if (it == 0) stringResource(R.string.filter_unlimited)
    else stringResource(R.string.filter_keyword_bucket_fmt, it)
}

private val RATIO_VALUES = listOf("landscape", "portrait", "square")

@Composable
private fun RATIO_LABELS(): List<String> = listOf(
    stringResource(R.string.filter_all_summary),
    stringResource(R.string.filter_ratio_landscape),
    stringResource(R.string.filter_ratio_portrait),
    stringResource(R.string.filter_ratio_square),
)

private fun ratioIndex(pattern: String?): Int = when (pattern) {
    "landscape" -> 1
    "portrait" -> 2
    "square" -> 3
    else -> 0
}

private val RESOLUTION_VALUES = listOf("Above3000", "Between1000And2999", "Below1000")

@Composable
private fun RESOLUTION_LABELS(): List<String> = listOf(
    stringResource(R.string.filter_all_summary),
    stringResource(R.string.filter_resolution_above_3000),
    stringResource(R.string.filter_resolution_1000_2999),
    stringResource(R.string.filter_resolution_below_1000),
)

private fun resolutionIndex(bucket: String?): Int = when (bucket) {
    "Above3000" -> 1
    "Between1000And2999" -> 2
    "Below1000" -> 3
    else -> 0
}

private val DURATION_VALUES =
    listOf(null, "Last24Hours", "LastWeek", "LastMonth", "LastHalfYear", "LastYear")

private data class RangeSpec(val min: Int?, val max: Int?, val labelRes: Int)

private val CHAR_BUCKETS = listOf(
    RangeSpec(null, 4999, R.string.filter_char_micro),
    RangeSpec(5000, 19999, R.string.filter_char_short),
    RangeSpec(20000, 79999, R.string.filter_char_medium),
    RangeSpec(80000, null, R.string.filter_char_long),
)
private val WORD_BUCKETS = listOf(
    RangeSpec(null, 4999, R.string.filter_word_below_5000),
    RangeSpec(5000, 19999, R.string.filter_word_5000_19999),
    RangeSpec(20000, 79999, R.string.filter_word_20000_79999),
    RangeSpec(80000, null, R.string.filter_word_above_80000),
)
private val TIME_BUCKETS = listOf(
    RangeSpec(null, 9, R.string.filter_time_under_10),
    RangeSpec(10, 59, R.string.filter_time_10_59),
    RangeSpec(60, 179, R.string.filter_time_60_179),
    RangeSpec(180, null, R.string.filter_time_above_180),
)
