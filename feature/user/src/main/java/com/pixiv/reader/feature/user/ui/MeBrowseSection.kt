package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 我的页「浏览设置」：小说默认页 / 插画查看方向 / 关注页排序 / 剪贴板链接提示 / 小说下载命名。
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
        MeRow(icon = Icons.AutoMirrored.Filled.MenuBook, title = stringResource(R.string.me_novel_default_tab))
        val tabOptions = listOf(
            NovelDefaultTab.RECOMMEND to R.string.me_novel_default_recommend,
            NovelDefaultTab.FOLLOW to R.string.me_novel_default_follow,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        ) {
            tabOptions.forEachIndexed { index, (value, labelRes) ->
                SegmentedButton(
                    selected = novelDefaultTab == value,
                    onClick = { onSetNovelDefaultTab(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabOptions.size),
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        MeRowDivider()
        // 插画查看方向：宽控件行
        MeRow(icon = Icons.Filled.ScreenRotation, title = stringResource(R.string.me_viewer_orientation))
        val orientationOptions = listOf(
            ViewerOrientation.HORIZONTAL to R.string.me_viewer_orientation_horizontal,
            ViewerOrientation.VERTICAL to R.string.me_viewer_orientation_vertical,
            ViewerOrientation.SEAMLESS to R.string.me_viewer_orientation_seamless,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        ) {
            orientationOptions.forEachIndexed { index, (value, labelRes) ->
                SegmentedButton(
                    selected = viewerOrientation == value,
                    onClick = { onSetViewerOrientation(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = orientationOptions.size),
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        MeRowDivider()
        // 关注页排序（值行 + 下拉菜单，菜单锚定行尾值区右对齐展开）
        var sortExpanded by remember { mutableStateOf(false) }
        val sortOptions = listOf(
            FollowSortMode.FOLLOW_TIME to R.string.me_follow_sort_follow_time,
            FollowSortMode.NAME_ASC to R.string.me_follow_sort_name_asc,
            FollowSortMode.NAME_DESC to R.string.me_follow_sort_name_desc,
            FollowSortMode.LATEST_WORK to R.string.me_follow_sort_latest_work,
        )
        MeRow(
            icon = Icons.Filled.Sort,
            title = stringResource(R.string.me_follow_sort),
            trailing = {
                Box {
                    MeValueTrailing(stringResource(sortOptions.first { it.first == followSortMode }.second))
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                    ) {
                        sortOptions.forEach { (value, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    onSetFollowSortMode(value)
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                }
            },
            onClick = { sortExpanded = true },
        )
        MeRowDivider()
        // 剪贴板链接提示（开关行：整行可点切换）
        MeRow(
            icon = Icons.Filled.ContentPaste,
            title = stringResource(R.string.me_clipboard_link_prompt),
            subtitle = stringResource(R.string.me_clipboard_link_prompt_desc),
            trailing = { Switch(checked = clipboardLinkPrompt, onCheckedChange = onSetClipboardLinkPrompt) },
            onClick = { onSetClipboardLinkPrompt(!clipboardLinkPrompt) },
        )
        MeRowDivider()
        // 小说下载命名（导航行：副文本显示当前模板，点击打开编辑弹窗）
        MeRow(
            icon = Icons.Filled.Description,
            title = stringResource(R.string.me_novel_file_name_template),
            subtitle = novelFileNameTemplate,
            subtitleMaxLines = 1,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onOpenFileNameTemplate,
        )
    }
}
