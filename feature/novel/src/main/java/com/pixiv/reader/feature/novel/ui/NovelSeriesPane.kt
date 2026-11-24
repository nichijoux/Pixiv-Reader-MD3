package com.pixiv.reader.feature.novel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pixiv.reader.core.ui.component.layout.PanePlaceholder
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel

/**
 * 小说系列 pane（Master-Detail 右栏；用户页经 app 组合根槽位注入复用，小说 Tab 同模块直用）。
 * 与系列全屏页同构（共享 [NovelSeriesBody] + [NovelSeriesList]），无 Scaffold / TopAppBar，
 * 也无内建关闭按钮：关闭由系统返回 / 外层入口承担。
 * 分册卡点系列标题→[onOpenSeries]（宿主维护返回栈后原地切系列），点卡→[onOpenNovel]（宿主分流）。
 */
@Composable
fun NovelSeriesPane(
    selectedId: Long?,
    placeholder: String,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: NovelSeriesViewModel,
) {
    val currentId = selectedId
    // 幂等：同 id 不重载
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    var showDownloadDialog by remember { mutableStateOf(false) }

    NovelSeriesBody(
        viewModel = viewModel,
        showDownloadDialog = showDownloadDialog,
        onShowDownloadDialogChange = { showDownloadDialog = it },
    ) {
        if (currentId == null) {
            PanePlaceholder(text = placeholder)
        } else {
            NovelSeriesList(
                viewModel = viewModel,
                onOpenNovel = onOpenNovel,
                onOpenCover = onOpenCover,
                onOpenUser = onOpenUser,
                onSearchTag = onSearchTag,
                onOpenSeries = onOpenSeries,
                onDownloadClick = { showDownloadDialog = true },
            )
        }
    }
}
