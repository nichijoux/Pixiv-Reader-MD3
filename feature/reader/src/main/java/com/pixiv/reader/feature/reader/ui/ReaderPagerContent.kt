package com.pixiv.reader.feature.reader.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderSpread
import com.pixiv.reader.feature.reader.state.spreadIndexForChar

/**
 * 翻页模式：普通横向滑动翻页（无 3D 特效）。
 * 双页显示时每个 Pager 页 = 一个跨页（左右两半页并排 + 中缝阴影）。
 *
 * @param contentTopInset 内容顶部额外避让（沉浸式纸面覆盖状态栏时 = 状态栏高度）
 */
@Composable
internal fun PagerReaderContent(
    pagerState: PagerState,
    spreads: List<ReaderSpread>,
    pageHeight: Dp,
    contentTopInset: Dp = 0.dp,
    restoreCharOffset: Int,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    jumpToChar: Int?,
    modifier: Modifier = Modifier,

) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(spreads, restoreCharOffset) {
        if (restored || spreads.isEmpty()) return@LaunchedEffect
        val index = spreads.spreadIndexForChar(restoreCharOffset)
        pagerState.scrollToPage(index)
        restored = true
    }

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (spreads.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(spreads.spreadIndexForChar(j))
    }

    LaunchedEffect(pagerState.settledPage, spreads.size) {
        val index = pagerState.settledPage
        onPageInfo(index, spreads.size)
        onPageChange(index)
    }

    if (spreads.isEmpty()) {
        EmptyBox(stringResource(R.string.reader_empty_content), modifier = modifier)
        return
    }

    // 吸附动画比默认弹簧（StiffnessMediumLow）更快：滑动翻页松手后页面快速到位，点击翻页（外层
    // animateScrollToPage 同用 tween(220)）与滑动手感一致
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = tween(220),
    )

    HorizontalPager(state = pagerState, flingBehavior = flingBehavior, modifier = modifier) { index ->
        val spread = spreads[index]
        RenderSpreadColumns(
            left = spread.left,
            right = spread.right,
            columns = spread.columns,
            containerHeight = pageHeight,
            contentTopInset = contentTopInset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
