package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.pageIndexForChar

/** 翻页模式：普通横向滑动翻页（无 3D 特效）。 */
@Composable
internal fun PagerReaderContent(
    pagerState: PagerState,
    pages: List<ReaderPage>,
    pageHeight: Dp,
    restoreCharOffset: Int,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    jumpToChar: Int?,
    modifier: Modifier = Modifier,

) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(pages, restoreCharOffset) {
        if (restored || pages.isEmpty()) return@LaunchedEffect
        val index = pages.pageIndexForChar(restoreCharOffset)
        pagerState.scrollToPage(index)
        restored = true
    }

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(pages.pageIndexForChar(j))
    }

    LaunchedEffect(pagerState.settledPage, pages.size) {
        val index = pagerState.settledPage
        onPageInfo(index, pages.size)
        onPageChange(index)
    }

    if (pages.isEmpty()) {
        EmptyBox(stringResource(R.string.reader_empty_content), modifier = modifier)
        return
    }

    HorizontalPager(state = pagerState, modifier = modifier) { index ->
        RenderReaderPage(
            pages[index],
            pageHeight,
            Modifier
                .fillMaxSize()
                .padding(PAGE_H_PADDING, PAGE_V_PADDING),

        )
    }
}
