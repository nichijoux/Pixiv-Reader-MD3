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

/**
 * 翻页模式：普通横向滑动翻页（无 3D 特效）。
 * 双页显示时每个 Pager 页 = 一个跨页（左右两半页并排 + 中缝阴影）。
 *
 * @param pagerState 外层持有的横向翻页状态
 * @param spreads 跨页列表
 * @param pageHeight 内容可用高度
 * @param contentTopInset 内容顶部额外避让（沉浸式纸面覆盖状态栏时 = 状态栏高度）
 * @param restoreCharOffset 首次恢复阅读进度的字符偏移
 * @param onPageChange 页码落定后回调当前跨页下标
 * @param onPageInfo 页码及总跨页数信息回调
 * @param jumpToChar 目录或搜索跳转目标字符偏移
 * @param onOpenImage 点击图片全屏预览回调
 * @param onJumpHandled 跳转完成后清理外部跳转锚点的回调
 * @param modifier 外部修饰符
 * @return 无返回值
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

    // 排版重排（字号/行距/双页/简繁切换）后按当前字符锚点重新对齐页码，避免页数变化导致停在错误页或越界。
    LaunchedEffect(spreads) {
        if (!restored || spreads.isEmpty()) return@LaunchedEffect
        val target = spreads.spreadIndexForChar(anchorChar).coerceIn(0, spreads.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // 目录/搜索跳转（就绪后跳转并通知外层清空锚点以便连续跳同一位置）
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

    // 吸附动画比默认弹簧（StiffnessMediumLow）更快：滑动翻页松手后页面快速到位，点击翻页（外层
    // animateScrollToPage 同用 tween(220)）与滑动手感一致
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
