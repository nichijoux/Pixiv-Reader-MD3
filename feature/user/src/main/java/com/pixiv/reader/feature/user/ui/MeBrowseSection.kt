package com.pixiv.reader.feature.user.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.feature.user.R

/**
 * 我的页「浏览设置」：小说默认页 / 作品查看方向 / 关注页排序 / 剪贴板链接提示 / 小说下载命名。
 * Expressive 分组面板：五项聚入单张 28dp 圆角卡，组内行用分隔线区隔。
 *
 * @param novelDefaultTab 小说 Tab 默认页（推荐/关注）
 * @param viewerOrientation 全屏查看器滑动方向
 * @param followSortMode 关注页排序方式
 * @param clipboardLinkPrompt 剪贴板链接提示开关
 * @param novelFileNameTemplate 当前小说下载命名模板
 * @param onSetNovelDefaultTab 设置小说默认页
 * @param onSetViewerOrientation 设置查看方向
 * @param onSetFollowSortMode 设置关注排序
 * @param onSetClipboardLinkPrompt 设置剪贴板提示开关
 * @param onOpenFileNameTemplate 打开命名模板编辑弹窗
 * @return 无返回值
 */
@Composable
internal fun MeBrowseSection(
    novelDefaultTab: NovelDefaultTab,
    viewerOrientation: ViewerOrientation,
    followSortMode: FollowSortMode,
    clipboardLinkPrompt: Boolean,
    novelFileNameTemplate: String,
    onSetNovelDefaultTab: (NovelDefaultTab) -> Unit,
    onSetViewerOrientation: (ViewerOrientation) -> Unit,
    onSetFollowSortMode: (FollowSortMode) -> Unit,
    onSetClipboardLinkPrompt: (Boolean) -> Unit,
    onOpenFileNameTemplate: () -> Unit,
) {
    MeGroupCard {
        // 小说默认页：宽控件行（标题行 + 全宽分段选择）
        MeSegmentedRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.me_novel_default_tab),
            selected = novelDefaultTab,
            options = listOf(
                NovelDefaultTab.RECOMMEND to R.string.me_novel_default_recommend,
                NovelDefaultTab.FOLLOW to R.string.me_novel_default_follow,
            ),
            onSelect = onSetNovelDefaultTab,
        )
        MeRowDivider()
        // 作品查看方向：宽控件行
        MeSegmentedRow(
            icon = Icons.Filled.ScreenRotation,
            title = stringResource(R.string.me_viewer_orientation),
            selected = viewerOrientation,
            options = listOf(
                ViewerOrientation.HORIZONTAL to R.string.me_viewer_orientation_horizontal,
                ViewerOrientation.VERTICAL to R.string.me_viewer_orientation_vertical,
                ViewerOrientation.SEAMLESS to R.string.me_viewer_orientation_seamless,
            ),
            onSelect = onSetViewerOrientation,
        )
        MeRowDivider()
        // 关注页排序（值行 + 下拉菜单，菜单锚定行尾值区右对齐展开）
        val sortOptions = listOf(
            FollowSortMode.FOLLOW_TIME to R.string.me_follow_sort_follow_time,
            FollowSortMode.NAME_ASC to R.string.me_follow_sort_name_asc,
            FollowSortMode.NAME_DESC to R.string.me_follow_sort_name_desc,
            FollowSortMode.LATEST_WORK to R.string.me_follow_sort_latest_work,
        )
        MeDropdownRow(
            icon = Icons.Filled.Sort,
            title = stringResource(R.string.me_follow_sort),
            currentValue = stringResource(sortOptions.first { it.first == followSortMode }.second),
            options = sortOptions,
            onSelect = onSetFollowSortMode,
        )
        MeRowDivider()
        // 剪贴板链接提示（开关行：整行可点切换）
        MeSwitchRow(
            icon = Icons.Filled.ContentPaste,
            title = stringResource(R.string.me_clipboard_link_prompt),
            subtitle = stringResource(R.string.me_clipboard_link_prompt_desc),
            checked = clipboardLinkPrompt,
            onCheckedChange = onSetClipboardLinkPrompt,
        )
        MeRowDivider()
        // 小说下载命名（导航行：副文本显示当前模板，点击打开编辑弹窗）
        MeRow(
            icon = Icons.Filled.Description,
            title = stringResource(R.string.me_novel_file_name_template),
            subtitle = novelFileNameTemplate,
            subtitleMaxLines = 1,
            trailing = { MeArrowTrailing() },
            onClick = onOpenFileNameTemplate,
        )
    }
}
