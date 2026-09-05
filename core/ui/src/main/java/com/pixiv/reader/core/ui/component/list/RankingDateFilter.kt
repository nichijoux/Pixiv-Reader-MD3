package com.pixiv.reader.core.ui.component.list

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
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 排行榜可查询的最早日期（pixiv 榜单起点，更早日期接口返回空榜单）。 */
private val MIN_RANKING_DATE: LocalDate = LocalDate.of(2007, 9, 7)

/**
 * yyyy-MM-dd → DatePicker 使用的 UTC 零点毫秒。
 *
 * @param date 日期字符串（yyyy-MM-dd）
 * @return 对应 UTC 零点毫秒；格式非法时返回 null
 */
private fun parseDateMillis(date: String): Long? = runCatching {
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

/**
 * DatePicker 的 UTC 零点毫秒 → yyyy-MM-dd。
 *
 * @param millis DatePicker 选中值的 UTC 毫秒
 * @return yyyy-MM-dd 日期字符串
 */
private fun formatDateMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

/**
 * TopAppBar 日期筛选入口：日历图标按钮 + 日期选择弹窗（漫画/插画/小说排行榜共用）。
 * 已选日期时图标着主色，提示当前处于历史榜单视图。
 *
 * @param selectedDate 当前筛选日期（yyyy-MM-dd），null = 最新榜（默认态）
 * @param onSelectDate 确认选择回调（参数为新日期 yyyy-MM-dd；null 表示回到最新榜）
 * @param modifier 外部传入的 Modifier
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
            // 历史榜单视图期间着主色，与默认态（跟随内容色）区分
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
 * 胶囊 chip 显示当前日期——点 chip 本体重选日期、点 × 回到最新榜；旁附灰字提示。
 *
 * @param date 当前筛选日期（yyyy-MM-dd）
 * @param onSelectDate 重选日期回调（参数为新日期 yyyy-MM-dd；null 表示回到最新榜）
 * @param onClear 清除日期（回到最新榜单）回调
 * @param modifier 外部传入的 Modifier
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
                // 关闭按钮独立于 chip 点击区：点 × 只清除日期，不弹选择器
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
 * 日期选择弹窗（内部共用）：M3 DatePicker，仅允许选 [MIN_RANKING_DATE] ~ 昨天——
 * 当日榜约中午才生成，传当天/未来日期接口返回空；默认定位到昨天（最新可查的历史日）。
 * 仅开放日历模式（showModeToggle = false）：排行榜选日期无需键盘输入，
 * 点标题年份/月份可弹年份列表快选较早日期。
 *
 * @param selectedDate 当前已选日期（yyyy-MM-dd），null 时默认选中昨天
 * @param onSelect 确认选择回调（参数为 yyyy-MM-dd）
 * @param onDismiss 关闭弹窗回调（取消/点击外部/Esc）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingDatePickerDialog(
    selectedDate: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 可选区间记忆化为 UTC 零点毫秒边界（DatePicker 的毫秒即 UTC 零点基准），各算一次
    val minSelectableMillis = remember {
        MIN_RANKING_DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    // 展示用“昨天”（本地时区）：日榜当天未生成，最新只能查到昨日
    val lastSelectableMillis = remember {
        LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    // SelectableDates 固化为单例：月历每格都会调用 isSelectableDate，
    // 退化为纯区间比较（日期格传入的就是 UTC 零点毫秒，与按日期换算等价），避免逐格分配
    val selectableDates = remember(minSelectableMillis, lastSelectableMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in minSelectableMillis..lastSelectableMillis
        }
    }
    // 初始选中已选日期、否则回落昨天（最新可查的历史日）
    val initialMillis = remember(selectedDate, lastSelectableMillis) {
        selectedDate?.let(::parseDateMillis) ?: lastSelectableMillis
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
                    state.selectedDateMillis?.let { millis -> onSelect(formatDateMillis(millis)) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
