package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 胶囊分段页签（[PillSegmentedRow]）+ [HorizontalPager] 双向同步容器。
 * 不包裹外层 Column，页签、[between] 与 Pager 直接按序发射到调用方 Column（[pagerModifier] 传 weight）；
 * [onSelect] 随 `state.currentPage` 触发（含初次组合），[tabs] 数量须与 [state] 一致。
 */
@Composable
fun <T> SegmentedPager(
    tabs: List<T>,
    onSelect: (T) -> Unit,
    tabLabel: (T) -> Int,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState(pageCount = { tabs.size }),
    pagerModifier: Modifier = Modifier,
    between: @Composable () -> Unit = {},
    pageContent: @Composable (page: Int, tab: T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.currentPage) {
        val page = state.currentPage
        if (page in tabs.indices) {
            onSelect(tabs[page])
        }
    }
    PillSegmentedRow(
        options = tabs.mapIndexed { index, tab -> index to tabLabel(tab) },
        selected = state.currentPage,
        onSelect = { index -> scope.launch { state.animateScrollToPage(index) } },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
    between()
    HorizontalPager(state = state, modifier = pagerModifier) { page ->
        pageContent(page, tabs[page])
    }
}
