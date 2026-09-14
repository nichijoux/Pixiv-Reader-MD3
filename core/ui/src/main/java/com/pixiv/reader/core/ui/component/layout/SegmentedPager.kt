package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 泛型分段页签容器：SingleChoiceSegmentedButtonRow 页签 + HorizontalPager 内容，双向同步
 * （点击页签滚动 Pager / 滑动落页回调 [onSelect]）。本组件不引入额外容器——页签行、[between]
 * 槽与 Pager 直接按序发射到调用方的 Column 中（与各页面原布局树一致）；Pager 的 weight 等
 * 尺寸约束须经 [pagerModifier] 从调用方的 ColumnScope 传入。
 *
 * 同步策略（与历史/下载/稍后再看/用户/收藏五页原写法一致的多数派）：
 * [LaunchedEffect] 以 `state.currentPage` 为 key，落页（含初次组合）时回调 [onSelect]；
 * 页签选中态始终读取 `state.currentPage`，调用方的选中状态仅用于推导 [PagerState] 初始页。
 *
 * @param T 页签数据类型（枚举 / 筛选值等）
 * @param tabs 页签列表（与 Pager 页一一对应；数量须与 [state] 的 pageCount 一致）
 * @param onSelect 滑动落页回调（参数为落页对应的页签；初次组合也会回调一次）
 * @param tabLabel 页签文案资源
 * @param modifier 页签行 Modifier（默认追加 fillMaxWidth + 水平 lg / 垂直 sm 内边距，与各页原样式一致）
 * @param state Pager 状态；默认按 tabs 数量创建（初始第 0 页）。需要外部持有（如统计格点击滚页）
 *   或非 0 初始页时，由调用方 `rememberPagerState` 传入
 * @param pagerModifier Pager 的 Modifier（如 `Modifier.weight(1f)`）
 * @param between 页签行与 Pager 之间的附加内容槽（如收藏页的标签筛选行）
 * @param pageContent 页内容（参数为页码与对应页签）
 * @return 无返回值
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
    // 滑动落页 → 回调选中项（LaunchedEffect 初次组合也执行一次，与各页原同步时序一致）
    LaunchedEffect(state.currentPage) {
        val page = state.currentPage
        if (page in tabs.indices) {
            onSelect(tabs[page])
        }
    }
    // 页签行：选中态跟 Pager 落页，点击反向滚页
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = state.currentPage == index,
                onClick = { scope.launch { state.animateScrollToPage(index) } },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(tabLabel(tab))) },
            )
        }
    }
    between()
    HorizontalPager(state = state, modifier = pagerModifier) { page ->
        pageContent(page, tabs[page])
    }
}
