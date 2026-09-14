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
 *
 * 复用 [NovelSeriesViewModel]（调用方 `hiltViewModel()` 注入）——选中项变化时 [switchTo] 加载；
 * 内容与系列全屏页同构（共享 [NovelSeriesBody] + [NovelSeriesList]：
 * 信息头 + 分册卡列表 + [DownloadSheet] 下载弹窗 + 通知宿主），
 * 无 Scaffold/TopAppBar，也无内建关闭按钮：pane 关闭由系统返回 / 外层入口承担。
 * 分册卡上点系列标题 → [onOpenSeries]（宿主接管：维护返回栈后原地切换系列，不跳出右栏）；
 * 分册卡点击 = [onOpenNovel]（宿主分流：pane 内切换到小说详情或全屏路由）。
 * 底部沉浸式跟随宿主方案（pane 底边与左栏列表一致直通屏幕底，列表尾 padding 避让导航栏）；
 * 消息通知自带（下载 / 关注作者操作反馈在 pane 内展示）。
 *
 * @param selectedId 当前选中系列 id（null = 未选中，显示 [placeholder]）
 * @param placeholder 未选中时的占位提示文案
 * @param onOpenNovel 分册点击回调（宿主分流：pane 内切换小说详情 / 全屏路由）
 * @param onOpenSeries 分册卡系列标题点击回调（宿主维护返回栈后原地切换系列）
 * @param onOpenUser 点击作者打开用户主页（全屏路由）
 * @param onOpenCover 打开封面全屏大图（全屏路由）
 * @param onSearchTag 标签搜索（跳发现页）
 * @param viewModel 系列 ViewModel（调用方注入）
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
    // 选中项变化时加载详情（幂等：同 id 不重载；小说详情 pane 同款模式）
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    // 下载格式弹窗（复用系列页 DownloadSheet）
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
