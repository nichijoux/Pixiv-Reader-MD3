package com.pixiv.reader.core.ranking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ranking.R
import com.pixiv.reader.core.ui.R as UiR
import com.pixiv.reader.core.ui.component.list.parseDatePickerMillis
import com.pixiv.reader.core.ui.component.list.formatDatePickerMillis
import com.pixiv.reader.core.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** pixiv 榜单起点，更早日期接口返回空榜单。 */
private val MIN_RANKING_DATE: LocalDate = LocalDate.of(2007, 9, 7)

/**
 * TopAppBar 日期筛选入口：已选日期时图标着主色，提示当前处于历史榜单视图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingDatePickerButton(
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    IconButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = stringResource(R.string.core_ranking_pick_date),
            tint = if (selectedDate != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
    if (showPicker) {
        RankingDatePickerDialog(
            selectedDate = selectedDate,
            onSelect = onSelectDate,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 日期 chip 行（作 [RankingList] 的 listHeader 使用，渲染在 TabRow 上方、限宽内容块内）：
 * 点 chip 本体重选日期、点 × 回到最新榜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingDateChipRow(
    date: String,
    onSelectDate: (String?) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            onClick = { showPicker = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.mdPlus, top = Spacing.xxs, bottom = Spacing.xxs, end = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
                // 嵌套可点：点 × 只清日期，不向外冒泡到 chip 的选日期
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.core_ranking_back_to_latest),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.core_ranking_history_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showPicker) {
        RankingDatePickerDialog(
            selectedDate = date,
            onSelect = onSelectDate,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 日期选择弹窗：仅允许选 [MIN_RANKING_DATE] ~ 昨天——当日榜约中午才生成，传当天/未来日期接口返回空；
 * 默认定位到昨天。仅开放日历模式（showModeToggle = false），点标题年月可弹年份列表快选较早日期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingDatePickerDialog(
    selectedDate: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker 的毫秒即 UTC 零点基准，区间边界按 UTC 零点算
    val minSelectableMillis = remember {
        MIN_RANKING_DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    // 日榜当天未生成，最新只能查昨日（同样按 UTC 零点算）
    val lastSelectableMillis = remember {
        LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    // SelectableDates 固化为单例：月历每格都会调 isSelectableDate，退化为纯区间比较避免逐格分配对象
    val selectableDates = remember(minSelectableMillis, lastSelectableMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in minSelectableMillis..lastSelectableMillis
        }
    }
    val initialMillis = remember(selectedDate, lastSelectableMillis) {
        selectedDate?.let(::parseDatePickerMillis) ?: lastSelectableMillis
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis -> onSelect(formatDatePickerMillis(millis)) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(UiR.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.common_cancel)) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
