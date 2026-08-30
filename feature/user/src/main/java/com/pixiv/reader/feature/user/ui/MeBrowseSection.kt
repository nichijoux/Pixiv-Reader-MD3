package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.Spacing

/** 我的页「浏览设置」：小说默认页 / 插画查看方向 / 关注页排序 / 剪贴板链接提示 / 小说下载命名（内容/浏览类偏好）。 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // 小说默认页（进入小说 Tab 时显示推荐还是关注）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_novel_default_tab),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.smPlus),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                NovelDefaultTab.RECOMMEND to R.string.me_novel_default_recommend,
                NovelDefaultTab.FOLLOW to R.string.me_novel_default_follow,
            ).forEach { (value, labelRes) ->
                PillSelectButton(
                    selected = novelDefaultTab == value,
                    onClick = { onSetNovelDefaultTab(value) },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    CardSpacer()
    // 插画查看方向（全屏查看器横向 / 竖向滑动切换）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_viewer_orientation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.smPlus),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                ViewerOrientation.HORIZONTAL to R.string.me_viewer_orientation_horizontal,
                ViewerOrientation.VERTICAL to R.string.me_viewer_orientation_vertical,
                ViewerOrientation.SEAMLESS to R.string.me_viewer_orientation_seamless,
            ).forEach { (value, labelRes) ->
                PillSelectButton(
                    selected = viewerOrientation == value,
                    onClick = { onSetViewerOrientation(value) },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    CardSpacer()
    // 关注页排序（关注 Tab 左列用户列表的排列方式）：下拉选择框
    MeSettingCard {
        var sortExpanded by remember { mutableStateOf(false) }
        val sortOptions = listOf(
            FollowSortMode.FOLLOW_TIME to R.string.me_follow_sort_follow_time,
            FollowSortMode.NAME_ASC to R.string.me_follow_sort_name_asc,
            FollowSortMode.NAME_DESC to R.string.me_follow_sort_name_desc,
            FollowSortMode.LATEST_WORK to R.string.me_follow_sort_latest_work,
        )
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
        ) {
            OutlinedTextField(
                value = stringResource(sortOptions.first { it.first == followSortMode }.second),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.me_follow_sort)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
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
    }
    CardSpacer()
    // 剪贴板链接提示（读取剪贴板中的 pixiv 链接并提示打开）
    MeSettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.me_clipboard_link_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.me_clipboard_link_prompt_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Switch(
                checked = clipboardLinkPrompt,
                onCheckedChange = onSetClipboardLinkPrompt,
            )
        }
    }
    CardSpacer()
    // 小说下载命名（模板占位符，点击打开编辑）
    androidx.compose.material3.Card(
        onClick = onOpenFileNameTemplate,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.me_novel_file_name_template),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = novelFileNameTemplate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
