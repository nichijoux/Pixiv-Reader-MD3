package com.pixiv.reader.feature.reader.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderSpread
import com.pixiv.reader.feature.reader.state.spreadIndexForChar

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
    onOpenImage: (String) -> Unit = {},
    onJumpHandled: () -> Unit = {},
    modifier: Modifier = Modifier,

) {
    var restored by remember { mutableStateOf(false) }
    var anchorChar by remember { mutableIntStateOf(restoreCharOffset) }
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val currentOnPageInfo by rememberUpdatedState(onPageInfo)
    val currentOnJumpHandled by rememberUpdatedState(onJumpHandled)

    LaunchedEffect(spreads, restoreCharOffset) {
        if (restored || spreads.isEmpty()) return@LaunchedEffect
        val index = spreads.spreadIndexForChar(restoreCharOffset).coerceIn(0, spreads.lastIndex)
        pagerState.scrollToPage(index)
        anchorChar = restoreCharOffset
        restored = true
    }

    // 排版重排后按字符锚点重新对齐页码，防止页数变化错位或越界
    LaunchedEffect(spreads) {
        if (!restored || spreads.isEmpty()) return@LaunchedEffect
        val target = spreads.spreadIndexForChar(anchorChar).coerceIn(0, spreads.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // 跳转后清空外部锚点，保证连续跳转同一位置仍触发
    LaunchedEffect(jumpToChar, spreads.isNotEmpty()) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (spreads.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(spreads.spreadIndexForChar(j).coerceIn(0, spreads.lastIndex))
        anchorChar = j
        currentOnJumpHandled()
    }

    LaunchedEffect(pagerState.settledPage, spreads) {
        if (spreads.isEmpty()) {
            currentOnPageInfo(0, 0)
            return@LaunchedEffect
        }
        val index = pagerState.settledPage.coerceIn(0, spreads.lastIndex)
        anchorChar = spreads[index].startChar
        currentOnPageInfo(index, spreads.size)
        currentOnPageChange(index)
    }

    if (spreads.isEmpty()) {
        EmptyBox(stringResource(R.string.reader_empty_content), modifier = modifier)
        return
    }

    // 吸附动画与外层点击翻页统一用 tween(220)，比默认弹簧更快到位
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = tween(220),
    )

    HorizontalPager(state = pagerState, flingBehavior = flingBehavior, modifier = modifier) { index ->
        val spread = spreads.getOrNull(index) ?: return@HorizontalPager
        RenderSpreadColumns(
            left = spread.left,
            right = spread.right,
            columns = spread.columns,
            containerHeight = pageHeight,
            contentTopInset = contentTopInset,
            onOpenImage = onOpenImage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
